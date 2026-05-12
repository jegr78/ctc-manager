package org.ctc.backup.service;

/**
 * Constants holder for ZIP-bomb defense thresholds.
 *
 * <p>Source of truth: CONTEXT §D-12 and SECU-02 acceptance criteria.
 *
 * <p>Consumers:
 * <ul>
 *   <li>{@code LimitedInputStream} — reads {@link #MAX_ENTRY_BYTES} as the per-entry inflated
 *       byte cap passed to its constructor by {@code BackupArchiveService.read*} (Plan 04).</li>
 *   <li>{@code BackupArchiveService.read*} (Plan 04) — enforces {@link #MAX_TOTAL_BYTES} as the
 *       running total of all inflated entry bytes across the archive, and {@link #MAX_ENTRIES}
 *       as the cap on the number of ZIP entries.</li>
 * </ul>
 *
 * <p>All three values are written as literal multiplication expressions so a code-review can
 * verify the math by inspection without consulting external comments.
 */
public final class BackupImportLimits {

    private BackupImportLimits() {
        /* utility class — no instances */
    }

    /** Maximum number of inflated bytes for a single ZIP entry (50 MB). SECU-02 / D-12. */
    public static final long MAX_ENTRY_BYTES = 50L * 1024 * 1024;

    /** Maximum total inflated bytes across all entries in a single backup archive (500 MB). SECU-02 / D-12. */
    public static final long MAX_TOTAL_BYTES = 500L * 1024 * 1024;

    /** Maximum number of ZIP entries allowed in a single backup archive. SECU-02 / D-12. */
    public static final int MAX_ENTRIES = 50_000;
}
