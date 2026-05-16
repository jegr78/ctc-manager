package org.ctc.backup.event;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Application event carrying the success-path payload from
 * {@code BackupImportService.execute(...)} to the
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} that runs the post-commit
 * {@code uploads/}-tree move and staging-file cleanup.
 *
 * <p>All 10 components are required: either for the uploads-move triple, the REQUIRES_NEW
 * audit-row write, or the success-flash binding — none are optional. The
 * {@code Map<String, Long>} table-count maps are passed by reference; the listener
 * MUST treat them as immutable views.
 *
 * @param stagingId            UUID returned by {@code BackupImportService.stage(...)} —
 *                             used for staging-file cleanup
 * @param auditUuid            id of the failure-time audit row OR a fresh UUID for the
 *                             success-time audit row
 * @param importBackupDir      timestamped directory {@code data/.import-backups/<ts>/}
 * @param uploadsTarget        live uploads directory {@code data/<profile>/uploads/}
 * @param uploadsNewDir        staged uploads at {@code data/.import-backups/<ts>/uploads-new/}
 * @param schemaVersion        manifest {@code schemaVersion} value, copied into the audit row
 * @param tableCountsWiped     per-table row counts deleted in the wipe step (empty map on
 *                             failure-before-wipe)
 * @param tableCountsRestored  per-table row counts restored (empty map on failure-before-restore)
 * @param sourceFilename       original filename from {@code MultipartFile.getOriginalFilename()}
 * @param executedBy           username from {@code SecurityContextHolder} on prod/docker,
 *                             literal {@code "dev"} on dev/local
 */
public record BackupImportSucceededEvent(
        UUID stagingId,
        UUID auditUuid,
        Path importBackupDir,
        Path uploadsTarget,
        Path uploadsNewDir,
        int schemaVersion,
        Map<String, Long> tableCountsWiped,
        Map<String, Long> tableCountsRestored,
        String sourceFilename,
        String executedBy) {
}
