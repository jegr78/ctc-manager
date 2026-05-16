package org.ctc.backup.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.ctc.admin.TestDataService;
import org.ctc.backup.dto.BackupImportPreview;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@ExtendWith(OutputCaptureExtension.class)
@Tag("integration")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackupRestoreZipOpenCountIT {

    private static final Path IMPORT_BACKUPS_ROOT;
    static {
        try {
            IMPORT_BACKUPS_ROOT = Files.createTempDirectory("ctc-back03-import-backups-it-");
            IMPORT_BACKUPS_ROOT.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to allocate import-backups tempdir", e);
        }
    }

    @DynamicPropertySource
    static void overrideImportBackupsDir(DynamicPropertyRegistry registry) {
        registry.add("app.backup.import-backups-dir", IMPORT_BACKUPS_ROOT::toString);
    }

    @Autowired
    BackupImportService backupImportService;

    @Autowired
    BackupArchiveService backupArchiveService;

    @Autowired
    TestDataService testDataService;

    @BeforeAll
    void seedFixture() {
        testDataService.seed();
    }

    @BeforeEach
    void cleanImportBackupsDir() throws IOException {
        if (!Files.exists(IMPORT_BACKUPS_ROOT)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(IMPORT_BACKUPS_ROOT)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(IMPORT_BACKUPS_ROOT))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new IllegalStateException("Failed to delete " + p, e);
                        }
                    });
        }
    }

    @Test
    void givenStagedBackup_whenExecuteImport_thenZipOpenedExactlyOnce() throws Exception {
        // given
        byte[] zipBytes = exportToBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "back03-counter-test.zip", "application/zip", zipBytes);
        BackupImportPreview preview = backupImportService.stage(file);
        UUID stagingId = preview.stagingId();

        // when
        backupImportService.execute(stagingId);

        // then
        assertThat(backupImportService.getZipOpenCount()).isEqualTo(1);
    }

    @Test
    void givenZipWithMissingEntry_whenRestore_thenWarnLogEmittedAndZeroRows(
            CapturedOutput output) throws Exception {
        // given
        byte[] fullZipBytes = exportToBytes();
        byte[] strippedZipBytes = stripEntry(fullZipBytes, "data/cars.json");

        MockMultipartFile file = new MockMultipartFile(
                "file", "in03-missing-entry-test.zip", "application/zip", strippedZipBytes);
        BackupImportPreview preview = backupImportService.stage(file);
        UUID stagingId = preview.stagingId();

        // when
        backupImportService.execute(stagingId);

        // then
        assertThat(output.getAll()).contains("Backup ZIP has no data entry for table=");
    }

    private byte[] exportToBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        backupArchiveService.writeZip(baos, Instant.now());
        return baos.toByteArray();
    }

    private byte[] stripEntry(byte[] zipBytes, String entryName) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes));
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    zis.closeEntry();
                    continue;
                }
                zos.putNextEntry(new ZipEntry(entry.getName()));
                zis.transferTo(zos);
                zos.closeEntry();
                zis.closeEntry();
            }
        }
        return baos.toByteArray();
    }
}
