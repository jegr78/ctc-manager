package org.ctc.backup.dto;

import java.util.UUID;

/**
 * Return type of {@code BackupImportService.execute(UUID stagingId)}.
 *
 * <p>Returning a record keeps the service-controller seam stateless: the controller binds
 * success-flash placeholders from {@link #restoredTotal()} and {@link #entityCount()}
 * without an extra DB round-trip.
 *
 * <p>The {@code auditUuid} component carries the persisted {@code data_import_audit.id}
 * so that the controller can include it in failure-path flash strings when the import is
 * rolled back mid-execute.
 *
 * <p>Component count is fixed at 3 — any additional metric belongs in the persisted
 * {@code data_import_audit} row, not in this return type.
 *
 * @param auditUuid     id of the {@code data_import_audit} row
 * @param restoredTotal total rows restored across all entities in the manifest
 * @param entityCount   number of distinct entities that contributed rows
 */
public record BackupImportResult(UUID auditUuid, long restoredTotal, int entityCount) {
}
