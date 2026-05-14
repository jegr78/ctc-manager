package org.ctc.backup.service;

import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.audit.DataImportAuditService;
import org.ctc.backup.event.BackupImportSucceededEvent;
import org.ctc.backup.exception.UploadsRestoreException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Phase 75 / Plan 07 — AFTER_COMMIT listener that owns the post-commit file-system mutations
 * (D-09 atomic-move-triple) and the success-path audit-row write.
 *
 * <p>File-system mutations cannot be enrolled in the JPA transaction; AFTER_COMMIT is Spring's
 * canonical post-commit hook (RESEARCH §2 / PATTERNS Pattern 2). The listener consumes
 * {@link BackupImportSucceededEvent} published by
 * {@link BackupImportService#execute(java.util.UUID)} (Plan 06) and performs Steps 1-4 of D-09:
 *
 * <ol>
 *   <li><strong>Step 1:</strong> {@code Files.move(uploadsTarget, importBackupDir/uploads-old,
 *       ATOMIC_MOVE)} — moves the live uploads tree out of the way. Failure throws
 *       {@link UploadsRestoreException} and writes a {@code success=false} audit row.</li>
 *   <li><strong>Step 2:</strong> {@code Files.move(importBackupDir/uploads-new, uploadsTarget,
 *       ATOMIC_MOVE)} — promotes the staged uploads to the live target. Failure attempts a
 *       Step-1 revert (move uploads-old back to uploadsTarget), writes a {@code success=false}
 *       audit row, and throws {@link UploadsRestoreException}.</li>
 *   <li><strong>Step 3:</strong> {@link DataImportAuditService#recordResult(java.util.UUID,
 *       String, int, java.util.Map, java.util.Map, String, boolean)} with {@code success=true}.
 *       Failure here logs ERROR but does NOT revert Steps 1+2 — the files are already in their
 *       target state; the operator's signal is the missing audit row.</li>
 *   <li><strong>Step 4:</strong> {@link BackupImportService#deleteStagingFile(java.util.UUID)} —
 *       best-effort cleanup of the staged ZIP plus its {@code .meta} sidecar (Phase 74 D-19).</li>
 * </ol>
 *
 * <p><strong>No transaction annotation on the listener method itself.</strong>
 * REQUIRES_NEW propagation is auto-enforced by {@code DataImportAuditService.recordResult(...)}
 * (Plan 02) via its own {@code Propagation.REQUIRES_NEW} setting — RESEARCH §9 documents the
 * Spring 6.1+ rule that {@code @TransactionalEventListener} methods may not run in the outer
 * (already-committed) transaction. The audit-service-side propagation is the exact contract
 * this listener relies on.
 *
 * <p><strong>PATTERNS Pitfall §2 — AFTER_COMMIT swallows exceptions silently.</strong> The
 * recovery path is best-effort revert + loud ERROR log + {@code success=false} audit row, NOT
 * exception propagation: a thrown exception inside AFTER_COMMIT will not unwind the
 * already-committed JPA transaction. The {@link UploadsRestoreException} re-throws from Steps 1
 * and 2 surface the failure to any caller that observes the listener's invocation context (none
 * in production, but the Plan 07 IT pins this behaviour via Spring's
 * {@code ApplicationEventMulticaster} which DOES propagate listener exceptions to the
 * synchronous publisher in test contexts).
 */
@Slf4j
@Component
public class BackupImportPostCommitListener {

    private final DataImportAuditService dataImportAuditService;
    private final BackupImportService backupImportService;

    public BackupImportPostCommitListener(
            DataImportAuditService dataImportAuditService,
            BackupImportService backupImportService) {
        this.dataImportAuditService = dataImportAuditService;
        this.backupImportService = backupImportService;
    }

    /**
     * Handles {@link BackupImportSucceededEvent} after the outer JPA transaction commits.
     *
     * <p>By the time this method runs, the DB-side wipe + restore is already durable. The four
     * post-commit steps are documented at the class level.
     *
     * @param event the success-event published by {@code BackupImportService.execute(...)}
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onImportSucceeded(BackupImportSucceededEvent event) {
        Path importBackupDir = event.importBackupDir();
        Path uploadsTarget = event.uploadsTarget();
        Path uploadsNewDir = event.uploadsNewDir();

        // dbCommitted is true by definition — AFTER_COMMIT only fires post-commit. We track
        // uploadsRestoredFully so the final log line tells the operator exactly where in the
        // sequence the listener got to.
        boolean dbCommitted = true;
        boolean uploadsRestoredFully = false;

        // Step 1: move existing uploads/ → importBackupDir/uploads-old/
        Path uploadsOld = importBackupDir.resolve("uploads-old");
        try {
            if (Files.exists(uploadsTarget)) {
                Files.move(uploadsTarget, uploadsOld, StandardCopyOption.ATOMIC_MOVE);
                log.info("AFTER_COMMIT Step 1 complete: moved {} -> {}", uploadsTarget, uploadsOld);
            } else {
                log.info("AFTER_COMMIT Step 1 skipped: uploadsTarget does not exist ({})", uploadsTarget);
            }
        } catch (IOException e) {
            log.error("AFTER_COMMIT Step 1 failed: uploads tree unchanged", e);
            recordResultBestEffort(event, /* success */ false);
            throw new UploadsRestoreException("Step 1 failed: move uploads -> uploads-old", e);
        }

        // Step 2: move importBackupDir/uploads-new/ → uploadsTarget/ (with revert-on-failure)
        try {
            Files.move(uploadsNewDir, uploadsTarget, StandardCopyOption.ATOMIC_MOVE);
            uploadsRestoredFully = true;
            log.info("AFTER_COMMIT Step 2 complete: moved {} -> {}", uploadsNewDir, uploadsTarget);
        } catch (IOException e) {
            log.error("AFTER_COMMIT Step 2 failed: attempting Step-1 revert", e);
            try {
                if (Files.exists(uploadsOld)) {
                    // CR-02 defensive sweep: if Step 2 partially materialized uploadsTarget
                    // (e.g. non-atomic-move fallback on some filesystems, or a third-party
                    // process dropped a metadata inode under uploadsTarget between Step 1
                    // and the revert attempt), ATOMIC_MOVE of uploads-old → uploadsTarget
                    // would fail with FileAlreadyExistsException, stranding the operator
                    // with NO uploads tree. Sweep any leftover aside first so the revert
                    // can complete.
                    if (Files.exists(uploadsTarget)) {
                        Path orphan = importBackupDir.resolve("uploads-step2-orphan");
                        Files.move(uploadsTarget, orphan, StandardCopyOption.ATOMIC_MOVE);
                        log.warn("Step-2 left orphan at {} - moved to {} before Step-1 revert",
                                uploadsTarget, orphan);
                    }
                    Files.move(uploadsOld, uploadsTarget, StandardCopyOption.ATOMIC_MOVE);
                    log.warn("Step-1 revert succeeded; uploads tree restored to pre-import state");
                } else {
                    log.warn("Step-1 revert skipped: {} does not exist (Step 1 was a no-op)", uploadsOld);
                }
            } catch (IOException revertEx) {
                log.error("Step-1 revert ALSO failed - manual recovery required from {}",
                        importBackupDir, revertEx);
            }
            recordResultBestEffort(event, /* success */ false);
            throw new UploadsRestoreException("Step 2 failed: move uploads-new -> uploads", e);
        }

        // Step 3: success audit row (REQUIRES_NEW via DataImportAuditService — D-01 success-flash semantics).
        // Do NOT rethrow on failure: files are already moved into place, the operator's signal is
        // the missing audit row (loud ERROR log).
        try {
            dataImportAuditService.recordResult(
                    event.auditUuid(),
                    event.executedBy(),
                    event.schemaVersion(),
                    event.tableCountsWiped(),
                    event.tableCountsRestored(),
                    event.sourceFilename(),
                    /* success */ true);
        } catch (Exception e) {
            log.error("AFTER_COMMIT Step 3 (audit success row) failed; DB + FS already in target state", e);
        }

        // Step 4: best-effort cleanup of the staged ZIP + .meta sidecar (Phase 74 D-19).
        // BackupImportService.deleteStagingFile is documented as idempotent + swallows IOException,
        // but we still wrap in try/catch defensively in case a future refactor changes that.
        try {
            backupImportService.deleteStagingFile(event.stagingId());
        } catch (Exception e) {
            log.warn("AFTER_COMMIT Step 4 (staging cleanup) failed", e);
        }

        log.info("AFTER_COMMIT import flow complete: auditUuid={}, dbCommitted={}, uploadsRestoredFully={}",
                event.auditUuid(), dbCommitted, uploadsRestoredFully);
    }

    /**
     * Best-effort wrapper around {@link DataImportAuditService#recordResult(...)} used by the
     * Step-1 and Step-2 failure paths. A failure here logs ERROR but does NOT throw — the
     * caller is in the middle of throwing {@link UploadsRestoreException}, and an audit-write
     * failure must not mask that.
     */
    private void recordResultBestEffort(BackupImportSucceededEvent event, boolean success) {
        try {
            dataImportAuditService.recordResult(
                    event.auditUuid(),
                    event.executedBy(),
                    event.schemaVersion(),
                    event.tableCountsWiped(),
                    event.tableCountsRestored(),
                    event.sourceFilename(),
                    success);
        } catch (Exception e) {
            log.error("recordResultBestEffort failed (auditUuid={}, success={})",
                    event.auditUuid(), success, e);
        }
    }
}
