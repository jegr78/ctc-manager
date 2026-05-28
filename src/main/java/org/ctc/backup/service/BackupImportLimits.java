package org.ctc.backup.service;

/**
 * Constants holder for ZIP-bomb defense thresholds.
 *
 * <p>Consumers:
 * <ul>
 *   <li>{@code LimitedInputStream} — reads {@link #MAX_ENTRY_BYTES} as the per-entry inflated
 *       byte cap passed to its constructor by {@code BackupArchiveService.read*}.</li>
 *   <li>{@code BackupArchiveService.read*} — enforces {@link #MAX_TOTAL_BYTES} as the running
 *       total of all inflated entry bytes across the archive, and {@link #MAX_ENTRIES} as the
 *       cap on the number of ZIP entries.</li>
 * </ul>
 *
 * <p>All three values are written as literal multiplication expressions so a code-review can
 * verify the math by inspection without consulting external comments.
 */
public final class BackupImportLimits {

    private BackupImportLimits() {
        /* utility class — no instances */
    }

    /** Maximum number of inflated bytes for a single ZIP entry (50 MB). */
    public static final long MAX_ENTRY_BYTES = 50L * 1024 * 1024;

    /** Maximum total inflated bytes across all entries in a single backup archive (500 MB). */
    public static final long MAX_TOTAL_BYTES = 500L * 1024 * 1024;

    /** Maximum number of ZIP entries allowed in a single backup archive. */
    public static final int MAX_ENTRIES = 50_000;
}
