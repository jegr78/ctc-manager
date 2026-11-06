package org.ctc.domain.service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.SeasonLockingRepositoryImpl;
import org.springframework.test.util.AopTestUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doAnswer;

@Tag("integration")
class MatchdayPickWindowConcurrencyIT extends PickPersistenceIT {
    @Autowired PickWindowService windows;
    @Autowired PickSessionEpoch epoch;
    @MockitoSpyBean SeasonLockingRepositoryImpl lockedSeasons;

    @Test
    void givenConfirmationWaitingForTheSeasonLock_whenManagementClosesTheRound_thenUseTheCommittedClosedFlag() throws Exception {
        concurrentChange(() -> entityManager.find(Matchday.class, fixture.matchday().getId()).setPicksOpen(false),
                () -> submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload), "closed");
        assertThat(picks.findSnapshotByRaceId(fixture.race().getId())).isEmpty();
    }

    @Test
    void givenConfirmationWaitingForTheSeasonLock_whenTheMatchExceptionExpires_thenUseTheCommittedDeadline() throws Exception {
        concurrentChange(() -> entityManager.find(Match.class, fixture.match().getId()).setPickDeadlineOverride(LocalDateTime.of(2020, 1, 1, 20, 0)),
                () -> submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload), "deadline");
        assertThat(picks.findSnapshotByRaceId(fixture.race().getId())).isEmpty();
    }

    @Test
    void givenPairingEditorReadingBeforeALockWait_whenItsVersionChanges_thenRejectItsStaleSnapshot() throws Exception {
        var view = windows.readPairing(fixture.match().getId(), false);
        var manager = new ManagementPickActor("test-window-manager", epoch.managementGeneration());
        concurrentChange(() -> {
            var pair = entityManager.find(Match.class, fixture.match().getId()); pair.setPickWindowVersion(view.version() + 1);
            pair.setPickDeadlineOverride(LocalDateTime.of(2036, 1, 1, 20, 0));
        }, () -> { windows.savePairing(view.id(), false, LocalDateTime.of(2037, 1, 1, 20, 0), view.version(), view.scope(), manager); return null; }, "changed");
        assertThat(windows.readPairing(view.id(), false).override()).isEqualTo(LocalDateTime.of(2036, 1, 1, 20, 0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void givenAPlayoffLegMovedWhileTheEditorWaits_whenSavingEitherRoute_thenRejectTheChangedCanonicalPairing(boolean matchRoute) throws Exception {
        var ids = playoffFixture();
        var view = windows.readPairing(matchRoute ? ids[1] : ids[0], !matchRoute);
        var manager = new ManagementPickActor("test-window-manager", epoch.managementGeneration());
        concurrentChange(() -> {
            var moved = entityManager.find(Race.class, ids[2]); moved.setMatchday(entityManager.find(Matchday.class, ids[3]));
            moved.setMatch(entityManager.find(Match.class, ids[4]));
        }, () -> { windows.savePairing(view.id(), !matchRoute, LocalDateTime.of(2036, 1, 1, 20, 0), view.version(), view.scope(), manager); return null; }, "matchdays");
        tx.executeWithoutResult(status -> assertThat(entityManager.find(PlayoffMatchup.class, ids[0]).getPickDeadlineOverride()).isNull());
    }

    @Test
    void givenPlayoffMetadataChangedDuringTheLockWait_whenSaving_thenCompareCurrentScalarOwnership() throws Exception {
        var ids = playoffFixture();
        var otherPhase = tx.execute(status -> {
            var phase = new SeasonPhase(entityManager.find(Season.class, fixture.season().getId()), PhaseType.PLACEMENT, PhaseLayout.BRACKET, 20);
            phase.setLabel("Test changed playoff context"); entityManager.persist(phase); entityManager.flush(); return phase.getId();
        });
        var view = windows.readPairing(ids[0], true);
        var manager = new ManagementPickActor("test-window-manager", epoch.managementGeneration());
        concurrentChange(() -> entityManager.find(Playoff.class, ids[5]).setPhase(entityManager.find(SeasonPhase.class, otherPhase)),
                () -> { windows.savePairing(view.id(), true, LocalDateTime.of(2036, 1, 1, 20, 0), view.version(), view.scope(), manager); return null; }, "context changed");
        tx.executeWithoutResult(status -> assertThat(entityManager.find(PlayoffMatchup.class, ids[0]).getPickDeadlineOverride()).isNull());
    }

    private UUID[] playoffFixture() {
        return tx.execute(status -> {
            var season = entityManager.find(Season.class, fixture.season().getId());
            var phase = new SeasonPhase(season, PhaseType.PLAYOFF, PhaseLayout.BRACKET, 10); phase.setLabel("Test concurrent playoff"); entityManager.persist(phase);
            var playoff = new Playoff(phase, "Test concurrent playoff"); entityManager.persist(playoff);
            var round = new PlayoffRound(playoff, "Test final", 0); entityManager.persist(round);
            var pairing = new PlayoffMatchup(round, 0); pairing.setTeam1(entityManager.find(Team.class, fixture.homeTeam().getId()));
            pairing.setTeam2(entityManager.find(Team.class, fixture.awayTeam().getId())); entityManager.persist(pairing);
            var day = new Matchday(phase, "Test original round", 1); entityManager.persist(day);
            var other = new Matchday(phase, "Test changed round", 2); entityManager.persist(other);
            var bridge = new Match(day, pairing.getTeam1(), pairing.getTeam2()); entityManager.persist(bridge);
            var otherBridge = new Match(other, pairing.getTeam1(), pairing.getTeam2()); entityManager.persist(otherBridge);
            Race moving = null;
            for (int leg = 0; leg < 2; leg++) {
                var race = new Race(); race.setMatchday(day); race.setMatch(bridge); race.setPlayoffMatchup(pairing); entityManager.persist(race); moving = race;
            }
            entityManager.flush(); return new UUID[]{pairing.getId(), bridge.getId(), moving.getId(), other.getId(), otherBridge.getId(), playoff.getId()};
        });
    }

    private void concurrentChange(Runnable change, java.util.concurrent.Callable<?> waiting, String message) throws Exception {
        var held = new CountDownLatch(1); var release = new CountDownLatch(1); var attempted = new CountDownLatch(1);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var owner = workers.submit(() -> tx.execute(status -> {
                seasons.findByIdForUpdate(fixture.season().getId()).orElseThrow();
                change.run(); entityManager.flush(); held.countDown();
                try { assertThat(release.await(15, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                return null;
            }));
            try {
                assertThat(held.await(15, TimeUnit.SECONDS)).isTrue();
                doAnswer(call -> { attempted.countDown(); return call.callRealMethod(); })
                        .when(AopTestUtils.<SeasonLockingRepositoryImpl>getUltimateTargetObject(lockedSeasons)).findByIdForUpdate(fixture.season().getId());
                var writer = workers.submit(waiting);
                assertThat(attempted.await(15, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> writer.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown(); owner.get(15, TimeUnit.SECONDS);
                var rejected = catchThrowable(() -> writer.get(15, TimeUnit.SECONDS));
                assertThat(rejected).hasCauseInstanceOf(BusinessRuleException.class);
                String reason = rejected.getCause().getMessage();
                assertThat(reason.contains(message) || reason.startsWith("Selections changed while saving."))
                        .as("current context rejection or MariaDB's explicit snapshot-conflict rejection: %s", reason).isTrue();
            } finally { release.countDown(); owner.get(15, TimeUnit.SECONDS); }
        }
    }
}
