package org.ctc.domain.service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.dto.Webhook;
import org.ctc.discord.service.DiscordChannelService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.IncidentEventKind;
import org.ctc.domain.model.IncidentReportEvent;
import org.ctc.domain.model.IncidentReportWindow;
import org.ctc.domain.model.IncidentWindowState;
import org.ctc.domain.model.TimeProgressionMode;
import org.ctc.domain.repository.IncidentReportEventRepository;
import org.ctc.domain.repository.IncidentReportWindowRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("integration")
class IncidentWindowConcurrencyIT extends IncidentIntegrationFixture {
    @Autowired RaceService raceService;
    @Autowired org.ctc.backup.lock.ImportLockService restoreLock;
    @Autowired DiscordChannelService channels;
    @MockitoBean DiscordRestClient discord;
    @Autowired SeasonRepository seasonLocks;
    @Autowired IncidentReportWindowRepository windowSaves;
    @Autowired IncidentReportEventRepository eventSaves;

    @Test
    void twoEditsFromOneVersionCommitExactlyOneNewRevision() throws Exception {
        var original = submit(); var start = new CyclicBarrier(2);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Throwable> action = () -> attempt(() -> {
                start.await(10, TimeUnit.SECONDS);
                return reports.edit(original.reportId(), 1, 1, UUID.randomUUID(), actor, payload);
            });
            var first = workers.submit(action); var second = workers.submit(action);
            var outcomes = Arrays.asList(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(outcomes).filteredOn(java.util.Objects::isNull).hasSize(1);
            assertThat(outcomes).filteredOn(java.util.Objects::nonNull).singleElement()
                    .satisfies(failure -> assertThat(failure).isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed"));
        }
        tx.executeWithoutResult(status -> {
            assertThat(reportRows.findById(original.reportId()).orElseThrow().getVersion()).isEqualTo(2);
            assertThat(revisionRows.findByReportIdOrderByRevisionNumberAsc(original.reportId())).hasSize(2);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"submit", "edit", "withdraw"})
    void aMutationWaitingForTheSeasonLockUsesTheClockAfterAcquiringIt(String operation) throws Exception {
        var original = submit(); var held = new CountDownLatch(1); var release = new CountDownLatch(1);
        var reached = new CountDownLatch(1); var contender = new AtomicReference<Thread>();
        observeSeasonAttempt(contender, reached);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var holder = workers.submit(() -> tx.executeWithoutResult(status -> {
                seasons.findByIdForUpdate(fixture.season().getId()).orElseThrow(); held.countDown(); await(release);
            }));
            try {
                await(held);
                var writer = workers.submit(() -> {
                    contender.set(Thread.currentThread());
                    return attempt(() -> switch (operation) {
                        case "submit" -> reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload);
                        case "edit" -> reports.edit(original.reportId(), 1, 1, UUID.randomUUID(), actor, payload);
                        default -> reports.withdraw(original.reportId(), 1, 1, UUID.randomUUID(), actor);
                    });
                });
                await(reached); clock.set(NOW.plusSeconds(3600)); release.countDown();
                holder.get(10, TimeUnit.SECONDS);
                assertThat(writer.get(10, TimeUnit.SECONDS)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("closed");
            } finally { release.countDown(); }
        }
        tx.executeWithoutResult(status -> {
            var report = reportRows.findById(original.reportId()).orElseThrow();
            assertThat(report.getVersion()).isEqualTo(1); assertThat(report.getState().name()).isEqualTo("ACTIVE");
            assertThat(reportRows.findByWindowIdOrderByCaseReferenceAsc(windowId)).hasSize(1);
            assertThat(windowRows.findById(windowId).orElseThrow().getState()).isEqualTo(IncidentWindowState.OPEN);
        });
    }

    @Test
    void closureWaitsForTheAcceptedEditAndFreezesItsCommittedRevision() throws Exception {
        var original = submit(); var recorded = new CountDownLatch(1); var release = new CountDownLatch(1);
        intercept(eventSaves, invocation -> {
            var result = invocation.proceed();
            if (invocation.getMethod().getName().equals("save") && invocation.getArguments()[0] instanceof IncidentReportEvent event
                    && event.getKind() == IncidentEventKind.EDITED) { em.flush(); recorded.countDown(); await(release); }
            return result;
        });
        var reached = new CountDownLatch(1); var contender = new AtomicReference<Thread>(); observeSeasonAttempt(contender, reached);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var edit = workers.submit(() -> reports.edit(original.reportId(), 1, 1, UUID.randomUUID(), actor, payload));
            try {
                await(recorded); clock.set(NOW.plusSeconds(3600));
                var close = workers.submit(() -> {
                    contender.set(Thread.currentThread()); return attempt(() -> { assertThat(windows.closeDue(windowId, NOW)).isTrue(); return null; });
                });
                await(reached); release.countDown(); edit.get(10, TimeUnit.SECONDS);
                var conflict = close.get(10, TimeUnit.SECONDS);
                if (conflict != null) {
                    assertThat(conflict).isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
                    tx.executeWithoutResult(status -> {
                        var report = reportRows.findById(original.reportId()).orElseThrow();
                        assertThat(report.getVersion()).isEqualTo(2); assertThat(report.getLatestRevisionNumber()).isEqualTo(2);
                        assertThat(report.getFrozenRevisionNumber()).isNull();
                        assertThat(windowRows.findById(windowId).orElseThrow().getState()).isEqualTo(IncidentWindowState.OPEN);
                    });
                    assertThat(windows.closeDue(windowId, NOW)).isTrue();
                }
            } finally { release.countDown(); }
        }
        tx.executeWithoutResult(status -> {
            var report = reportRows.findById(original.reportId()).orElseThrow();
            assertThat(report.getLatestRevisionNumber()).isEqualTo(2); assertThat(report.getFrozenRevisionNumber()).isEqualTo(2);
            assertThat(revisionRows.findByReportIdOrderByRevisionNumberAsc(original.reportId())).hasSize(2);
            assertThat(windowRows.findById(windowId).orElseThrow().getState()).isEqualTo(IncidentWindowState.CLOSED);
        });
    }

    @Test
    void raceIdentitySaveSeesAContextCommittedWhileItWaitedForTheSeason() throws Exception {
        removeDraftBinding(); var stored = new CountDownLatch(1); var release = new CountDownLatch(1); pauseBinding(stored, release);
        var reached = new CountDownLatch(1); var contender = new AtomicReference<Thread>(); observeSeasonAttempt(contender, reached);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var binding = workers.submit(() -> contexts.bind(org.ctc.domain.incidents.IncidentMatchRef.match(fixture.match().getId()), GUILD, CHANNEL, manager));
            try {
                await(stored);
                var edit = workers.submit(() -> {
                    contender.set(Thread.currentThread());
                    try {
                        return (Object) raceService.saveRace(fixture.race().getId(), fixture.matchday().getId(), fixture.awayTeam().getId(), fixture.homeTeam().getId(),
                                null, null, null, null, null, null, null, null, null, TimeProgressionMode.MULTIPLIER, 0, null, null, null, null);
                    } catch (BusinessRuleException retry) { return retry; }
                });
                await(reached); release.countDown(); contextId = binding.get(10, TimeUnit.SECONDS);
                var result = edit.get(10, TimeUnit.SECONDS);
                if (result instanceof RaceService.SaveResult saved) {
                    assertThat(saved.success()).isFalse(); assertThat(saved.message()).contains("incident");
                } else assertThat((Throwable) result).isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
            } finally { release.countDown(); }
        }
        tx.executeWithoutResult(status -> assertThat(matches.findById(fixture.match().getId()).orElseThrow().getHomeTeam().getId())
                .isEqualTo(fixture.homeTeam().getId()));
    }

    @Test
    void channelLinkSeesAContextCommittedWhileItWaitedForTheGuildLock() throws Exception {
        removeDraftBinding();
        var other = tx.execute(status -> {
            var extra = helper.createFullSeasonFixture("TICRace" + UUID.randomUUID().toString().substring(0, 5)); extras.add(extra); return extra.match();
        });
        when(discord.listWebhooks(CHANNEL)).thenReturn(List.of(new Webhook("test-hook", "CTC Manager", "test-token", "https://example.invalid/test", CHANNEL)));
        var stored = new CountDownLatch(1); var release = new CountDownLatch(1); pauseBinding(stored, release);
        var reached = new CountDownLatch(1); var contender = new AtomicReference<Thread>(); observeSeasonAttempt(contender, reached);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var binding = workers.submit(() -> contexts.bind(org.ctc.domain.incidents.IncidentMatchRef.match(fixture.match().getId()), GUILD, CHANNEL, manager));
            try {
                await(stored);
                var link = workers.submit(() -> {
                    contender.set(Thread.currentThread());
                    return attempt(() -> { channels.linkExistingChannel(other, CHANNEL); return null; });
                });
                await(reached); release.countDown(); contextId = binding.get(10, TimeUnit.SECONDS);
                assertThat(link.get(10, TimeUnit.SECONDS)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("incident");
                verify(discord, never()).fetchChannel(any());
            } finally { release.countDown(); }
        }
    }

    @Test
    void deletionSeesAReportCommittedWhileItWaitedForTheSeason() throws Exception {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        var stored = new CountDownLatch(1); var release = new CountDownLatch(1);
        intercept(eventSaves, invocation -> {
            var result = invocation.proceed();
            if (invocation.getMethod().getName().equals("save") && invocation.getArguments()[0] instanceof IncidentReportEvent) {
                em.flush(); stored.countDown(); await(release);
            }
            return result;
        });
        var reached = new CountDownLatch(1); var contender = new AtomicReference<Thread>(); observeSeasonAttempt(contender, reached);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var save = workers.submit(() -> reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload));
            try {
                await(stored);
                var deletion = workers.submit(() -> {
                    contender.set(Thread.currentThread()); return attempt(() -> raceService.deleteRace(fixture.race().getId()));
                });
                await(reached); release.countDown(); var receipt = save.get(10, TimeUnit.SECONDS);
                assertThat(deletion.get(10, TimeUnit.SECONDS)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("incident");
                assertThat(reportRows.existsById(receipt.reportId())).isTrue(); assertThat(races.existsById(fixture.race().getId())).isTrue();
            } finally { release.countDown(); }
        }
    }

    @Test
    void restoreAdmissionWaitsForTheCommittedEditAndRejectsNewIncidentWrites() throws Exception {
        var original = submit(); var stored = new CountDownLatch(1); var release = new CountDownLatch(1);
        intercept(eventSaves, invocation -> {
            var result = invocation.proceed();
            if (invocation.getMethod().getName().equals("save") && invocation.getArguments()[0] instanceof IncidentReportEvent event
                    && event.getKind() == IncidentEventKind.EDITED) { em.flush(); stored.countDown(); await(release); }
            return result;
        });
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var edit = workers.submit(() -> reports.edit(original.reportId(), 1, 1, UUID.randomUUID(), actor, payload));
            try {
                await(stored); assertThat(restoreLock.tryLock()).isTrue();
                var draining = new CountDownLatch(1);
                var drained = workers.submit(() -> { draining.countDown(); restoreLock.awaitWritersDrained(); return null; });
                await(draining);
                assertThatThrownBy(() -> drained.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
                assertThatThrownBy(() -> reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload))
                        .isInstanceOf(BusinessRuleException.class).hasMessageContaining("restore");
                release.countDown(); edit.get(10, TimeUnit.SECONDS); drained.get(10, TimeUnit.SECONDS);
            } finally { release.countDown(); restoreLock.unlock(); }
        }
        tx.executeWithoutResult(status -> {
            assertThat(reportRows.findById(original.reportId()).orElseThrow().getVersion()).isEqualTo(2);
            assertThat(revisionRows.findByReportIdOrderByRevisionNumberAsc(original.reportId())).hasSize(2);
        });
    }

    @Test
    void closureIncludesTheFirstReportCommittedWhileWaiting() throws Exception {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        var stored = new CountDownLatch(1); var release = new CountDownLatch(1);
        intercept(eventSaves, invocation -> {
            var result = invocation.proceed();
            if (invocation.getMethod().getName().equals("save") && invocation.getArguments()[0] instanceof IncidentReportEvent event
                    && event.getKind() == IncidentEventKind.SUBMITTED) { em.flush(); stored.countDown(); await(release); }
            return result;
        });
        var reached = new CountDownLatch(1); var contender = new AtomicReference<Thread>(); observeSeasonAttempt(contender, reached);
        UUID reportId;
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var submission = workers.submit(() -> reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload));
            try {
                await(stored); clock.set(NOW.plusSeconds(3600));
                var close = workers.submit(() -> {
                    contender.set(Thread.currentThread()); return attempt(() -> { assertThat(windows.closeDue(windowId, NOW)).isTrue(); return null; });
                });
                await(reached); release.countDown(); reportId = submission.get(10, TimeUnit.SECONDS).reportId();
                var conflict = close.get(10, TimeUnit.SECONDS);
                if (conflict != null) {
                    assertThat(conflict).isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
                    assertThat(windowRows.findById(windowId).orElseThrow().getState()).isEqualTo(IncidentWindowState.OPEN);
                    assertThat(reportRows.findById(reportId).orElseThrow().getFrozenRevisionNumber()).isNull();
                    assertThat(windows.closeDue(windowId, NOW)).isTrue();
                }
            } finally { release.countDown(); }
        }
        final UUID saved = reportId;
        tx.executeWithoutResult(status -> {
            var report = reportRows.findById(saved).orElseThrow();
            assertThat(report.getLatestRevisionNumber()).isEqualTo(1); assertThat(report.getFrozenRevisionNumber()).isEqualTo(1);
            assertThat(windowRows.findById(windowId).orElseThrow().getState()).isEqualTo(IncidentWindowState.CLOSED);
        });
    }

    private org.ctc.domain.incidents.IncidentReportReceipt submit() {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        return reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload);
    }
    private void removeDraftBinding() {
        tx.executeWithoutResult(status -> { windowRows.deleteById(windowId); windowRows.flush(); contextRows.deleteById(contextId); contextRows.flush(); });
    }
    private void observeSeasonAttempt(AtomicReference<Thread> contender, CountDownLatch reached) {
        intercept(seasonLocks, invocation -> {
            if (invocation.getMethod().getName().equals("findByIdForUpdate") && Thread.currentThread() == contender.get()) reached.countDown();
            return invocation.proceed();
        });
    }
    private void pauseBinding(CountDownLatch stored, CountDownLatch release) {
        intercept(windowSaves, invocation -> {
            var result = invocation.proceed();
            if (invocation.getMethod().getName().equals("save") && invocation.getArguments()[0] instanceof IncidentReportWindow) {
                em.flush(); stored.countDown(); await(release);
            }
            return result;
        });
    }
    private final List<Runnable> removeInterceptors = new ArrayList<>();
    private void intercept(Object repository, MethodInterceptor interceptor) {
        var advised = (Advised) repository; advised.addAdvice(0, interceptor);
        removeInterceptors.add(() -> advised.removeAdvice(interceptor));
    }
    @AfterEach
    void removeInterceptors() { removeInterceptors.forEach(Runnable::run); }

    private static Throwable attempt(Callable<?> work) {
        try { work.call(); return null; } catch (Throwable failure) { return failure; }
    }
    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
}
