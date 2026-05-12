package org.ctc.backup.dto;

/**
 * One card in the 24-card preview grid on {@code admin/backup-preview.html}.
 *
 * <p>Phase 74 — CONTEXT D-21. Each card shows a single entity's row counts from the
 * staged backup ZIP compared to the current database state, allowing the admin to
 * evaluate data-loss risk before confirming an import.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code tableName} — snake_case table name, e.g. {@code season_phases}. Matches the
 *       key in {@code BackupManifest.tableCounts()} and the file name under {@code data/}
 *       in the backup ZIP.</li>
 *   <li>{@code humanLabel} — human-readable label, e.g. {@code Season Phases}. Precomputed
 *       by {@code BackupImportService.toHumanLabel(tableName)} (Plan 05) — this record
 *       carries no derivation logic (per CLAUDE.md "Keep Thymeleaf Templates Lean" +
 *       "No Fallback Calculations").</li>
 *   <li>{@code currentRows} — current row count in the live database at preview time.</li>
 *   <li>{@code importedRows} — row count from the backup ZIP for this entity.</li>
 * </ul>
 */
public record EntityRowCount(
        String tableName,
        String humanLabel,
        long currentRows,
        long importedRows
) {
}
