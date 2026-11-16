package org.ctc.media.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.service.PickSessionEpoch;
import org.junit.jupiter.api.Test;

class MediaJobExecutorTest {

    private final List<Runnable> queued = new ArrayList<>();
    private final ImportLockService imports = new ImportLockService();
    private final PickSessionEpoch epoch = new PickSessionEpoch();
    private final MediaJobExecutor executor = new MediaJobExecutor(imports, epoch, queued::add,
            Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void givenRunningJob_whenSubmitSecond_thenBusy() {
        // given
        executor.submit(MediaJobKind.CUTOUT, "car:a", ctx -> { });

        // when / then
        assertThatThrownBy(() -> executor.submit(MediaJobKind.TRAILER, "matchday:b", ctx -> { }))
                .isInstanceOf(MediaJobBusyException.class)
                .hasMessage("Another media job is running.");
    }

    @Test
    void givenFinishedJob_whenSubmit_thenAccepted() {
        // given
        executor.submit(MediaJobKind.CUTOUT, "car:a", ctx -> ctx.progress(1, 1, "Done"));
        queued.removeFirst().run();

        // when
        var status = executor.submit(MediaJobKind.CUTOUT, "car:a", ctx -> { });

        // then
        assertThat(status.state()).isEqualTo(MediaJobState.QUEUED);
    }

    @Test
    void givenJobWithProgress_whenFinished_thenDoneWithLastProgress() {
        // given
        executor.submit(MediaJobKind.TRAILER, "matchday:b", ctx -> ctx.progress(3, 5, "Rendering scenes"));

        // when
        queued.removeFirst().run();

        // then
        assertThat(executor.status("matchday:b")).hasValueSatisfying(status -> {
            assertThat(status.state()).isEqualTo(MediaJobState.DONE);
            assertThat(status.step()).isEqualTo(3);
            assertThat(status.steps()).isEqualTo(5);
        });
    }

    @Test
    void givenRestoreEventDuringJob_whenCheckNotAbandoned_thenAbandonedState() {
        // given
        executor.submit(MediaJobKind.TRAILER, "matchday:b", ctx -> {
            epoch.afterRestore(null);
            ctx.checkNotAbandoned();
        });

        // when
        queued.removeFirst().run();

        // then
        assertThat(executor.status("matchday:b")).hasValueSatisfying(status -> {
            assertThat(status.state()).isEqualTo(MediaJobState.ABANDONED);
            assertThat(status.message()).isEqualTo("A backup restore replaced the data; start again.");
        });
    }

    @Test
    void givenJobThrows_whenFinished_thenFailedWithSanitizedMessage() {
        // given
        executor.submit(MediaJobKind.CUTOUT, "car:a", ctx -> {
            throw new IllegalStateException("broken\nline");
        });

        // when
        queued.removeFirst().run();

        // then
        assertThat(executor.status("car:a")).hasValueSatisfying(status -> {
            assertThat(status.state()).isEqualTo(MediaJobState.FAILED);
            assertThat(status.message()).doesNotContain("\n").contains("broken");
        });
    }

    @Test
    void givenJobThrowsVirtualMachineError_whenFinished_thenFailedAndNextJobAccepted() {
        // given
        executor.submit(MediaJobKind.CUTOUT, "car:a", ctx -> {
            throw new StackOverflowError();
        });

        // when
        queued.removeFirst().run();

        // then
        assertThat(executor.status("car:a")).hasValueSatisfying(status -> assertThat(status.state()).isEqualTo(MediaJobState.FAILED));
        assertThat(executor.submit(MediaJobKind.CUTOUT, "car:b", ctx -> { }).state()).as("executor is free again")
                .isEqualTo(MediaJobState.QUEUED);
    }

    @Test
    void givenImportLockHeld_whenSubmit_thenRejected() {
        // given
        imports.tryLock();

        // when / then
        assertThatThrownBy(() -> executor.submit(MediaJobKind.CUTOUT, "car:a", ctx -> { }))
                .isInstanceOf(MediaJobBusyException.class)
                .hasMessage("A restore is running.");
    }

    @Test
    void givenUnknownScope_whenStatus_thenEmpty() {
        // when / then
        assertThat(executor.status("car:none")).isEmpty();
    }
}
