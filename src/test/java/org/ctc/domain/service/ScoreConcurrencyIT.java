package org.ctc.domain.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.ctc.TestHelper;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.repository.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@CtcDevSpringBootContext
@Tag("integration")
class ScoreConcurrencyIT {
    @Autowired TestHelper helper;
    @Autowired RaceService raceService;
    @Autowired RaceRepository races;
    @Autowired MatchRepository matches;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired DriverRepository drivers;
    @Autowired RaceLineupRepository lineups;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean ScoringService scoring;
    TransactionTemplate tx;
    TestHelper.SeasonFixture graph;
    UUID secondRace;
    UUID homeDriver;
    UUID awayDriver;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            var suffix = UUID.randomUUID().toString().substring(0, 8);
            graph = helper.createFullSeasonFixture("TSC" + suffix);
            var second = new Race();
            second.setMatchday(graph.matchday());
            second.setMatch(graph.match());
            secondRace = races.saveAndFlush(second).getId();
            homeDriver = drivers.save(new Driver("TSC_H_" + suffix, "Test Home")).getId();
            awayDriver = drivers.save(new Driver("TSC_A_" + suffix, "Test Away")).getId();
            for (var race : List.of(graph.race(), second)) {
                lineups.save(new RaceLineup(race, drivers.findById(homeDriver).orElseThrow(), graph.homeTeam()));
                lineups.save(new RaceLineup(race, drivers.findById(awayDriver).orElseThrow(), graph.awayTeam()));
                raceService.saveResults(race.getId(), result(1, 2));
            }
        });
    }

    @AfterEach
    void cleanUp() {
        if (graph == null) return;
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(graph.season().getId()).orElseThrow());
            seasons.flush();
            drivers.deleteAllById(List.of(homeDriver, awayDriver));
            teams.deleteAllById(List.of(graph.homeTeam().getId(), graph.awayTeam().getId()));
        });
    }

    @Test
    void givenTwoLegsEditedConcurrently_whenBothCommit_thenTheAggregateContainsBothChanges() throws Exception {
        // given
        var firstSaved = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var secondAggregating = new CountDownLatch(1);
        doAnswer(call -> {
            if (Thread.currentThread().getName().equals("score-second")) secondAggregating.countDown();
            return call.callRealMethod();
        }).when(scoring).aggregateMatchScores(any());
        // when
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> tx.execute(status -> {
                raceService.saveResults(graph.race().getId(), result(3, 4));
                firstSaved.countDown();
                await(releaseFirst);
                return true;
            }));
            try {
                assertThat(firstSaved.await(10, TimeUnit.SECONDS)).isTrue();
                var second = workers.submit(() -> {
                    Thread.currentThread().setName("score-second");
                    secondStarted.countDown();
                    try {
                        raceService.saveResults(secondRace, result(5, 6));
                        return (BusinessRuleException) null;
                    } catch (BusinessRuleException rejected) {
                        return rejected;
                    }
                });
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(secondAggregating.await(300, TimeUnit.MILLISECONDS))
                        .as("the second writer waits before reading any score sources").isFalse();
                releaseFirst.countDown();
                assertThat(first.get(15, TimeUnit.SECONDS)).isTrue();
                var rejected = second.get(15, TimeUnit.SECONDS);
                if (rejected != null) {
                    assertThat(snapshotConflict(rejected)).as("only MariaDB's stale-snapshot rejection is retryable").isTrue();
                    tx.executeWithoutResult(status -> assertThat(races.findById(secondRace).orElseThrow().getResults())
                            .extracting(row -> row.getPosition()).containsExactly(1, 2));
                    raceService.saveResults(secondRace, result(5, 6));
                }
            } finally {
                releaseFirst.countDown();
            }
        }
        // then
        tx.executeWithoutResult(status -> {
            var legs = races.findByMatchId(graph.match().getId());
            var expectedHome = legs.stream().flatMap(race -> race.getResults().stream())
                    .filter(row -> row.getDriver().getId().equals(homeDriver)).mapToInt(row -> row.getPointsTotal()).sum();
            var expectedAway = legs.stream().flatMap(race -> race.getResults().stream())
                    .filter(row -> row.getDriver().getId().equals(awayDriver)).mapToInt(row -> row.getPointsTotal()).sum();
            var stored = matches.findById(graph.match().getId()).orElseThrow();
            assertThat(stored.getHomeScore()).isEqualTo(expectedHome);
            assertThat(stored.getAwayScore()).isEqualTo(expectedAway);
        });
    }

    private List<RaceService.RaceResultData> result(int home, int away) {
        return List.of(new RaceService.RaceResultData(homeDriver, null, null, home, home, false),
                new RaceService.RaceResultData(awayDriver, null, null, away, away, false));
    }

    private static boolean snapshotConflict(Throwable failure) {
        for (var cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException sql && sql.getErrorCode() == 1020) return true;
        }
        return false;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Writer was not released");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
