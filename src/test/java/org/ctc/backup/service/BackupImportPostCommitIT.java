package org.ctc.backup.service;

import org.ctc.backup.audit.DataImportAudit;
import org.ctc.backup.audit.DataImportAuditRepository;
import org.ctc.backup.audit.DataImportAuditService;
import org.ctc.backup.event.BackupImportSucceededEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

/**
 * Phase 75 / Plan 07 — Failsafe IT for {@link BackupImportPostCommitListener}.
 *
 * <p>Proves the D-09 atomic-move-triple semantics on H2 + the local filesystem. Each scenario
 * sets up a {@link TempDir} layout, publishes a {@link BackupImportSucceededEvent} from inside
 * a {@link TransactionTemplate}-managed transaction (so the listener fires AFTER_COMMIT), and
 * asserts on:
 *
 * <ul>
 *   <li>Step 1 + Step 2 file-system state ({@code uploadsTarget} contents, presence of
 *       {@code importBackupDir/uploads-old/}).</li>
 *   <li>The persisted {@code data_import_audit} row's {@code success} flag.</li>
 *   <li>Captured ERROR-level log output (Plan 07 success criterion: operator-recovery flash
 *       depends on loud error logs).</li>
 * </ul>
 *
 * <p>The {@code @TransactionalEventListener(phase=AFTER_COMMIT)} machinery delivers the event
 * synchronously to the listener after the outer transaction commits — exceptions thrown by the
 * listener propagate to the publisher of the event, which is exactly the surface the Plan 06
 * orchestrator (and this test's {@link TransactionTemplate#executeWithoutResult(...)} call)
 * observes.
 *
 * <p>{@link OutputCaptureExtension} (Spring Boot Test) captures stdout to assert on the ERROR
 * log lines. Mirrors the {@code BackupStagingCleanupIT} pattern already established in this
 * repository.
 */
@SpringBootTest
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
class BackupImportPostCommitIT {

    @TempDir
    Path tempRoot;

    @Autowired
    BackupImportPostCommitListener listener;

    @Autowired
    DataImportAuditRepository auditRepository;

    @MockitoSpyBean
    DataImportAuditService dataImportAuditService;

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Autowired
    TransactionTemplate transactionTemplate;

    /** Live {@code uploads/} target — moved away by Step 1, replaced by Step 2. */
    Path uploadsTarget;

    /** Per-import backup directory {@code data/.import-backups/<ts>/}. */
    Path importBackupDir;

    /** Staged uploads tree {@code importBackupDir/uploads-new/} — promoted to live by Step 2. */
    Path uploadsNewDir;

    @BeforeEach
    void setUpLayout() throws IOException {
        // Fresh per-test layout — TempDir guarantees uniqueness even across forks
        uploadsTarget = tempRoot.resolve("uploads");
        importBackupDir = tempRoot.resolve(".import-backups").resolve("2026-05-14T10-00-00Z");
        uploadsNewDir = importBackupDir.resolve("uploads-new");
        Files.createDirectories(importBackupDir);
    }

    /**
     * Builds the success event with the per-test paths. The UUIDs/strings are arbitrary; the
     * listener uses them verbatim for the audit-row write.
     */
    private BackupImportSucceededEvent buildEvent(UUID auditUuid) {
        return new BackupImportSucceededEvent(
                /* stagingId */ UUID.randomUUID(),
                /* auditUuid */ auditUuid,
                /* importBackupDir */ importBackupDir,
                /* uploadsTarget */ uploadsTarget,
                /* uploadsNewDir */ uploadsNewDir,
                /* schemaVersion */ 1,
                /* tableCountsWiped */ Map.of("teams", 3L),
                /* tableCountsRestored */ Map.of("teams", 3L),
                /* sourceFilename */ "phase73-export.zip",
                /* executedBy */ "dev");
    }

    /**
     * Publishes the event inside a managed transaction so that Spring's
     * {@code @TransactionalEventListener(phase=AFTER_COMMIT)} fires after the
     * {@code executeWithoutResult} block commits.
     */
    private void publishWithCommit(BackupImportSucceededEvent event) {
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));
    }

    @Test
    void givenAllStepsSucceed_whenEventPublishedInsideTx_thenAfterCommitMoveTripleCompletesAndAuditRowSuccessIsTrue()
            throws IOException {
        // given — populate uploadsTarget with one existing file + uploadsNewDir with two new files
        Files.createDirectories(uploadsTarget);
        Files.writeString(uploadsTarget.resolve("old-file.png"), "old-content");
        Files.createDirectories(uploadsNewDir);
        Files.writeString(uploadsNewDir.resolve("new-file-1.png"), "new-content-1");
        Files.writeString(uploadsNewDir.resolve("new-file-2.png"), "new-content-2");

        UUID auditUuid = UUID.randomUUID();
        BackupImportSucceededEvent event = buildEvent(auditUuid);

        // when
        publishWithCommit(event);

        // then — Step 2 placed new-file-1.png + new-file-2.png at uploadsTarget
        assertThat(uploadsTarget.resolve("new-file-1.png"))
                .as("Step 2: uploadsTarget must contain the promoted new-file-1.png")
                .exists();
        assertThat(uploadsTarget.resolve("new-file-2.png"))
                .as("Step 2: uploadsTarget must contain the promoted new-file-2.png")
                .exists();
        assertThat(uploadsTarget.resolve("old-file.png"))
                .as("Step 1: old-file.png must have moved away from uploadsTarget")
                .doesNotExist();

        // Step 1 result: importBackupDir/uploads-old/ contains the original uploadsTarget contents
        Path uploadsOldDir = importBackupDir.resolve("uploads-old");
        assertThat(uploadsOldDir)
                .as("Step 1: importBackupDir/uploads-old/ must exist")
                .exists();
        assertThat(uploadsOldDir.resolve("old-file.png"))
                .as("Step 1: uploads-old/ must contain the pre-import old-file.png")
                .exists();

        // Step 3: success=true audit row was written via REQUIRES_NEW
        Optional<DataImportAudit> auditRow = auditRepository.findById(auditUuid);
        assertThat(auditRow)
                .as("Step 3: success audit row must be persisted")
                .isPresent();
        assertThat(auditRow.get().isSuccess())
                .as("Step 3: audit row success flag must be true")
                .isTrue();
    }

    @Test
    void givenStep2FailsBecauseUploadsNewMissing_whenEventPublished_thenStep1RevertedAndAuditSuccessFalse(
            CapturedOutput output) throws IOException {
        // given — uploadsTarget exists with content, but uploadsNewDir does NOT exist:
        // Step 1 moves uploadsTarget → uploads-old; Step 2 then fails because uploadsNewDir
        // is missing, and the listener must revert Step 1.
        Files.createDirectories(uploadsTarget);
        Files.writeString(uploadsTarget.resolve("original.png"), "original-content");
        // intentionally do NOT create uploadsNewDir

        UUID auditUuid = UUID.randomUUID();
        BackupImportSucceededEvent event = buildEvent(auditUuid);

        // when — listener throws internally; Spring's TransactionalApplicationListenerMethodAdapter
        // logs the exception rather than re-throwing through publishEvent (default behaviour for
        // AFTER_COMMIT listeners). The contract is therefore proven via state assertions on
        // the file-system + audit row + the captured ERROR log line, not via assertThatThrownBy.
        publishWithCommit(event);

        // then — Step-1 revert worked: uploadsTarget still contains the original content
        assertThat(uploadsTarget)
                .as("Step-1 revert: uploadsTarget directory must be restored")
                .exists();
        assertThat(uploadsTarget.resolve("original.png"))
                .as("Step-1 revert: uploadsTarget must still contain its original content")
                .exists();
        assertThat(Files.readString(uploadsTarget.resolve("original.png")))
                .as("Step-1 revert: file content must be byte-identical to pre-import")
                .isEqualTo("original-content");

        // uploads-old/ must NOT exist anymore — Step-1 revert moved it back
        Path uploadsOldDir = importBackupDir.resolve("uploads-old");
        assertThat(uploadsOldDir)
                .as("Step-1 revert: uploads-old/ must be gone after revert")
                .doesNotExist();

        // Audit row: success=false, written via REQUIRES_NEW from the catch-block of Step 2
        Optional<DataImportAudit> auditRow = auditRepository.findById(auditUuid);
        assertThat(auditRow)
                .as("Failure-path audit row must be persisted via REQUIRES_NEW")
                .isPresent();
        assertThat(auditRow.get().isSuccess())
                .as("Failure-path audit row success flag must be false")
                .isFalse();

        // ERROR log lines were emitted — Plan 08 flash-message recovery path depends on this.
        // Loud-fail contract: Step 2 failure + UploadsRestoreException both surface to stdout.
        assertThat(output.getAll())
                .as("ERROR log line must be emitted on Step-2 failure")
                .contains("AFTER_COMMIT Step 2 failed");
        assertThat(output.getAll())
                .as("UploadsRestoreException must be visible in the log stream")
                .contains("UploadsRestoreException");
    }

    @Test
    void givenStep3AuditWriteFails_whenEventPublished_thenFilesStillInTargetStateAndExceptionNotRethrown(
            CapturedOutput output) throws IOException {
        // given — happy-path FS layout but the REQUIRES_NEW recordResult throws on success=true.
        // The listener MUST swallow that exception because Steps 1+2 already moved the files;
        // the operator's signal is the missing audit row + the ERROR log line.
        Files.createDirectories(uploadsTarget);
        Files.writeString(uploadsTarget.resolve("old-file.png"), "old-content");
        Files.createDirectories(uploadsNewDir);
        Files.writeString(uploadsNewDir.resolve("new-file.png"), "new-content");

        UUID auditUuid = UUID.randomUUID();
        BackupImportSucceededEvent event = buildEvent(auditUuid);

        // Make the success=true audit-row write throw — both helpers (the inline Step-3 call
        // and the recordResultBestEffort helper) go through the same recordResult method.
        doThrow(new IllegalStateException("simulated audit-write failure"))
                .when(dataImportAuditService)
                .recordResult(any(UUID.class), anyString(), anyInt(), anyMap(), anyMap(),
                        anyString(), anyBoolean());

        // when — listener must NOT rethrow (files already in target state)
        publishWithCommit(event);

        // then — Step 1 + Step 2 succeeded, so the FS is in the post-import target state
        assertThat(uploadsTarget.resolve("new-file.png"))
                .as("Step 2 must have promoted new-file.png to uploadsTarget")
                .exists();
        assertThat(uploadsTarget.resolve("old-file.png"))
                .as("Step 1 must have moved old-file.png away from uploadsTarget")
                .doesNotExist();
        assertThat(importBackupDir.resolve("uploads-old").resolve("old-file.png"))
                .as("Step 1 must have placed old-file.png into uploads-old/")
                .exists();

        // No success audit row persisted (the spy threw)
        assertThat(auditRepository.findById(auditUuid))
                .as("Audit row must be absent when Step 3 fails — operator-recovery signal")
                .isEmpty();

        // ERROR log line emitted (loud-fail contract)
        assertThat(output.getAll())
                .as("ERROR log line must be emitted on Step-3 audit-write failure")
                .contains("AFTER_COMMIT Step 3 (audit success row) failed");
    }

    @org.junit.jupiter.api.AfterEach
    void cleanupTempLayout() throws IOException {
        // Recursive best-effort cleanup so failed scenarios don't leak into the next test.
        // tempRoot is auto-deleted by JUnit at class end, but explicit cleanup here keeps the
        // intermediate test boundary clean.
        if (Files.exists(tempRoot)) {
            try (Stream<Path> walk = Files.walk(tempRoot)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException io) {
                        // best-effort
                    }
                });
            } catch (IOException io) {
                // best-effort
            }
        }
    }
}
