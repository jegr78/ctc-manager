package org.ctc.backup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.CatalogImageService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.SitegenTestDir;
import org.ctc.testsupport.TestImages;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@CtcDevSpringBootContext
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:ctc_gallery_backup;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Tag("integration")
class CatalogImageBackupRoundTripIT {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Path ROOT = SitegenTestDir.create("gallery-backup");
    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry properties) {
        properties.add("app.upload-dir", () -> ROOT.resolve("uploads").toString());
        properties.add("app.backup.staging-dir", () -> ROOT.resolve("staging").toString());
        properties.add("app.backup.import-backups-dir", () -> ROOT.resolve("imports").toString());
        properties.add("ctc.site.output-dir", () -> ROOT.resolve("site").toString());
    }
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired CarImageRepository carImages;
    @Autowired TrackImageRepository trackImages;
    @Autowired CatalogImageService gallery;
    @Autowired BackupArchiveService archives;
    @MockitoSpyBean BackupImportService imports;
    UUID car;
    UUID track;

    @BeforeEach
    void setUp() throws IOException {
        // given
        Files.createDirectories(ROOT.resolve("uploads"));
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        car = cars.saveAndFlush(new Car("Test", "Backup gallery car " + suffix)).getId();
        track = tracks.saveAndFlush(new Track("Backup gallery track " + suffix)).getId();
        gallery.uploadCarImages(car, List.of(png("a.png"), png("b.png")), CarImageKind.STUDIO, "Front");
        gallery.uploadTrackImages(track, List.of(png("t.png")), TrackImageKind.LAYOUT, null);
        org.mockito.Mockito.clearInvocations(imports);
    }

    @AfterEach
    void cleanUp() {
        if (cars.existsById(car)) gallery.deleteAllForCar(car);
        if (tracks.existsById(track)) gallery.deleteAllForTrack(track);
        cars.findById(car).ifPresent(cars::delete);
        tracks.findById(track).ifPresent(tracks::delete);
    }

    @Test
    void givenGalleries_whenRestoring_thenRestoreRowsOrderAndFiles() throws Exception {
        // given
        byte[] archive = export();
        var images = gallery.carImages(car);
        var second = images.get(1);
        gallery.moveCarImage(car, second.getId(), true);
        gallery.deleteCarImage(car, images.get(0).getId());
        // when
        imports.execute(imports.stage(upload(archive)).stagingId());
        // then
        verify(imports).wipeAllTables(anyMap());
        var restored = gallery.carImages(car);
        assertThat(restored).extracting(CarImage::getId).containsExactly(images.get(0).getId(), second.getId());
        assertThat(restored).extracting(CarImage::getLabel).containsOnly("Front");
        assertThat(restored).allSatisfy(image -> assertThat(ROOT.resolve("uploads").resolve(image.getImagePath().substring("/uploads/".length()))).exists());
        assertThat(gallery.trackImages(track)).singleElement().satisfies(image -> {
            assertThat(image.getKind()).isEqualTo(TrackImageKind.LAYOUT);
            assertThat(image.getWidth()).isEqualTo(640);
        });
    }

    @Test
    void givenOlderSchemaCarryingImageRows_whenRestoring_thenRejectBeforeWiping() throws Exception {
        // given
        var files = unzip(export());
        var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json"));
        manifest.put("schema_version", 23);
        files.put("manifest.json", JSON.writeValueAsBytes(manifest));
        // when / then
        assertThatThrownBy(() -> imports.stage(upload(zip(files)))).isInstanceOfSatisfying(BackupArchiveException.class,
                failure -> assertThat(failure.reason()).isEqualTo(BackupArchiveException.Reason.DATA_MISMATCH));
        verify(imports, never()).wipeAllTables(anyMap());
    }

    @Test
    void givenArchiveWithoutAnImageFile_whenRestoring_thenRejectBeforeWiping() throws Exception {
        // given
        var files = unzip(export());
        String path = gallery.trackImages(track).getFirst().getImagePath();
        files.remove("uploads/" + path.substring("/uploads/".length()));
        // when / then
        assertThatThrownBy(() -> imports.stage(upload(zip(files)))).isInstanceOfSatisfying(BackupArchiveException.class, failure -> {
            assertThat(failure.reason()).isEqualTo(BackupArchiveException.Reason.DATA_MISMATCH);
            assertThat(failure.getMessage()).contains("is missing from the archive");
        });
        verify(imports, never()).wipeAllTables(anyMap());
    }

    @Test
    void givenGalleryFileLostOnDisk_whenExporting_thenLeaveTheRowOutAndKeepTheArchiveRestorable() throws Exception {
        // given
        var lost = gallery.carImages(car).getFirst();
        Files.delete(ROOT.resolve("uploads").resolve(lost.getImagePath().substring("/uploads/".length())));
        // when
        byte[] archive = export();
        imports.execute(imports.stage(upload(archive)).stagingId());
        // then
        assertThat(gallery.carImages(car)).extracting(CarImage::getId).as("only the image with its file survives")
                .doesNotContain(lost.getId()).hasSize(1);
        assertThat(gallery.trackImages(track)).hasSize(1);
    }

    private static MockMultipartFile png(String name) { return new MockMultipartFile("files", name, "image/png", TestImages.png(640, 360)); }
    private byte[] export() throws IOException {
        var output = new ByteArrayOutputStream();
        archives.writeZip(output, Instant.now());
        return output.toByteArray();
    }
    private MockMultipartFile upload(byte[] bytes) { return new MockMultipartFile("file", "test-gallery.zip", "application/zip", bytes); }
    private Map<String, byte[]> unzip(byte[] archive) throws IOException {
        var files = new LinkedHashMap<String, byte[]>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) files.put(entry.getName(), zip.readAllBytes());
        }
        return files;
    }
    private byte[] zip(Map<String, byte[]> files) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
