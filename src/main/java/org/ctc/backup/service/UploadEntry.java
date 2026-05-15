package org.ctc.backup.service;

import java.nio.file.Path;

/**
 * Value object describing a single referenced upload file.
 *
 * <p>Used by {@link BackupExportService#enumerateReferencedUploads()} and consumed by
 * {@code BackupArchiveService} to write the {@code uploads/<relativePath>} entries into
 * the backup ZIP.
 *
 * @param absolutePath absolute, normalized filesystem location of the file
 * @param relativePath path relative to the configured {@code app.upload-dir} root, e.g.
 *                     {@code "teams/<uuid>/logo.png"}. Always stored without the
 *                     {@code /uploads/} prefix.
 */
public record UploadEntry(Path absolutePath, String relativePath) {
}
