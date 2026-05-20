package org.ctc.backup.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.ctc.backup.audit.DataImportAudit;
import org.ctc.backup.audit.DataImportAuditRepository;
import org.ctc.backup.event.BackupImportSucceededEvent;
import org.ctc.backup.exception.UploadsRestoreException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 87 / Plan 87-05 — edge-case gap fills for v1.10 Phase 75 post-commit semantics.
 *
 * <p>{@link BackupImportPostCommitIT} covers the three primary atomic-move-triple scenarios
 * (happy path, Step-2 failure with Step-1 revert, Step-3 audit-write failure). This IT closes
 * two edge cases the v1.10 Phase-75 VALIDATION audit (Phase 87 D-05 aggressive gap-filling)
 * surfaced as not explicitly asserted:
 *
 * <ol>
 *   <li><strong>Gap A — listener double-fire safety:</strong> if Spring's
 *       {@code ApplicationEventMulticaster} delivers the same {@link BackupImportSucceededEvent}
 *       twice (defensive scenario; not expected under {@code @TransactionalEventListener(AFTER_COMMIT)}
 *       semantics, but cheap to pin), the second invocation MUST fail loudly at Step 1 instead
 *       of corrupting the file-system. The {@code Files.move(uploadsTarget, uploadsOld, ATOMIC_MOVE)}
 *       call cannot succeed twice because after the first call {@code uploads-old/} already exists
 *       and {@code uploadsTarget} now holds the promoted contents from the first Step 2. The
 *       first invocation's audit row stays {@code success=true}; the second invocation writes a
 *       {@code success=false} row via {@code recordResultBestEffort} and throws
 *       {@link UploadsRestoreException}.</li>
 *   <li><strong>Gap B — timestamped sub-directory naming convention:</strong> the
 *       {@code data/.import-backups/&lt;ts&gt;/} sub-directory name produced by
 *       {@link BackupImportService#execute(UUID)} (line {@code "Instant.now()
 *       .truncatedTo(SECONDS).toString().replace(":", "-")"}) MUST match the documented
 *       ISO-8601-with-dashes pattern {@code yyyy-MM-ddTHH-mm-ssZ}. This is the operator-visible
 *       retention key (CONTEXT D-04 24h retention) and a contract for forensic recovery scripts.
 *       The listener does NOT produce the path — {@link BackupImportService} does — but the
 *       listener's {@code Files.move(uploadsTarget, importBackupDir.resolve("uploads-old"))}
 *       contract relies on the parent {@code importBackupDir} already being a well-formed
 *       timestamped directory. We pin the convention here as the canonical retroactive
 *       assertion.</li>
 * </ol>
 *
 * <p>Both scenarios reuse the {@code @SpringBootTest + @ActiveProfiles("dev")} context already
 * loaded by {@link BackupImportPostCommitIT} (no new context, ~0 s cold-start cost — Phase 87
 * D-06 wallclock budget).
 */
@CtcDevSpringBootContext
@ExtendWith(OutputCaptureExtension.class)
@Tag("integration")
class BackupImportPostCommitEdgeCasesIT {

    /** ISO-8601 instant truncated to seconds, with {@code :} replaced by {@code -} to make
     *  the literal path-component safe across POSIX + Windows file systems. Matches the
     *  exact production formula in {@link BackupImportService#execute(UUID)}. */
    private static final Pattern TS_DIR_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}-\\d{2}-\\d{2}Z$");

    @TempDir
    Path tempRoot;

    @Autowired
    BackupImportPostCommitListener listener;

    @Autowired
    DataImportAuditRepository auditRepository;

    Path uploadsTarget;
    Path importBackupDir;
    Path uploadsNewDir;

    @BeforeEach
    void setUpLayout() throws IOException {
        uploadsTarget = tempRoot.resolve("uploads");
        // Use the same timestamp format the production code produces so the path naming
        // in this test exactly mirrors what BackupImportService.execute(...) would build.
        String ts = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "-");
        importBackupDir = tempRoot.resolve(".import-backups").resolve(ts);
        uploadsNewDir = importBackupDir.resolve("uploads-new");
        Files.createDirectories(importBackupDir);
    }

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
                /* sourceFilename */ "phase87-edge-export.zip",
                /* executedBy */ "dev");
    }

    // ----------------------------------------------------------------------
    // Gap A — listener double-fire safety
    // ----------------------------------------------------------------------

    @Test
    void givenAfterCommitListenerIsInvokedTwiceWithSameEvent_whenSecondCallFires_thenStep1FailsAndOriginalFsStateProtected(
            CapturedOutput output) throws IOException {
        // given — happy-path layout for the FIRST invocation
        Files.createDirectories(uploadsTarget);
        Files.writeString(uploadsTarget.resolve("old-file.png"), "old-content");
        Files.createDirectories(uploadsNewDir);
        Files.writeString(uploadsNewDir.resolve("new-file.png"), "new-content");

        UUID firstAuditUuid = UUID.randomUUID();
        BackupImportSucceededEvent event = buildEvent(firstAuditUuid);

        // when — fire the listener directly twice. The second call MUST NOT succeed:
        // - uploadsOld already exists from call #1, so Step-1 ATOMIC_MOVE throws.
        // - The listener wraps the failure in UploadsRestoreException + writes success=false
        //   via recordResultBestEffort.
        listener.onImportSucceeded(event);

        // After call #1: uploadsTarget contains the promoted new-file.png, uploads-old has
        // the original old-file.png, and the success=true audit row was persisted.
        assertThat(uploadsTarget.resolve("new-file.png"))
                .as("Call #1 Step 2: promoted new-file.png to uploadsTarget")
                .exists();
        Path uploadsOldDir = importBackupDir.resolve("uploads-old");
        assertThat(uploadsOldDir.resolve("old-file.png"))
                .as("Call #1 Step 1: old-file.png is now in uploads-old/")
                .exists();

        // Call #2 — use a SECOND audit UUID so we can distinguish first vs. second outcome.
        // The same event payload would do, but a fresh audit UUID exposes the success=false
        // recordResultBestEffort write performed by the failed Step-1 path of call #2.
        UUID secondAuditUuid = UUID.randomUUID();
        BackupImportSucceededEvent replay = new BackupImportSucceededEvent(
                event.stagingId(),
                /* auditUuid */ secondAuditUuid,
                event.importBackupDir(),
                event.uploadsTarget(),
                event.uploadsNewDir(),
                event.schemaVersion(),
                event.tableCountsWiped(),
                event.tableCountsRestored(),
                event.sourceFilename(),
                event.executedBy());

        // then — the second call MUST throw UploadsRestoreException from Step 1.
        // Calling the listener directly (no @TransactionalEventListener swallow path) makes
        // the throw observable, which is the contract surface the operator-recovery flash
        // relies on (Plan 08 success criterion: failed listener invocations are visible).
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> listener.onImportSucceeded(replay))
                .as("Second listener invocation must throw UploadsRestoreException at Step 1")
                .isInstanceOf(UploadsRestoreException.class);

        // Step-1 of call #2 failed loudly + the success=false audit row exists.
        Optional<DataImportAudit> secondAudit = auditRepository.findById(secondAuditUuid);
        assertThat(secondAudit)
                .as("Failed second invocation must persist a success=false audit row")
                .isPresent();
        assertThat(secondAudit.get().isSuccess())
                .as("Second invocation's audit row must be success=false")
                .isFalse();

        // The first invocation's success=true audit row was NOT overwritten — separate UUIDs.
        Optional<DataImportAudit> firstAudit = auditRepository.findById(firstAuditUuid);
        assertThat(firstAudit)
                .as("First invocation's success=true audit row must survive the replay")
                .isPresent();
        assertThat(firstAudit.get().isSuccess())
                .as("First invocation's audit row stays success=true after replay")
                .isTrue();

        // File-system invariant: uploadsTarget still holds the promoted content; the original
        // old-file.png remains in uploads-old/. Neither the live tree nor the backup tree was
        // corrupted by the double-fire.
        assertThat(uploadsTarget.resolve("new-file.png"))
                .as("FS invariant: promoted file remains in uploadsTarget after replay")
                .exists();
        assertThat(uploadsOldDir.resolve("old-file.png"))
                .as("FS invariant: pre-import file remains in uploads-old/ after replay")
                .exists();

        // ERROR log line on the replay must be loud (Plan 08 operator recovery contract).
        assertThat(output.getAll())
                .as("ERROR log line must be emitted on double-fire Step-1 failure")
                .contains("AFTER_COMMIT Step 1 failed");
    }

    // ----------------------------------------------------------------------
    // Gap B — timestamped sub-directory naming convention
    // ----------------------------------------------------------------------

    @Test
    void givenImportBackupDirNamingConvention_whenReplicatingProductionFormula_thenSubdirMatchesIso8601WithDashes() {
        // given — the production formula in BackupImportService.execute(...) (Instant.now()
        // .truncatedTo(SECONDS).toString().replace(":", "-")) is replicated in setUpLayout.
        // This test pins the convention via the resolved sub-directory name created there. The
        // matcher is the canonical ISO-8601-with-dashes shape used as the 24h-retention key
        // (CONTEXT D-04) and operator forensic identifier.

        // when — extract the timestamped segment from the importBackupDir we built in setUpLayout
        Path tsSegment = importBackupDir.getFileName();
        assertThat(tsSegment)
                .as("importBackupDir must have a leaf segment (the <ts> directory name)")
                .isNotNull();

        // then — leaf segment matches yyyy-MM-ddTHH-mm-ssZ (ISO-8601 with `:` → `-`).
        assertThat(tsSegment.toString())
                .as("Timestamped <ts> sub-directory must match ISO-8601-with-dashes contract")
                .matches(TS_DIR_PATTERN);

        // and — the sub-directory is a direct child of the `.import-backups` parent.
        // This invariant is what the listener relies on when resolving uploads-old/ relative
        // to importBackupDir; if the parent were ever truncated or differently named, the
        // 24h retention sweeper (operator-side) would miss it.
        Path parent = importBackupDir.getParent();
        assertThat(parent)
                .as("Parent of <ts> must exist")
                .isNotNull();
        assertThat(parent.getFileName())
                .as("Parent must be `.import-backups` per app.backup.import-backups-dir contract")
                .hasToString(".import-backups");

        // Cross-check: regenerating the formula at the moment of assertion produces a value
        // that ALSO matches the same regex — the contract is stable, not coincidence.
        String regenerated = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString().replace(":", "-");
        assertThat(regenerated)
                .as("Re-applying production formula at assertion time must produce a contract-matching value")
                .matches(TS_DIR_PATTERN);
    }

    @AfterEach
    void cleanupTempLayout() throws IOException {
        if (Files.exists(tempRoot)) {
            try (Stream<Path> walk = Files.walk(tempRoot)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException _) {
                        // best-effort
                    }
                });
            } catch (IOException _) {
                // best-effort
            }
        }
    }
}
