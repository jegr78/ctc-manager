package org.ctc.domain.service;

import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@CtcDevSpringBootContext
@Tag("integration")
class RaceCategoryMoveIT {
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired CarRepository cars;
    @Autowired RaceRepository races;
    @Autowired MatchRepository matches;
    @Autowired SeasonCarPoolService pools;
    @Autowired RaceService raceService;
    @Autowired RaceCarSelectionService selections;
    TestHelper.SeasonFixture source;
    TestHelper.SeasonFixture target;
    UUID sourceEntry;
    UUID targetEntry;
    UUID mr;
    UUID rr;
    UUID returnLeg;

    @BeforeEach
    void setUp() {
        tx.executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            source = helper.createFullSeasonFixture("TCMS" + suffix);
            target = helper.createFullSeasonFixture("TCMT" + suffix);
            target.season().addTeam(source.homeTeam());
            target.season().addTeam(source.awayTeam());
            mr = car(suffix, "MR");
            rr = car(suffix, "RR");
            sourceEntry = freeze(source.season());
            targetEntry = freeze(target.season());
            var leg = new Race();
            leg.setMatchday(source.matchday());
            leg.setMatch(source.match());
            leg.setHomeTeamOverride(source.awayTeam());
            leg.setAwayTeamOverride(source.homeTeam());
            returnLeg = races.saveAndFlush(leg).getId();
        });
        assertThat(save(source.race().getId(), source.matchday().getId(), source.homeTeam().getId(), source.awayTeam().getId(),
                sourceEntry, mr, 5).success()).isTrue();
    }

    @AfterEach
    void cleanUp() {
        if (source == null) return;
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(source.season().getId()).orElseThrow());
            helper.deleteSeasonCascade(seasons.findById(target.season().getId()).orElseThrow());
            seasons.flush();
            cars.deleteAllById(List.of(mr, rr));
            teams.deleteAll(List.of(source.homeTeam(), source.awayTeam(), target.homeTeam(), target.awayTeam()));
        });
    }

    @Test
    void givenUnscoredPairingAndTargetEntry_whenMovingAcrossSeasons_thenMoveWholePairingAndReleaseOldConsumption() {
        // given / when
        var result = save(source.race().getId(), target.matchday().getId(), source.homeTeam().getId(), source.awayTeam().getId(),
                targetEntry, mr, 20);
        // then
        assertThat(result.success()).isTrue();
        tx.executeWithoutResult(status -> {
            var saved = races.findById(source.race().getId()).orElseThrow();
            assertThat(saved.getMatchday().getId()).isEqualTo(target.matchday().getId());
            assertThat(saved.getMatch().getMatchday().getId()).isEqualTo(target.matchday().getId());
            assertThat(saved.getCarPoolEntry().getId()).isEqualTo(targetEntry);
            var reversed = races.findById(returnLeg).orElseThrow();
            assertThat(reversed.getMatchday().getId()).isEqualTo(target.matchday().getId());
            assertThat(reversed.getHomeTeam().getId()).isEqualTo(source.awayTeam().getId());
            assertThat(reversed.getCarPoolEntry()).isNull();
            assertThat(selections.availability(source.season().getId(), source.homeTeam().getId(), null).consumedEntryIds()).isEmpty();
            assertThat(selections.availability(target.season().getId(), source.homeTeam().getId(), null).consumedEntryIds())
                    .containsExactly(targetEntry);
        });
        pools.reopen(source.season().getId());
    }

    @Test
    void givenAnotherConfiguredLegWithSourceEntry_whenMovingOneLeg_thenRejectBeforeChangingAnyContainerOrSettings() {
        // given
        assertThat(save(returnLeg, source.matchday().getId(), source.awayTeam().getId(), source.homeTeam().getId(),
                sourceEntry, rr, 7).success()).isTrue();
        // when
        var result = save(source.race().getId(), target.matchday().getId(), source.homeTeam().getId(), source.awayTeam().getId(),
                targetEntry, mr, 99);
        // then
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("entry from this season");
        tx.executeWithoutResult(status -> {
            assertThat(matches.findById(source.match().getId()).orElseThrow().getMatchday().getId()).isEqualTo(source.matchday().getId());
            for (var id : List.of(source.race().getId(), returnLeg)) {
                var saved = races.findById(id).orElseThrow();
                assertThat(saved.getMatchday().getId()).isEqualTo(source.matchday().getId());
                assertThat(saved.getCarPoolEntry().getId()).isEqualTo(sourceEntry);
            }
            assertThat(races.findById(source.race().getId()).orElseThrow().getSettings().getNumberOfLaps()).isEqualTo(5);
            assertThat(races.findById(returnLeg).orElseThrow().getSettings().getNumberOfLaps()).isEqualTo(7);
        });
    }

    @Test
    void givenTargetAlreadyContainsThePairing_whenMoving_thenRejectAndKeepSourceSelection() {
        // given
        tx.executeWithoutResult(status -> helper.createMatch(target.matchday(), source.awayTeam(), source.homeTeam()));
        // when
        var result = save(source.race().getId(), target.matchday().getId(), source.homeTeam().getId(), source.awayTeam().getId(),
                targetEntry, mr, 20);
        // then
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Match already exists");
        tx.executeWithoutResult(status -> {
            var saved = races.findById(source.race().getId()).orElseThrow();
            assertThat(saved.getMatchday().getId()).isEqualTo(source.matchday().getId());
            assertThat(saved.getCarPoolEntry().getId()).isEqualTo(sourceEntry);
        });
    }

    @Test
    void givenCachedSourceAndCommittedMove_whenSavingUnderOldSeason_thenRefreshOwnerAndRejectBeforeAnyWrite() {
        // given
        var writer = new TransactionTemplate(tx.getTransactionManager());
        writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // when / then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            var cached = races.findById(source.race().getId()).orElseThrow();
            assertThat(cached.getMatchday().getId()).isEqualTo(source.matchday().getId());
            writer.executeWithoutResult(inner -> assertThat(save(source.race().getId(), target.matchday().getId(),
                    source.homeTeam().getId(), source.awayTeam().getId(), targetEntry, mr, 20).success()).isTrue());
            assertThat(cached.getMatchday().getId()).isEqualTo(source.matchday().getId());
            save(source.race().getId(), source.matchday().getId(), source.homeTeam().getId(), source.awayTeam().getId(),
                    sourceEntry, mr, 99);
        })).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class).hasMessageContaining("moved while saving");
        tx.executeWithoutResult(status -> {
            var current = races.findById(source.race().getId()).orElseThrow();
            assertThat(current.getMatchday().getId()).isEqualTo(target.matchday().getId());
            assertThat(current.getCarPoolEntry().getId()).isEqualTo(targetEntry);
            assertThat(current.getSettings().getNumberOfLaps()).isEqualTo(20);
        });
    }

    @Test
    void givenSameCarAndHomeInDifferentSeasons_whenSelectingFromFixedTargetPool_thenDoNotLeakSourceConsumption() {
        // given
        tx.executeWithoutResult(status -> {
            pools.changeMode(target.season().getId(), CarPoolMode.FIXED_CARS);
            seasons.findById(target.season().getId()).orElseThrow().getCars().add(cars.findById(mr).orElseThrow());
        });
        // when
        var result = save(target.race().getId(), target.matchday().getId(), source.homeTeam().getId(), source.awayTeam().getId(),
                null, mr, 12);
        // then
        assertThat(result.success()).isTrue();
        tx.executeWithoutResult(status -> {
            assertThat(selections.availability(source.season().getId(), source.homeTeam().getId(), null).consumedEntryIds())
                    .containsExactly(sourceEntry);
            assertThat(selections.availability(target.season().getId(), source.homeTeam().getId(), null).usedCarIds())
                    .containsExactly(mr);
        });
    }

    private RaceService.SaveResult save(UUID id, UUID matchday, UUID home, UUID away, UUID entry, UUID car, int laps) {
        return raceService.saveRace(id, matchday, home, away, null, car, null, laps, null, null, null, null, null,
                TimeProgressionMode.MULTIPLIER, 0, null, null, null, null, entry);
    }

    private UUID freeze(Season season) {
        pools.changeMode(season.getId(), CarPoolMode.CATEGORY_POOL);
        var entry = pools.saveEntry(season.getId(), null, "Test move combined entry", List.of(
                new SeasonCarPoolService.ClauseInput(CarCategory.GR_3, null, 9894)));
        pools.freeze(season.getId());
        return entry;
    }

    private UUID car(String suffix, String name) {
        var car = new Car("Test move " + suffix, name);
        car.setCategory(CarCategory.GR_3);
        car.setDrivetrain(Drivetrain.valueOf(name));
        car.setModelYear(9894);
        car.setModelYearOverride(true);
        car.setModelYearSource("Test fixture");
        car.setModelYearEvidence("Test accepted local year");
        return cars.saveAndFlush(car).getId();
    }
}
