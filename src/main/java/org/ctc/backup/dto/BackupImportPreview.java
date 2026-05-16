package org.ctc.backup.dto;

import java.util.List;
import java.util.UUID;

/**
 * Read-only view-model for {@code admin/backup-preview.html}.
 *
 * <p>Produced by {@code BackupImportService.stage(MultipartFile)} and passed to Thymeleaf
 * as the primary model attribute. Carries all data required for the preview page without any
 * entity lookups in the template.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code stagingId} — UUID of the on-disk staging file
 *       ({@code data/${profile}/backup-staging/upload-{uuid}.zip}). Rendered into a hidden
 *       input so the confirm page can reference it.</li>
 *   <li>{@code originalFilename} — client-supplied filename from the multipart upload.</li>
 *   <li>{@code fileSizeBytes} — size in bytes of the staged ZIP on disk.</li>
 *   <li>{@code schemaVersion} — {@code schema_version} extracted from {@code manifest.json}
 *       in the backup ZIP.</li>
 *   <li>{@code currentSchemaVersion} — value of {@code BackupSchema.SCHEMA_VERSION} at
 *       preview time (the expected version).</li>
 *   <li>{@code schemaMatches} — stored field (NOT derived); {@code BackupImportService}
 *       computes {@code schemaVersion == currentSchemaVersion} once and passes the result in.
 *       Keeps this record a pure data carrier with clean equals/hashCode semantics.</li>
 *   <li>{@code entityCounts} — one {@link EntityRowCount} per entity in
 *       {@code BackupSchema.getExportOrder()}, in order. Drives the 24-card grid.</li>
 *   <li>{@code uploadFileCount} — number of entries under {@code uploads/} in the ZIP.</li>
 *   <li>{@code totalImportedRows} — sum of {@code importedRows} across all
 *       {@code entityCounts}. Rendered in the header block.</li>
 * </ul>
 */
public record BackupImportPreview(
        UUID stagingId,
        String originalFilename,
        long fileSizeBytes,
        int schemaVersion,
        int currentSchemaVersion,
        boolean schemaMatches,
        List<EntityRowCount> entityCounts,
        int uploadFileCount,
        long totalImportedRows
) {
}
