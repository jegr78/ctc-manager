package org.ctc.domain.service;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.SeasonRepository;
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
    @MockitoSpyBean SeasonRepository lockedSeasons;

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
                        .when(lockedSeasons).findByIdForUpdate(fixture.season().getId());
                var writer = workers.submit(waiting);
                assertThat(attempted.await(15, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> writer.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown(); owner.get(15, TimeUnit.SECONDS);
                assertThatThrownBy(() -> writer.get(15, TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessRuleException.class)
                        .cause().hasMessageContaining(message);
            } finally { release.countDown(); owner.get(15, TimeUnit.SECONDS); }
        }
    }
}
