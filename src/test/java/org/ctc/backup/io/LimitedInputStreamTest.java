package org.ctc.backup.io;

import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimitedInputStreamTest {

    @Test
    void givenStreamUnderLimit_whenRead_thenAllBytesReturned() throws Exception {
        // given
        byte[] data = new byte[1024];
        LimitedInputStream limited = new LimitedInputStream(new ByteArrayInputStream(data), 2_048L, null);

        // when
        int total = 0;
        byte[] buf = new byte[256];
        int n;
        while ((n = limited.read(buf, 0, buf.length)) != -1) {
            total += n;
        }

        // then
        assertThat(total).isEqualTo(1024);
        assertThat(limited.read()).isEqualTo(-1);
        limited.close();
    }

    @Test
    void givenStreamUnderLimit_whenClose_thenOnCloseFiresWithFinalByteCount() throws Exception {
        // given
        byte[] data = new byte[1024];
        long[] captured = {-1L};
        LimitedInputStream limited = new LimitedInputStream(new ByteArrayInputStream(data), 2_048L, bytes -> captured[0] = bytes);

        // when — drain fully
        byte[] buf = new byte[256];
        while (limited.read(buf, 0, buf.length) != -1) {
            // consume
        }
        limited.close();

        // then — success-path contract: onClose delivers the final byte count
        assertThat(captured[0]).isEqualTo(1024L);
    }

    @Test
    void givenStreamExceedingLimit_whenRead_thenOnCloseFiresWithLimitPlusOne_thenThrows() {
        // given
        byte[] data = new byte[1024];
        long[] captured = {-1L};
        LimitedInputStream limited = new LimitedInputStream(new ByteArrayInputStream(data), 512L, bytes -> captured[0] = bytes);

        // when / then
        assertThatThrownBy(() -> {
            while (limited.read() != -1) {
                // consume single bytes until limit tripped
            }
        })
                .isInstanceOf(BackupArchiveException.class)
                .satisfies(ex -> {
                    BackupArchiveException bae = (BackupArchiveException) ex;
                    assertThat(bae.reason()).isEqualTo(Reason.ENTRY_TOO_LARGE);
                    assertThat(bae.getMessage()).contains("limit=512");
                });

        // then — callback must have fired BEFORE the exception; captured[0] != -1 proves ordering
        assertThat(captured[0]).isEqualTo(513L); // 512 + 1: the single byte that crossed the limit
    }

    @Test
    void givenBulkReadCrossesLimit_whenRead_thenThrowsOnTheChunkThatCrossesLimit_andOnCloseFires() throws Exception {
        // given
        byte[] data = new byte[2048];
        long[] captured = {-1L};
        LimitedInputStream limited = new LimitedInputStream(new ByteArrayInputStream(data), 1_500L, bytes -> captured[0] = bytes);

        // when — first chunk: 1024 bytes, cumulative = 1024, still under limit
        byte[] buf = new byte[1024];
        int firstRead = limited.read(buf, 0, buf.length);
        assertThat(firstRead).isEqualTo(1024);

        // when — second chunk: 1024 bytes would push count to 2048, which exceeds limit 1500
        assertThatThrownBy(() -> limited.read(buf, 0, buf.length))
                .isInstanceOf(BackupArchiveException.class)
                .satisfies(ex -> {
                    BackupArchiveException bae = (BackupArchiveException) ex;
                    assertThat(bae.reason()).isEqualTo(Reason.ENTRY_TOO_LARGE);
                });

        // then — bytes already in buf when limit was crossed are intentionally NOT rolled back
        // (defence-in-depth lives one level up — the caller discards the partial entry)
        assertThat(captured[0]).isEqualTo(2048L); // 1024 from chunk 1 + 1024 from chunk 2
    }

    @Test
    void givenOnCloseCallback_whenCloseCalledTwice_thenCallbackFiresOnlyOnce() throws Exception {
        // given
        byte[] data = new byte[16];
        AtomicInteger fireCount = new AtomicInteger(0);
        LimitedInputStream limited = new LimitedInputStream(new ByteArrayInputStream(data), 1024L, bytes -> fireCount.incrementAndGet());

        // when — close twice (simulates try-with-resources nesting)
        limited.close();
        limited.close();

        // then — idempotency: callback fires exactly once
        assertThat(fireCount.get()).isEqualTo(1);
    }

    @Test
    void givenNullOnClose_whenLimitExceeded_thenThrowsWithoutNullPointerException() {
        // given
        byte[] data = new byte[1024];
        LimitedInputStream limited = new LimitedInputStream(new ByteArrayInputStream(data), 512L, null);

        // when / then — must throw BackupArchiveException, NOT NullPointerException
        assertThatThrownBy(() -> {
            while (limited.read() != -1) {
                // consume
            }
        })
                .isInstanceOf(BackupArchiveException.class)
                .satisfies(ex -> assertThat(((BackupArchiveException) ex).reason()).isEqualTo(Reason.ENTRY_TOO_LARGE));
    }
}
