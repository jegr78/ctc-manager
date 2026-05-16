package org.ctc.backup.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.LongConsumer;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;

/**
 * A {@link FilterInputStream} that counts inflated bytes passing through {@link #read()} and
 * {@link #read(byte[], int, int)}, and throws {@link BackupArchiveException} with reason
 * {@link Reason#ENTRY_TOO_LARGE} once the running count exceeds the configured {@code limit}.
 *
 * <h3>Rationale — the {@code ZipEntry.getSize()} trust problem</h3>
 * Malicious ZIP archives can lie in the central directory: they may claim an entry is 1 KB in
 * the header while the inflated content expands to 5 GB. {@code ZipEntry.getSize()} reflects
 * the central-directory value, not the truth. The only reliable defence is to count the actual
 * bytes produced by the {@code InflaterInputStream} against {@code MAX_ENTRY_BYTES} — that is
 * exactly what this class does.
 *
 * <h3>{@code onClose} contract — exactly once, callback-before-throw</h3>
 * The {@code onClose} {@link LongConsumer} fires EXACTLY ONCE with the final inflated byte count:
 * <ul>
 *   <li>On the <em>success path</em>: when {@link #close()} is called after a complete or partial
 *       drain (the {@code count} at that moment is delivered).</li>
 *   <li>On the <em>limit-exceeded path</em>: immediately BEFORE throwing
 *       {@link BackupArchiveException}. The throw follows the callback — callers can read the
 *       final byte count from the callback even after catching the exception.</li>
 * </ul>
 * Multiple {@link #close()} calls fire the callback at most once (idempotency guard via
 * {@code onCloseFired}). A {@code null} {@code onClose} is silently skipped.
 *
 * <h3>Canonical call site ({@code BackupArchiveService.read*})</h3>
 * {@code new LimitedInputStream(zipInputStream, BackupImportLimits.MAX_ENTRY_BYTES,
 * finalBytes -> totalInflatedAcc[0] += finalBytes)}
 */
public final class LimitedInputStream extends FilterInputStream {

    /** The maximum number of bytes allowed to pass through before throwing. */
    private final long limit;

    /**
     * Callback invoked exactly once with the final inflated byte count.
     * May be {@code null} — null is silently skipped.
     */
    private final LongConsumer onClose;

    /** Running byte count; monotonically increasing. */
    private long count;

    /** Guard ensuring {@code onClose} fires at most once. */
    private boolean onCloseFired;

    /**
     * Creates a new {@code LimitedInputStream}.
     *
     * @param delegate the underlying stream (typically a {@code ZipInputStream} entry stream)
     * @param limit    maximum number of inflated bytes allowed; exceeding this throws
     *                 {@link BackupArchiveException}({@link Reason#ENTRY_TOO_LARGE})
     * @param onClose  callback invoked exactly once with the final inflated byte count; may be
     *                 {@code null}
     */
    public LimitedInputStream(InputStream delegate, long limit, LongConsumer onClose) {
        super(delegate);
        this.limit = limit;
        this.onClose = onClose;
    }

    /**
     * Reads a single byte, increments the running count, and throws if the count exceeds the
     * limit. The {@code onClose} callback fires BEFORE the exception is thrown (callback-first
     * contract).
     *
     * @return the byte value (0–255), or {@code -1} at EOF
     * @throws BackupArchiveException if the cumulative byte count exceeds {@link #limit}
     * @throws IOException            if the underlying stream throws
     */
    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b == -1) {
            return -1;
        }
        count++;
        if (count > limit) {
            fireOnClose();
            throw new BackupArchiveException(Reason.ENTRY_TOO_LARGE,
                    "Entry exceeds limit: limit=" + limit + " bytes");
        }
        return b;
    }

    /**
     * Reads up to {@code len} bytes into {@code b}, increments the running count by the number
     * of bytes actually read, and throws if the count exceeds the limit. The {@code onClose}
     * callback fires BEFORE the exception is thrown.
     *
     * <p>Note: bytes already placed into {@code b} by the underlying {@code super.read} call
     * are NOT rolled back when the limit is exceeded — defence-in-depth lives one level up;
     * the caller must discard the partial entry after catching the exception.
     *
     * @return the number of bytes read, or {@code -1} at EOF
     * @throws BackupArchiveException if the cumulative byte count exceeds {@link #limit}
     * @throws IOException            if the underlying stream throws
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n == -1) {
            return -1;
        }
        count += n;
        if (count > limit) {
            fireOnClose();
            throw new BackupArchiveException(Reason.ENTRY_TOO_LARGE,
                    "Entry exceeds limit: limit=" + limit + " bytes");
        }
        return n;
    }

    /**
     * Closes the underlying stream after firing the {@code onClose} callback exactly once with
     * the current byte count. Idempotent — multiple calls fire the callback at most once.
     *
     * @throws IOException if the underlying stream throws
     */
    @Override
    public void close() throws IOException {
        if (!onCloseFired) {
            onCloseFired = true;
            if (onClose != null) {
                onClose.accept(count);
            }
        }
        super.close();
    }

    // skip(), mark(), reset(), available() are NOT overridden — FilterInputStream defaults suffice.

    // ----- private helpers -----

    /**
     * Fires the {@code onClose} callback exactly once (guarded by {@link #onCloseFired}).
     * Called on the limit-exceeded path BEFORE the throw.
     */
    private void fireOnClose() {
        if (!onCloseFired) {
            onCloseFired = true;
            if (onClose != null) {
                onClose.accept(count);
            }
        }
    }
}
