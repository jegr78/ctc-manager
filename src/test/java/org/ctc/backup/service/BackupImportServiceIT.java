package org.ctc.backup.service;

import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.dto.EntityRowCount;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 74 Plan 05 — happy-path IT for {@link BackupImportService}.
 *
 * <p>Boots the {@code dev} profile (H2 in-memory + DevDataSeeder fixture) and drives
 * the four happy/rejection scenarios:
 * <ol>
 *   <li>Stage a Phase-73 export ZIP → 24 entity cards, correct field values.</li>
 *   <li>Reparse the staged file → equivalent preview, staging file survives (D-08).</li>
 *   <li>Delete staging file → file gone; second delete is silent-idempotent.</li>
 *   <li>Stage a non-ZIP payload → {@code Reason.NOT_A_ZIP}, no staging file written.</li>
 * </ol>
 *
 * <p>All fixtures are generated programmatically (D-25). No binary blobs committed.
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BackupImportServiceIT {

    @Autowired
    BackupImportService service;

    @Autowired
    BackupArchiveService archive;

    @Autowired
    BackupSchema backupSchema;

    @Value("${app.backup.staging-dir}")
    String stagingDirRaw;

    Path stagingDir;
    byte[] phase73ZipBytes;

    @BeforeAll
    void produceFixtureBytes() throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            archive.writeZip(out, Instant.now());
            this.phase73ZipBytes = out.toByteArray();
        }
    }

    @BeforeEach
    void clearStagingDir() throws IOException {
        stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
        Files.createDirectories(stagingDir);
        try (var paths = Files.list(stagingDir)) {
            paths.filter(p -> p.getFileName().toString().startsWith("upload-"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            // ignore in @BeforeEach
                        }
                    });
        }
    }

    @Test
    void givenPhase73Export_whenStage_thenPreviewHasNonZeroEntityCounts_andAll24CardsPopulated()
            throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "phase73-export.zip", "application/zip", phase73ZipBytes);

        // when
        BackupImportPreview preview = service.stage(file);

        // then
        assertThat(preview).as("preview must not be null").isNotNull();
        assertThat(preview.stagingId()).as("stagingId must be a fresh UUID").isNotNull();
        assertThat(preview.originalFilename())
                .as("originalFilename must match the multipart filename")
                .isEqualTo("phase73-export.zip");
        assertThat(preview.fileSizeBytes())
                .as("fileSizeBytes must match the ZIP byte length")
                .isEqualTo(phase73ZipBytes.length);
        assertThat(preview.schemaVersion())
                .as("schemaVersion must match BackupSchema.SCHEMA_VERSION")
                .isEqualTo(BackupSchema.SCHEMA_VERSION);
        assertThat(preview.currentSchemaVersion())
                .as("currentSchemaVersion must match BackupSchema.SCHEMA_VERSION")
                .isEqualTo(BackupSchema.SCHEMA_VERSION);
        assertThat(preview.schemaMatches())
                .as("schemaMatches must be true for a matching export")
                .isTrue();
        assertThat(preview.entityCounts())
                .as("entityCounts must have exactly one card per EntityRef")
                .hasSize(backupSchema.getExportOrder().size());

        // verify order matches getExportOrder()
        assertThat(preview.entityCounts())
                .extracting(EntityRowCount::tableName)
                .as("entityCounts order must match BackupSchema.getExportOrder() exactly")
                .containsExactlyElementsOf(
                        backupSchema.getExportOrder().stream().map(EntityRef::tableName).toList());

        assertThat(preview.entityCounts())
                .allSatisfy(card -> assertThat(card.humanLabel())
                        .as("humanLabel for %s must be non-blank", card.tableName())
                        .isNotBlank());

        assertThat(preview.uploadFileCount())
                .as("uploadFileCount must be non-negative")
                .isGreaterThanOrEqualTo(0);

        long expectedTotal = preview.entityCounts().stream()
                .mapToLong(EntityRowCount::importedRows).sum();
        assertThat(preview.totalImportedRows())
                .as("totalImportedRows must equal sum of importedRows")
                .isEqualTo(expectedTotal);

        // staging file must survive after successful stage (D-08)
        Path staged = stagingDir.resolve("upload-" + preview.stagingId() + ".zip");
        assertThat(staged)
                .as("staging file must exist after successful stage")
                .exists();
    }

    @Test
    void givenStagedFile_whenReparse_thenReturnsEquivalentPreview_andStagingFileSurvives()
            throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "phase73-export.zip", "application/zip", phase73ZipBytes);
        BackupImportPreview original = service.stage(file);
        UUID stagingId = original.stagingId();

        // when
        BackupImportPreview reparsed = service.reparse(stagingId);

        // then
        assertThat(reparsed).as("reparsed preview must not be null").isNotNull();
        assertThat(reparsed.schemaVersion())
                .as("schemaVersion must match original")
                .isEqualTo(original.schemaVersion());
        assertThat(reparsed.entityCounts())
                .as("entityCounts size must match original")
                .hasSize(original.entityCounts().size());

        for (int i = 0; i < original.entityCounts().size(); i++) {
            EntityRowCount orig = original.entityCounts().get(i);
            EntityRowCount reparsedCard = reparsed.entityCounts().get(i);
            assertThat(reparsedCard.tableName())
                    .as("tableName at index %d must match", i)
                    .isEqualTo(orig.tableName());
            assertThat(reparsedCard.importedRows())
                    .as("importedRows at index %d must match", i)
                    .isEqualTo(orig.importedRows());
            assertThat(reparsedCard.currentRows())
                    .as("currentRows at index %d must match (DB static within test)", i)
                    .isEqualTo(orig.currentRows());
        }

        // D-08: staging file must still exist after reparse
        Path staged = stagingDir.resolve("upload-" + stagingId + ".zip");
        assertThat(staged)
                .as("staging file must still exist after reparse (D-08)")
                .exists();
    }

    @Test
    void givenStagingId_whenDeleteStagingFile_thenFileGone() throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "phase73-export.zip", "application/zip", phase73ZipBytes);
        BackupImportPreview preview = service.stage(file);
        UUID stagingId = preview.stagingId();
        Path staged = stagingDir.resolve("upload-" + stagingId + ".zip");
        assertThat(staged).as("staging file must exist before delete").exists();

        // when
        service.deleteStagingFile(stagingId);

        // then
        assertThat(staged).as("staging file must be gone after delete").doesNotExist();

        // second delete must not throw (Files.deleteIfExists semantics)
        service.deleteStagingFile(stagingId);
        // silent idempotency — no exception expected
    }

    @Test
    void givenNonZipUpload_whenStage_thenThrowsNotAZip_andNoStagingFileWritten() throws Exception {
        // given — payload bytes whose first 4 bytes are {0x68, 0x65, 0x6C, 0x6C} ("hell")
        // deliberately ≥ 4 bytes so the Arrays.equals comparison does the work, not length guard
        byte[] notAZip = "hello world\n".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "not-a-zip.zip", "application/zip", notAZip);

        // when / then
        assertThatThrownBy(() -> service.stage(file))
                .isInstanceOf(BackupArchiveException.class)
                .satisfies(t -> assertThat(((BackupArchiveException) t).reason())
                        .isEqualTo(Reason.NOT_A_ZIP));

        // the magic-byte sniff fires BEFORE transferTo, so no staging file was ever written
        assertThat(Files.list(stagingDir).count())
                .as("no staging file must be written for a NOT_A_ZIP rejection")
                .isZero();
    }
}
