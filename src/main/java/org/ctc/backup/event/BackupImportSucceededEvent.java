package org.ctc.backup.event;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 75 — application event carrying the success-path payload from
 * {@code BackupImportService.execute(...)} (D-14, Plan 75-06) to the
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} that runs the post-commit
 * {@code uploads/}-tree move triple (D-09) and the staging-file cleanup (Plan 75-07).
 *
 * <p>Plan reference: scaffolding lives in Plan 75-01; the publisher (the
 * {@code @Transactional} {@code execute(...)} method) ships in Plan 75-06; the listener that
 * consumes this event (and runs Step-1 / Step-2 / D-01 REQUIRES_NEW audit-row write) ships in
 * Plan 75-07.
 *
 * <p>RESEARCH §2 / PATTERNS Pattern 2: a single record carrier keeps the listener signature
 * clean and lets the planner pick between
 * {@code @TransactionalEventListener(phase=AFTER_COMMIT)} and a manual
 * {@code TransactionSynchronizationManager.registerSynchronization(...)} callback without
 * having to renegotiate the carrier shape.
 *
 * <p>Lock notes:
 * <ul>
 *   <li>All 10 components are required by either Step-1, Step-2, the D-01 REQUIRES_NEW audit
 *       row, or the success-flash binding — none are optional. The
 *       {@code Map<String, Long>} table-count maps are passed by reference; the listener
 *       MUST treat them as immutable views.</li>
 *   <li>{@code importBackupDir} is the timestamped per-import directory
 *       {@code data/.import-backups/<ts>/} (D-11). {@code uploadsTarget} is the live
 *       {@code data/<profile>/uploads/}. {@code uploadsNewDir} is the staged extraction at
 *       {@code data/.import-backups/<ts>/uploads-new/} (D-12). The listener moves
 *       {@code uploadsTarget} → {@code importBackupDir/uploads-old} (Step-1) and
 *       {@code uploadsNewDir} → {@code uploadsTarget} (Step-2).</li>
 *   <li>{@code stagingId} is the original {@code BackupImportService.stage(...)} UUID (Phase
 *       74 D-15) so the listener can call {@code BackupImportService.deleteStagingFile(...)}
 *       after the move triple succeeds (Plan 75-07).</li>
 *   <li>{@code schemaVersion}, {@code tableCountsWiped}, {@code tableCountsRestored},
 *       {@code sourceFilename}, {@code executedBy} feed the D-01 REQUIRES_NEW audit-row
 *       write inside the listener.</li>
 * </ul>
 *
 * @param stagingId            UUID returned by {@code BackupImportService.stage(...)} —
 *                             used for staging-file cleanup
 * @param auditUuid            id of the failure-time audit row OR a fresh UUID for the
 *                             success-time audit row (D-15 success-flash carrier)
 * @param importBackupDir      timestamped directory {@code data/.import-backups/<ts>/}
 *                             (D-11)
 * @param uploadsTarget        live uploads directory {@code data/<profile>/uploads/}
 * @param uploadsNewDir        staged uploads at
 *                             {@code data/.import-backups/<ts>/uploads-new/} (D-12)
 * @param schemaVersion        manifest {@code schemaVersion} value, copied into the audit
 *                             row
 * @param tableCountsWiped     per-table row counts deleted in the wipe step (empty map on
 *                             failure-before-wipe)
 * @param tableCountsRestored  per-table row counts restored (empty map on
 *                             failure-before-restore)
 * @param sourceFilename       original filename from
 *                             {@code MultipartFile.getOriginalFilename()} (Phase 74)
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
