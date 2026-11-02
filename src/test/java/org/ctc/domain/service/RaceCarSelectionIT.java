package org.ctc.domain.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.ctc.TestHelper;
import org.ctc.dataimport.GoogleCalendarService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@CtcDevSpringBootContext
@Tag("integration")
class RaceCarSelectionIT {
    @Autowired TestHelper helper;
    @Autowired SeasonRepository seasons;
    @Autowired CarRepository cars;
    @Autowired TeamRepository teams;
    @Autowired MatchRepository matches;
    @Autowired RaceRepository races;
    @Autowired SeasonCarPoolEntryRepository entries;
    @Autowired SeasonCarPoolService pools;
    @Autowired RaceCarSelectionService selections;
    @Autowired RaceService raceService;
    @Autowired MatchService matchService;
    @Autowired MatchdayService matchdayService;
    @Autowired RaceCalendarService calendar;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean GoogleCalendarService googleCalendar;
    TransactionTemplate tx;
    TransactionTemplate writer;
    Fixture fixture;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        writer = new TransactionTemplate(transactionManager);
        writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        fixture = tx.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            var graph = helper.createFullSeasonFixture("TCS" + suffix);
            graph.race().setDateTime(LocalDateTime.of(2031, 1, 1, 20, 0));
            graph.matchday().getPhase().setEventDurationMinutes(30);
            var mr = car(suffix, "MR", Drivetrain.MR);
            var rr = car(suffix, "RR", Drivetrain.RR);
            pools.changeMode(graph.season().getId(), CarPoolMode.CATEGORY_POOL);
            var entry = pools.saveEntry(graph.season().getId(), null, "Test combined entry", List.of(
                    new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, null, 9899)));
            pools.freeze(graph.season().getId());
            return new Fixture(graph.season().getId(), graph.match().getId(), graph.race().getId(),
                    graph.homeTeam().getId(), graph.awayTeam().getId(), entry, mr.getId(), rr.getId());
        });
    }

    @AfterEach
    void cleanUp() {
        if (fixture == null) return;
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season()).orElseThrow());
            seasons.flush();
            cars.deleteAllById(List.of(fixture.mr(), fixture.rr()));
            teams.deleteAllById(List.of(fixture.home(), fixture.away()));
        });
    }

    @Test
    void givenCachedBlankRaceAndCommittedSelection_whenValidatingAnotherMember_thenRefreshUsageBeforeAccepting() {
        // given
        tx.executeWithoutResult(status -> {
            var cached = races.findById(fixture.race()).orElseThrow();
            assertThat(cached.getCarPoolEntry()).isNull();
            writer.executeWithoutResult(inner -> configureSelection());
            assertThat(cached.getCarPoolEntry()).isNull();
            // when
            var result = selections.validate(fixture.season(), fixture.home(), null, fixture.entry(), fixture.rr());
            // then
            assertThat(result.valid()).as("a fresh usage read sees the committed MR selection and rejects RR reuse").isFalse();
            assertThat(result.message()).contains("already");
        });
    }

    @Test
    void givenCachedPairingAndCommittedReorientation_whenValidating_thenRefreshEffectiveHomeOwnership() {
        // given
        tx.executeWithoutResult(status -> configureSelection());
        tx.executeWithoutResult(status -> {
            var cached = races.findById(fixture.race()).orElseThrow();
            assertThat(cached.getHomeTeam().getId()).isEqualTo(fixture.home());
            writer.executeWithoutResult(inner -> {
                seasons.findByIdForUpdate(fixture.season()).orElseThrow();
                var match = matches.findById(fixture.match()).orElseThrow();
                match.setHomeTeam(teams.findById(fixture.away()).orElseThrow());
                match.setAwayTeam(teams.findById(fixture.home()).orElseThrow());
                matches.saveAndFlush(match);
            });
            assertThat(cached.getHomeTeam().getId()).isEqualTo(fixture.home());
            // when
            var result = selections.validate(fixture.season(), fixture.away(), null, fixture.entry(), fixture.rr());
            // then
            assertThat(result.valid()).as("the committed effective home owns the consumed entry").isFalse();
            assertThat(result.message()).contains("already");
        });
    }

    @Test
    void givenLockedSelectionRows_whenAnotherTransactionWritesCalendarMetadata_thenDatabaseBlocksUntilRelease() {
        // given
        Runnable calendarWrite = () -> writer.executeWithoutResult(status -> {
            setLockTimeout(250);
            try {
                var race = races.findById(fixture.race()).orElseThrow();
                race.setCalendarEventId("test-lock-calendar");
                races.saveAndFlush(race);
            } finally {
                setLockTimeout(2000);
            }
        });
        tx.executeWithoutResult(status -> {
            seasons.findByIdForUpdate(fixture.season()).orElseThrow();
            races.findBySeasonForSelectionUpdate(fixture.season());
            // when / then
            assertThatThrownBy(calendarWrite::run).isInstanceOf(PessimisticLockingFailureException.class);
        });
        // when
        calendarWrite.run();
        // then
        tx.executeWithoutResult(status -> assertThat(races.findById(fixture.race()).orElseThrow().getCalendarEventId())
                .isEqualTo("test-lock-calendar"));
    }

    @Test
    void givenSelectionCommittedDuringCalendarCall_whenCalendarMetadataSaves_thenKeepTheConcurrentSelection() throws Exception {
        // given
        when(googleCalendar.isAvailable()).thenReturn(true);
        doAnswer(invocation -> {
            writer.executeWithoutResult(status -> configureSelection());
            return "test-concurrent-calendar";
        }).when(googleCalendar).createEvent(anyString(), any(LocalDateTime.class), eq(30));
        // when
        calendar.createOrUpdateCalendarEvent(fixture.race());
        // then
        tx.executeWithoutResult(status -> {
            var saved = races.findById(fixture.race()).orElseThrow();
            assertThat(saved.getCarPoolEntry()).as("an unrelated calendar write preserves the entry").isNotNull();
            assertThat(saved.getCarPoolEntry().getId()).isEqualTo(fixture.entry());
            assertThat(saved.getCar().getId()).isEqualTo(fixture.mr());
            assertThat(saved.getCalendarEventId()).isEqualTo("test-concurrent-calendar");
        });
    }

    @Test
    void givenSavedSelection_whenReeditFails_thenPersistedSelectionSettingsAndPairingStayUnchanged() {
        // given
        assertThat(save(fixture.race(), fixture.home(), fixture.away(), fixture.entry(), fixture.mr(), 5).success()).isTrue();
        // when
        var result = save(fixture.race(), fixture.away(), fixture.home(), UUID.randomUUID(), fixture.rr(), 99);
        // then
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("entry");
        tx.executeWithoutResult(status -> {
            var saved = races.findById(fixture.race()).orElseThrow();
            assertThat(saved.getCar().getId()).isEqualTo(fixture.mr());
            assertThat(saved.getCarPoolEntry().getId()).isEqualTo(fixture.entry());
            assertThat(saved.getSettings().getNumberOfLaps()).isEqualTo(5);
            assertThat(saved.getHomeTeam().getId()).isEqualTo(fixture.home());
            assertThat(saved.getAwayTeam().getId()).isEqualTo(fixture.away());
            assertThat(selections.availability(fixture.season(), fixture.home(), null).consumedEntryIds())
                    .containsExactly(fixture.entry());
            assertThat(selections.availability(fixture.season(), fixture.away(), null).consumedEntryIds()).isEmpty();
        });
    }

    @Test
    void givenTwoTransactionsWithCachedContext_whenSelectingSameHomeAndEntry_thenExactlyOneSucceeds() throws Exception {
        // given
        UUID secondId = addLeg(false);
        var ready = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> selectAfterContextRead(fixture.race(), fixture.mr(), ready));
            var second = executor.submit(() -> selectAfterContextRead(secondId, fixture.rr(), ready));
            // when
            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            // then
            assertThat(results).filteredOn(RaceService.SaveResult::success).hasSize(1);
            assertThat(results).filteredOn(result -> !result.success()).singleElement()
                    .satisfies(result -> assertThat(result.message()).containsAnyOf("already", "changed"));
        }
        tx.executeWithoutResult(status -> assertThat(races.findByMatchId(fixture.match()))
                .filteredOn(race -> race.getCarPoolEntry() != null).hasSize(1));
    }

    @Test
    void givenBothLegsConfiguredForDifferentHomes_whenSwappingPairing_thenValidateBothFinalOwnersTogether() {
        // given
        UUID reverse = addLeg(true);
        assertThat(save(fixture.race(), fixture.home(), fixture.away(), fixture.entry(), fixture.mr(), 5).success()).isTrue();
        assertThat(save(reverse, fixture.away(), fixture.home(), fixture.entry(), fixture.rr(), 7).success()).isTrue();
        // when
        var result = save(fixture.race(), fixture.away(), fixture.home(), fixture.entry(), fixture.mr(), 6);
        // then
        assertThat(result.success()).isTrue();
        tx.executeWithoutResult(status -> {
            assertThat(races.findById(fixture.race()).orElseThrow().getHomeTeam().getId()).isEqualTo(fixture.away());
            assertThat(races.findById(reverse).orElseThrow().getHomeTeam().getId()).isEqualTo(fixture.home());
            assertThat(selections.availability(fixture.season(), fixture.home(), null).consumedEntryIds()).containsExactly(fixture.entry());
            assertThat(selections.availability(fixture.season(), fixture.away(), null).consumedEntryIds()).containsExactly(fixture.entry());
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"race", "match", "matchday"})
    void givenSeasonLocked_whenDeletingInAnotherTransaction_thenDeletionCannotBypassSelectionProtocol(String kind) {
        // given
        assertThat(save(fixture.race(), fixture.home(), fixture.away(), fixture.entry(), fixture.mr(), 5).success()).isTrue();
        tx.executeWithoutResult(status -> {
            seasons.findByIdForUpdate(fixture.season()).orElseThrow();
            // when / then
            assertThatThrownBy(() -> writer.executeWithoutResult(inner -> {
                setLockTimeout(250);
                try {
                    delete(kind);
                } finally {
                    setLockTimeout(2000);
                }
            })).isInstanceOf(PessimisticLockingFailureException.class);
        });
        // when
        delete(kind);
        // then
        assertThat(selections.availability(fixture.season(), fixture.home(), null).consumedEntryIds()).isEmpty();
    }

    void setLockTimeout(int milliseconds) {
        jdbc.execute("SET LOCK_TIMEOUT " + milliseconds);
    }

    private void delete(String kind) {
        switch (kind) {
            case "race" -> raceService.deleteRace(fixture.race());
            case "match" -> matchService.deleteMatch(fixture.match());
            case "matchday" -> {
                UUID matchday = races.findById(fixture.race()).orElseThrow().getMatchday().getId();
                matchdayService.deleteMatchday(matchday);
            }
            default -> throw new IllegalArgumentException(kind);
        }
    }

    private RaceService.SaveResult selectAfterContextRead(UUID raceId, UUID carId, CyclicBarrier ready) {
        try {
            return tx.execute(status -> {
            var cached = races.findById(raceId).orElseThrow();
            assertThat(cached.getCarPoolEntry()).isNull();
            assertThat(cached.getHomeTeam().getId()).isEqualTo(fixture.home());
            try {
                ready.await(5, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException("Competing selection did not reach its context read", ex);
            }
            return save(raceId, fixture.home(), fixture.away(), fixture.entry(), carId, 12);
            });
        } catch (BusinessRuleException ex) {
            return new RaceService.SaveResult(false, ex.getMessage(), raceId, null);
        }
    }

    private UUID addLeg(boolean reversed) {
        return tx.execute(status -> {
            var first = races.findById(fixture.race()).orElseThrow();
            var leg = new Race();
            leg.setMatchday(first.getMatchday());
            leg.setMatch(first.getMatch());
            if (reversed) {
                leg.setHomeTeamOverride(first.getAwayTeam());
                leg.setAwayTeamOverride(first.getHomeTeam());
            }
            return races.saveAndFlush(leg).getId();
        });
    }

    private RaceService.SaveResult save(UUID raceId, UUID home, UUID away, UUID entry, UUID car, int laps) {
        UUID matchday = tx.execute(status -> races.findById(raceId).orElseThrow().getMatchday().getId());
        return raceService.saveRace(raceId, matchday, home, away, null, car, null,
                laps, null, null, null, null, null, TimeProgressionMode.MULTIPLIER, 0,
                null, null, null, null, entry);
    }

    @Test
    void givenCachedRaceEntity_whenUnrelatedMetadataSavesAfterNativeSelection_thenMappingPreservesConcurrentSelection() {
        // given
        tx.executeWithoutResult(status -> {
            var cached = races.findById(fixture.race()).orElseThrow();
            assertThat(cached.getCarPoolEntry()).isNull();
            writer.executeWithoutResult(inner -> assertThat(save(fixture.race(), fixture.home(), fixture.away(),
                    fixture.entry(), fixture.mr(), 12).success()).isTrue());
            // when
            cached.setCalendarEventId("test-independent-metadata-write");
            races.saveAndFlush(cached);
        });
        // then
        tx.executeWithoutResult(status -> {
            var saved = races.findById(fixture.race()).orElseThrow();
            assertThat(saved.getCarPoolEntry()).as("cached scalar writes must not clear the concurrent selection").isNotNull();
            assertThat(saved.getCarPoolEntry().getId()).isEqualTo(fixture.entry());
            assertThat(saved.getCar().getId()).isEqualTo(fixture.mr());
            assertThat(saved.getCalendarEventId()).isEqualTo("test-independent-metadata-write");
        });
    }

    private void configureSelection() {
        seasons.findByIdForUpdate(fixture.season()).orElseThrow();
        var race = races.findById(fixture.race()).orElseThrow();
        race.setCarPoolEntry(entries.findById(fixture.entry()).orElseThrow());
        race.setCar(cars.findById(fixture.mr()).orElseThrow());
        races.saveAndFlush(race);
    }

    private Car car(String suffix, String name, Drivetrain drivetrain) {
        var car = new Car("Test selection " + suffix, name);
        car.setCategory(CarCategory.GR_3);
        car.setDrivetrain(drivetrain);
        car.setModelYear(9899);
        car.setModelYearOverride(true);
        car.setModelYearSource("Test fixture");
        car.setModelYearEvidence("Test accepted local year");
        return cars.saveAndFlush(car);
    }

    record Fixture(UUID season, UUID match, UUID race, UUID home, UUID away, UUID entry, UUID mr, UUID rr) {}
}
