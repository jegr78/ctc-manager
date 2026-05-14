package org.ctc.backup.dto;

import java.util.UUID;

/**
 * Phase 75 — locked return type of {@code BackupImportService.execute(UUID stagingId)} (D-14,
 * added in Plan 75-06).
 *
 * <p>RESEARCH §"Open Questions" §3 resolution: returning a record (instead of writing the
 * counters into Spring's {@code RedirectAttributes} from inside the service, or re-reading
 * them from the just-committed {@code data_import_audit} row) keeps the service-controller
 * seam stateless and lets the controller bind the D-15 success-flash placeholders
 * {@code {restored}} and {@code {entities}} from {@link #restoredTotal()} and
 * {@link #entityCount()} respectively without an extra DB round-trip.
 *
 * <p>The {@code auditUuid} component carries the persisted
 * {@code data_import_audit.id} so that the controller can include it in the failure-path
 * flash strings (D-15 #2 and #3) when the import is rolled back mid-execute — see the
 * exception-paths in {@code BackupController.executeImport(...)} (Plan 75-07).
 *
 * <p>Lock notes:
 * <ul>
 *   <li>Components are intentionally primitive ({@code long} / {@code int}) — no
 *       {@code Optional<Long>} round-trip — because every successful execute always produces
 *       both counters. The failure path constructs the result on the
 *       {@code @Transactional(propagation=REQUIRES_NEW)} audit-writer side with
 *       {@code restoredTotal=0L} / {@code entityCount=0} and the audit row's UUID.</li>
 *   <li>Component count is fixed at 3 — any additional metric (e.g. wipe counters) belongs in
 *       the persisted {@code data_import_audit} row, not in the return type that the
 *       controller binds. Adding components is a wire-contract change that must bump
 *       {@code BackupSchema.SCHEMA_VERSION}.</li>
 * </ul>
 *
 * @param auditUuid     id of the {@code data_import_audit} row persisted by
 *                      {@code DataImportAuditService.recordResult(...)} (D-01)
 * @param restoredTotal total rows restored across all entities in the manifest (binds to
 *                      the D-15 {@code {restored}} placeholder)
 * @param entityCount   number of distinct entities ({@code data/<entity>.json} files) that
 *                      contributed rows (binds to the D-15 {@code {entities}} placeholder)
 */
public record BackupImportResult(UUID auditUuid, long restoredTotal, int entityCount) {
}
