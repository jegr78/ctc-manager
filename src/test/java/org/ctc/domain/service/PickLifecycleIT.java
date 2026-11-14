package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.ctc.TestHelper;
import org.ctc.dataimport.GoogleCalendarService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.ctc.domain.repository.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Tag("integration")
class PickLifecycleIT {
    @Autowired EntityManager entityManager;
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired RaceRepository races;
    @Autowired MatchdayPickRepository picks;
    @Autowired MatchdayPickRevisionRepository revisions;
    @Autowired PickSubmissionService submissions;
    @Autowired RaceService raceService;
    @Autowired RaceFormDataService forms;
    @Autowired MatchService matchService;
    @Autowired MatchdayService matchdayService;
    @Autowired SeasonPhaseService phaseService;
    @Autowired SeasonManagementService seasonService;
    @Autowired SeasonCarPoolService pools;
    @MockitoBean GoogleCalendarService googleCalendar;
    TestHelper.SeasonFixture fixture;
    UUID otherRace;
    UUID nextDay;
    UUID emptyDay;
    UUID unscheduled;
    UUID thirdTeam;
    List<UUID> carIds;
    List<UUID> trackIds;
    PickPayload payload;

    @BeforeEach
    void setUp() {
        // given
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TPL" + UUID.randomUUID().toString().substring(0, 7));
            fixture.race().setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0));
            fixture.matchday().setPickDeadline(LocalDateTime.of(2035, 1, 1, 12, 0));
            fixture.matchday().setPicksOpen(true);
            var car1 = cars.save(new Car("Test lifecycle", UUID.randomUUID().toString()));
            var car2 = cars.save(new Car("Test lifecycle", UUID.randomUUID().toString()));
            var track1 = tracks.save(new Track("Test lifecycle " + UUID.randomUUID()));
            var track2 = tracks.save(new Track("Test lifecycle " + UUID.randomUUID()));
            fixture.season().getCars().addAll(List.of(car1, car2));
            fixture.season().getTracks().addAll(List.of(track1, track2));
            carIds = List.of(car1.getId(), car2.getId());
            trackIds = List.of(track1.getId(), track2.getId());
            var day = helper.createMatchday(fixture.season(), "Test next lifecycle round", 2);
            nextDay = day.getId();
            emptyDay = helper.createMatchday(fixture.season(), "Test empty lifecycle target", 4).getId();
            var match = helper.createMatch(day, fixture.homeTeam(), fixture.awayTeam());
            var second = helper.createRace(day, match);
            second.setDateTime(LocalDateTime.of(2035, 1, 2, 20, 0));
            otherRace = second.getId();
            var day3 = helper.createMatchday(fixture.season(), "Test unscheduled lifecycle round", 3);
            unscheduled = helper.createRace(day3, helper.createMatch(day3, fixture.homeTeam(), fixture.awayTeam())).getId();
            thirdTeam = helper.createTeam("Test successor " + UUID.randomUUID(), "TL" + UUID.randomUUID().toString().substring(0, 7)).getId();
        });
        tx.executeWithoutResult(status -> {
        });
        payload = new PickPayload(trackIds.getFirst(), carIds.getFirst(), null, 12, 2, 3, 4, PickInitialFuel.DEFAULT, 1,
                TimeProgressionMode.MULTIPLIER, 0, "S01", "Sunset", Set.of(PickTyre.RS), Set.of());
        submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor(fixture.race().getId(), fixture.homeTeam().getId()), payload);
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
            seasons.flush();
            cars.deleteAllById(carIds);
            tracks.deleteAllById(trackIds);
            teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId(), thirdTeam));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"match", "leg"})
    void givenCachedPausedSeason_whenCollectionIsEnabledBeforePlanning_thenUseTheLockedCurrentEnrollmentFlag(String creation) {
        tx.executeWithoutResult(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            races.findById(fixture.race().getId()).orElseThrow().getMatchday().getPhase().setLegs(2);
        });
        var separate = new TransactionTemplate(tx.getTransactionManager());
        separate.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.executeWithoutResult(status -> {
            var owner = seasons.findById(fixture.season().getId()).orElseThrow();
            var day = entityManager.find(Matchday.class, emptyDay);
            var match = creation.equals("match")
                    ? matchService.createMatchWithLegs(day, teams.findById(fixture.homeTeam().getId()).orElseThrow(),
                            teams.findById(fixture.awayTeam().getId()).orElseThrow(), false)
                    : matchService.addLeg(fixture.match().getId());
            entityManager.flush();
            var created = races.findByMatchId(match.getId()).stream()
                    .filter(race -> !race.getId().equals(fixture.race().getId())).toList();
            assertThat(created).isNotEmpty().allSatisfy(race -> {
                assertThat(race.getDateTime()).isNull();
            });
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"round", "home", "away"})
    void givenPendingHistory_whenReassigningNativeRace_thenRejectAndRetainExactTarget(String change) {
        // when
        var result = save(fixture.race().getId(), change.equals("round") ? emptyDay : fixture.matchday().getId(),
                change.equals("home") ? thirdTeam : fixture.homeTeam().getId(),
                change.equals("away") ? thirdTeam : fixture.awayTeam().getId(), null, null, null,
                LocalDateTime.of(2035, 1, 1, 20, 0));
        // then
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("pick history");
        assertOriginal();
    }

    @ParameterizedTest
    @ValueSource(strings = {"race", "match", "matchday"})
    void givenPendingHistory_whenDeletingNativeFixture_thenRejectBeforeDatabaseConstraintFailure(String target) {
        // when / then
        assertThatThrownBy(() -> {
            switch (target) {
                case "race" -> raceService.deleteRace(fixture.race().getId());
                case "match" -> matchService.deleteMatch(fixture.match().getId());
                case "matchday" -> matchdayService.deleteMatchday(fixture.matchday().getId());
                default -> throw new AssertionError(target);
            }
        }).isInstanceOf(BusinessRuleException.class).hasMessageContaining("pick history");
        assertOriginal();
    }

    @Test
    void givenContainingSeasonAndPhase_whenDeleting_thenExistingStructuralGuardsRetainHistory() {
        // when / then
        assertThatThrownBy(() -> seasonService.delete(fixture.season().getId())).isInstanceOf(BusinessRuleException.class);
        UUID phase = tx.execute(status -> races.findById(fixture.race().getId()).orElseThrow().getMatchday().getPhase().getId());
        assertThatThrownBy(() -> phaseService.delete(phase)).isInstanceOf(BusinessRuleException.class);
        assertOriginal();
    }

    @Test
    void givenManagedPendingRace_whenChangingSetupThroughNativeEditor_thenRequireReviewAndKeepSubmission() {
        // when
        var result = save(fixture.race().getId(), fixture.matchday().getId(), fixture.homeTeam().getId(), fixture.awayTeam().getId(),
                payload.trackId(), payload.carId(), payload, LocalDateTime.of(2035, 1, 1, 20, 0));
        // then
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("pick review");
        tx.executeWithoutResult(status -> assertThat(races.findById(fixture.race().getId()).orElseThrow().getSettings()).isNull());
        assertOriginal();
    }

    @Test
    void givenTrackReservedByPendingPick_whenNativeEditorChoosesAnotherCar_thenRejectTrackReuseAndDisableFormChoice() {
        // when
        var result = save(otherRace, nextDay, fixture.homeTeam().getId(), fixture.awayTeam().getId(),
                trackIds.getFirst(), carIds.get(1), null, LocalDateTime.of(2035, 1, 2, 20, 0));
        // then
        assertThat(result.success()).isFalse();
        assertThat(result.message().toLowerCase(Locale.ROOT)).contains("track");
        assertThat(forms.getRaceFormData(otherRace).usedTrackIds()).contains(trackIds.getFirst());
        tx.executeWithoutResult(status -> assertThat(races.findById(otherRace).orElseThrow().getTrack()).isNull());
    }

    @ParameterizedTest
    @ValueSource(strings = {"car", "track", "mode"})
    void givenHistoricalSelection_whenRemovingPoolChoiceOrChangingMode_thenRejectBeforeChangingPool(String choice) {
        // when / then
        assertThatThrownBy(() -> {
            switch (choice) {
                case "car" -> seasonService.removeCarsFromSeason(fixture.season().getId(), List.of(carIds.getFirst()));
                case "track" -> seasonService.removeTracksFromSeason(fixture.season().getId(), List.of(trackIds.getFirst()));
                case "mode" -> pools.changeMode(fixture.season().getId(), CarPoolMode.CATEGORY_POOL);
                default -> throw new AssertionError(choice);
            }
        }).isInstanceOf(BusinessRuleException.class).hasMessageContaining("pick");
        tx.executeWithoutResult(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            assertThat(season.getCarPoolMode()).isEqualTo(CarPoolMode.FIXED_CARS);
            assertThat(season.getCars()).extracting(Car::getId).contains(carIds.getFirst());
            assertThat(season.getTracks()).extracting(Track::getId).contains(trackIds.getFirst());
        });
    }

    @Test
    void givenPreviouslyScheduledUnmanagedRace_whenEditingMetadataAfterOptIn_thenPreserveExistingEnrollmentDecision() {
        // when
        var result = save(otherRace, nextDay, fixture.homeTeam().getId(), fixture.awayTeam().getId(), null, null, null,
                LocalDateTime.of(2035, 1, 2, 21, 0));
        // then
        assertThat(result.success()).isTrue();
    }

    @Test
    void givenUnscheduledRaceInEnabledSeason_whenSchedulingFirstTime_thenEnrollWithoutClearingExistingHistoryOnDisable() {
        // given
        UUID day = tx.execute(status -> races.findById(unscheduled).orElseThrow().getMatchday().getId());
        // when
        var result = save(unscheduled, day, fixture.homeTeam().getId(), fixture.awayTeam().getId(), null, null, null,
                LocalDateTime.of(2035, 1, 3, 20, 0));
        // then
        assertThat(result.success()).isTrue();
        tx.executeWithoutResult(status -> {
        });
        tx.executeWithoutResult(status -> {
            assertThat(picks.findByRaceId(fixture.race().getId()).orElseThrow().getVersion()).isEqualTo(1);
        });
    }

    @Test
    void givenTeamReplacement_whenUpdatingSeasonRoster_thenPreserveOriginalRaceAndPickOwnership() {
        // when
        seasonService.replaceTeam(fixture.season().getId(), fixture.homeTeam().getId(), thirdTeam, LocalDate.of(2030, 1, 1));
        // then
        assertOriginal();
        assertThatThrownBy(() -> submissions.submit(fixture.race().getId(), 1, UUID.randomUUID(), actor(fixture.race().getId(), thirdTeam), payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("target");
    }

    private void assertOriginal() {
        tx.executeWithoutResult(status -> {
            var race = races.findById(fixture.race().getId()).orElseThrow();
            assertThat(race.getHomeTeam().getId()).isEqualTo(fixture.homeTeam().getId());
            assertThat(race.getAwayTeam().getId()).isEqualTo(fixture.awayTeam().getId());
            assertThat(race.getMatchday().getId()).isEqualTo(fixture.matchday().getId());
            var pick = picks.findByRaceId(race.getId()).orElseThrow();
            assertThat(pick.getTeam().getId()).isEqualTo(fixture.homeTeam().getId());
            assertThat(pick.getVersion()).isEqualTo(1);
            assertThat(revisions.findByPickIdAndRevisionNumber(pick.getId(), 1).orElseThrow().getPayload()).isEqualTo(payload);
        });
    }
    private AuthorizedPickActor actor(UUID race, UUID team) { return new AuthorizedPickActor(race, fixture.season().getId(), team, "12345"); }
    private RaceService.SaveResult save(UUID id, UUID day, UUID home, UUID away, UUID track, UUID car, PickPayload pick, LocalDateTime date) {
        var values = pick == null ? RaceSettingsValues.from((RaceSettings) null) : RaceSettingsValues.from(pick);
        return raceService.saveRace(id, day, home, away, track, car, date, values.laps(), values.tyreWear(), values.fuelConsumption(),
                values.refuelingSpeed(), values.initialFuel(), values.requiredPitStops(), values.timeProgressionMode(), values.timeProgressionMultiplier(),
                values.weather(), values.timeOfDay(), values.availableTyres(), values.mandatoryTyres(), null);
    }
}
