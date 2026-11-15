package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.ctc.admin.dto.DataAuditFinding.Category;
import org.ctc.admin.service.DataAuditService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.CarImage;
import org.ctc.domain.model.CarImageKind;
import org.ctc.domain.model.ImageSource;
import org.ctc.domain.model.Track;
import org.ctc.domain.model.TrackImageKind;
import org.ctc.domain.repository.CarImageRepository;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.TrackImageRepository;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.TestImages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@CtcDevSpringBootContext
@AutoConfigureMockMvc
@Tag("integration")
class CatalogImageIT {
    @Autowired MockMvc mvc;
    @Autowired CatalogImageService gallery;
    @Autowired CarService carService;
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired CarImageRepository carImages;
    @Autowired TrackImageRepository trackImages;
    @Autowired DataAuditService audit;
    @Autowired TransactionTemplate tx;
    @Value("${app.upload-dir}") String uploadDir;
    UUID car;
    UUID track;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        car = cars.saveAndFlush(new Car("Test", "Gallery car " + suffix)).getId();
        track = tracks.saveAndFlush(new Track("Gallery track " + suffix)).getId();
    }

    @AfterEach
    void cleanUp() {
        if (cars.existsById(car)) gallery.deleteAllForCar(car);
        gallery.deleteAllForTrack(track);
        cars.findById(car).ifPresent(cars::delete);
        tracks.deleteById(track);
    }

    @Test
    void givenSeveralFiles_whenUploading_thenStoreThemInOrderWithTheirSize() {
        // when
        int added = gallery.uploadCarImages(car, List.of(png("front.png", 1920, 1080), jpeg("side.jpg", 3840, 2160)), CarImageKind.STUDIO, "  Livery A ");
        // then
        assertThat(added).isEqualTo(2);
        var images = gallery.carImages(car);
        assertThat(images).extracting(CarImage::getSortIndex).containsExactly(0, 1);
        assertThat(images).extracting(CarImage::getWidth).containsExactly(1920, 3840);
        assertThat(images).allSatisfy(image -> {
            assertThat(image.getSource()).isEqualTo(ImageSource.MANUAL);
            assertThat(image.getSourceKey()).isNull();
            assertThat(image.getLabel()).as("the label is trimmed").isEqualTo("Livery A");
            assertThat(file(image.getImagePath())).exists();
        });
    }

    @Test
    void givenOneInvalidFileAmongValidOnes_whenUploading_thenStoreNothing() {
        // when / then
        assertThatThrownBy(() -> gallery.uploadCarImages(car, List.of(png("ok.png", 10, 10), file("fake.png", "GIF89a....".getBytes())), CarImageKind.STUDIO, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("fake.png is not a PNG, JPEG or WebP image");
        assertThat(gallery.carImages(car)).isEmpty();
        assertThat(Path.of(uploadDir, "car-images", car.toString())).as("no file is written before every file passes").doesNotExist();
    }

    @Test
    void givenRolledBackUpload_whenTheTransactionEnds_thenRemoveTheStoredFile() {
        // when
        var path = tx.execute(status -> {
            gallery.uploadTrackImages(track, List.of(png("layout.png", 800, 600)), TrackImageKind.LAYOUT, null);
            status.setRollbackOnly();
            return gallery.trackImages(track).getFirst().getImagePath();
        });
        // then
        assertThat(file(path)).doesNotExist();
        assertThat(gallery.trackImages(track)).isEmpty();
    }

    @Test
    void givenGallery_whenMovingAndDeleting_thenKeepOrderAndRemoveTheFileAfterCommit() {
        // given
        gallery.uploadCarImages(car, List.of(png("a.png", 10, 10), png("b.png", 10, 10), png("c.png", 10, 10)), CarImageKind.SCENE, null);
        var ids = gallery.carImages(car).stream().map(CarImage::getId).toList();
        // when
        int position = gallery.moveCarImage(car, ids.get(2), true);
        int boundary = gallery.moveCarImage(car, ids.get(0), true);
        String removed = gallery.carImages(car).getFirst().getImagePath();
        gallery.deleteCarImage(car, ids.get(0));
        // then
        assertThat(position).isEqualTo(2);
        assertThat(boundary).as("the first image stays first").isEqualTo(1);
        assertThat(gallery.carImages(car)).extracting(CarImage::getId).containsExactly(ids.get(2), ids.get(1));
        assertThat(file(removed)).doesNotExist();
        assertThatThrownBy(() -> gallery.deleteCarImage(UUID.randomUUID(), ids.get(1))).as("an image belongs to its car")
                .isInstanceOf(org.ctc.domain.exception.EntityNotFoundException.class);
    }

    @Test
    void givenKinds_whenAskingForTheFirstImage_thenFollowTheSortOrder() {
        // given
        gallery.uploadCarImages(car, List.of(png("cockpit.png", 10, 10)), CarImageKind.COCKPIT, null);
        gallery.uploadCarImages(car, List.of(png("s1.png", 10, 10), png("s2.png", 10, 10)), CarImageKind.STUDIO, null);
        var second = gallery.carImages(car).get(2).getId();
        gallery.moveCarImage(car, second, true);
        // when / then
        assertThat(gallery.firstCarImage(car, CarImageKind.STUDIO)).map(CarImage::getId).contains(second);
        assertThat(gallery.firstCarImage(car, CarImageKind.SCENE)).isEmpty();
    }

    @Test
    void givenCarWithImages_whenDeletingTheCar_thenRemoveImagesAndFiles() {
        // given
        gallery.uploadCarImages(car, List.of(png("gone.png", 10, 10)), CarImageKind.OTHER, null);
        var path = gallery.carImages(car).getFirst().getImagePath();
        // when
        carService.delete(car);
        // then
        assertThat(carImages.findByCarIdOrderBySortIndexAscIdAsc(car)).isEmpty();
        assertThat(file(path)).doesNotExist();
    }

    @Test
    void givenEditorForms_whenUploadingMovingAndFailing_thenRedirectToTheGalleryWithAStatus() throws Exception {
        // when / then
        mvc.perform(multipart("/admin/cars/{id}/images", car).file(png("one.png", 10, 10)).file(png("two.png", 10, 10))
                        .param("kind", "STUDIO").param("label", "Front"))
                .andExpect(redirectedUrl("/admin/cars/" + car + "/edit#car-images")).andExpect(flash().attribute("galleryStatus", "2 images added."));
        var first = gallery.carImages(car).getFirst().getId();
        mvc.perform(post("/admin/cars/{id}/images/{image}/move", car, first).param("direction", "down"))
                .andExpect(redirectedUrl("/admin/cars/" + car + "/edit#image-" + first + "-down"))
                .andExpect(flash().attribute("galleryStatus", "Image moved to position 2."));
        mvc.perform(multipart("/admin/tracks/{id}/images", track).file(png("t.png", 10, 10)).param("kind", "STUDIO"))
                .andExpect(redirectedUrl("/admin/tracks/" + track + "/edit#track-images"))
                .andExpect(flash().attribute("galleryError", "Choose an image kind"));
        mvc.perform(get("/admin/cars/{id}/edit", car))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"image-" + first + "-up\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Move image 2 of 2 up")));
    }

    @Test
    void givenImageWhoseFileIsGone_whenAuditing_thenReportItWithoutDeletingTheRow() throws Exception {
        // given
        gallery.uploadTrackImages(track, List.of(png("lost.png", 10, 10)), TrackImageKind.BACKGROUND, null);
        var image = gallery.trackImages(track).getFirst();
        Files.delete(file(image.getImagePath()));
        // when
        var findings = audit.audit().findings(Category.CATALOG_IMAGES);
        // then
        assertThat(findings).anySatisfy(finding -> assertThat(finding.evidence()).contains(image.getId().toString()));
        assertThat(trackImages.findById(image.getId())).as("the audit changes nothing").isPresent();
    }

    private Path file(String url) {
        return Path.of(uploadDir).resolve(url.substring("/uploads/".length()));
    }

    private static MockMultipartFile png(String name, int width, int height) {
        return new MockMultipartFile("files", name, "image/png", TestImages.png(width, height));
    }

    private static MockMultipartFile jpeg(String name, int width, int height) {
        return new MockMultipartFile("files", name, "image/jpeg", TestImages.jpeg(width, height));
    }

    private static MultipartFile file(String name, byte[] data) {
        return new MockMultipartFile("files", name, "image/png", data);
    }
}
