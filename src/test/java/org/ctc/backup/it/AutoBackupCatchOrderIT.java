package org.ctc.backup.it;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;
import org.ctc.admin.TestDataService;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Phase 76 / Plan 03 — catch-order regression IT for {@code BackupController.importExecute}
 * (CONTEXT D-17 / SECU-07).
 *
 * <p>{@code AutoBackupBeforeImportException extends BackupImportException}. Java's
 * first-match-wins catch order means the subclass catch MUST appear textually BEFORE the
 * parent catch — otherwise the parent branch swallows the subclass and the operator sees
 * the wrong Flash message ("Import failed and was rolled back" instead of "Import aborted
 * — pre-import auto-backup failed. No database changes."). A future refactor that
 * reorders the catches would silently degrade the operator UX without any compilation
 * error. This IT pins the catch-order by asserting the EXACT Flash wording produced on
 * the auto-backup failure path.
 *
 * <p>Companion to {@link AutoBackupBeforeImportFailureIT}, which already exercises the
 * service-level exception and the controller-level lock-release; this IT focuses
 * exclusively on the Flash-message contract that proves the catch-chain ordering.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMockMvc
@Tag("integration")
class AutoBackupCatchOrderIT {

    private static final String EXPECTED_FLASH_PREFIX =
            "Import aborted — pre-import auto-backup failed. No database changes. Audit-id: ";

    /** The parent-catch Flash text — must NOT appear when AutoBackup failure path is exercised. */
    private static final String PARENT_CATCH_FLASH_PREFIX =
            "Import failed and was rolled back";

    @Autowired
    BackupImportService backupImportService;

    @MockitoSpyBean
    BackupArchiveService backupArchiveService;

    @Autowired
    TestDataService testDataService;

    @Autowired
    MockMvc mockMvc;

    @Value("${app.backup.import-backups-dir}")
    String importBackupsDirRaw;

    Path importBackupsDir;

    @BeforeAll
    void seedFixture() throws IOException {
        testDataService.seed();
        importBackupsDir = Paths.get(importBackupsDirRaw).toAbsolutePath().normalize();
        Files.createDirectories(importBackupsDir);
    }

    @BeforeEach
    void resetSpy() {
        Mockito.reset(backupArchiveService);
    }

    @Test
    void givenAutoBackupFails_whenImportExecutePostedViaController_thenFlashMessageMatchesSubclassBranchNotParentBranch()
            throws Exception {
        // given — pre-export + stage with real spy (before stub install).
        UUID stagingId = exportAndStage("auto-backup-catch-order-export.zip");

        // when — install the failure stub so the Step 0.5 writeZip throws IOException.
        Mockito.doThrow(new IOException("simulated auto-export failure (catch-order IT)"))
                .when(backupArchiveService).writeZip(Mockito.any(OutputStream.class), Mockito.any(Instant.class));

        // drive POST through the controller — controller catch chain resolves the
        // AutoBackupBeforeImportException → subclass branch (line 213 in BackupController)
        MvcResult result = mockMvc.perform(post("/admin/backup/import-execute")
                        .param("stagingId", stagingId.toString())
                        .param("acknowledged", "true"))
                .andReturn();

        // then — Flash carries the SUBCLASS branch wording. If a future refactor moves the
        // parent catch before the subclass catch (or removes the subclass catch entirely),
        // this assertion fails immediately — Flash would carry the parent-branch wording.
        Object errorMessage = result.getFlashMap().get("errorMessage");
        assertThat(errorMessage)
                .as("errorMessage Flash must be set on auto-backup failure path")
                .isNotNull()
                .isInstanceOf(String.class);
        String flashText = (String) errorMessage;

        assertThat(flashText)
                .as("Flash must come from the AutoBackupBeforeImportException branch (subclass catch first; D-17)")
                .startsWith(EXPECTED_FLASH_PREFIX);
        assertThat(flashText)
                .as("Flash must NOT come from the BackupImportException parent branch — "
                        + "if it does, the catch chain has regressed and the operator sees the wrong message")
                .doesNotStartWith(PARENT_CATCH_FLASH_PREFIX);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private UUID exportAndStage(String filename) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        backupArchiveService.writeZip(baos, Instant.now());
        byte[] zipBytes = baos.toByteArray();
        MockMultipartFile file = new MockMultipartFile("file", filename, "application/zip", zipBytes);
        return backupImportService.stage(file).stagingId();
    }
}
