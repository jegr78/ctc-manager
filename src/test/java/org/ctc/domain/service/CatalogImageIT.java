package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
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
    @Autowired FileStorageService files;
    @Autowired CarService carService;
    @Autowired TrackService trackService;
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired CarImageRepository carImages;
    @Autowired TrackImageRepository trackImages;
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
        if (tracks.existsById(track)) gallery.deleteAllForTrack(track);
        cars.findById(car).ifPresent(cars::delete);
        tracks.findById(track).ifPresent(tracks::delete);
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
    void givenExtensionOfAnotherFormat_whenUploading_thenRejectIt() {
        // given
        var disguised = new MockMultipartFile("files", "photo.pdf", "application/pdf", TestImages.jpeg(10, 10));
        // when / then
        assertThatThrownBy(() -> gallery.uploadCarImages(car, List.of(disguised), CarImageKind.STUDIO, null))
                .isInstanceOf(BusinessRuleException.class).hasMessage("photo.pdf is a JPEG image; its file extension must be .jpg");
        assertThat(gallery.carImages(car)).isEmpty();
    }

    @Test
    void givenWebpSentAsOctetStream_whenUploading_thenTrustTheCheckedContent() {
        // given
        var webp = new MockMultipartFile("files", "track.webp", "application/octet-stream", TestImages.webpLossy(800, 450));
        // when
        gallery.uploadTrackImages(track, List.of(webp), TrackImageKind.BACKGROUND, null);
        // then
        assertThat(gallery.trackImages(track)).singleElement().satisfies(image -> {
            assertThat(image.getWidth()).isEqualTo(800);
            assertThat(image.getImagePath()).endsWith(".webp");
            assertThat(file(image.getImagePath())).exists();
        });
    }

    @Test
    void givenTrackGallery_whenMovingDeletingAndDeletingTheTrack_thenKeepOrderAndRemoveFiles() throws Exception {
        // given
        gallery.uploadTrackImages(track, List.of(png("a.png", 10, 10), png("b.png", 10, 10)), TrackImageKind.LAYOUT, null);
        var images = gallery.trackImages(track);
        // when
        mvc.perform(post("/admin/tracks/{id}/images/{image}/move", track, images.get(1).getId()).param("direction", "up"))
                .andExpect(redirectedUrl("/admin/tracks/" + track + "/edit#image-" + images.get(1).getId() + "-up"))
                .andExpect(flash().attribute("galleryStatus", "Image moved to position 1."));
        mvc.perform(post("/admin/tracks/{id}/images/{image}/delete", track, images.get(0).getId()))
                .andExpect(flash().attribute("galleryStatus", "Image deleted."));
        String remaining = gallery.trackImages(track).getFirst().getImagePath();
        trackService.delete(track);
        // then
        assertThat(file(images.get(0).getImagePath())).doesNotExist();
        assertThat(file(remaining)).as("deleting the track removes its gallery files").doesNotExist();
        assertThat(trackImages.findByTrackIdOrderBySortIndexAscIdAsc(track)).isEmpty();
    }

    @Test
    void givenRejectedUpload_whenReturningToTheEditor_thenKeepTheChosenKindAndLabel() throws Exception {
        // when
        var result = mvc.perform(multipart("/admin/cars/{id}/images", car).file(new MockMultipartFile("files", "x.png", "image/png", "GIF89a....".getBytes()))
                        .param("kind", "COCKPIT").param("label", "Night race"))
                .andExpect(flash().attribute("galleryKind", "COCKPIT")).andExpect(flash().attribute("galleryLabel", "Night race")).andReturn();
        // then
        mvc.perform(get("/admin/cars/{id}/edit", car).flashAttrs(result.getFlashMap()))
                .andExpect(content().string(containsString("value=\"COCKPIT\" selected")))
                .andExpect(content().string(containsString("value=\"Night race\"")));
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
    void givenGt7AndManualImages_whenApplyingGt7Shots_thenReplaceTheSlotAppendNewOnesAndKeepManualImages() throws Exception {
        // given
        gallery.uploadCarImages(car, List.of(png("own.png", 10, 10)), CarImageKind.STUDIO, "Own");
        String first = storeGt7("first.jpg");
        gallery.applyGt7CarImages(car, List.of(new CatalogImageService.Gt7Shot("2_01", first, 1920, 1080)));
        String second = storeGt7("second.jpg");
        String scene = storeGt7("scene.jpg");
        // when
        var applied = gallery.applyGt7CarImages(car, List.of(new CatalogImageService.Gt7Shot("2_01", second, 1920, 1080),
                new CatalogImageService.Gt7Shot("1_01", scene, 1920, 1080)));
        // then
        assertThat(applied.added()).containsExactly("1_01");
        assertThat(applied.replaced()).containsExactly("2_01");
        var images = gallery.carImages(car);
        assertThat(images).extracting(CarImage::getSourceKey).containsExactly(null, "2_01", "1_01");
        assertThat(images).extracting(CarImage::getKind).containsExactly(CarImageKind.STUDIO, CarImageKind.STUDIO, CarImageKind.SCENE);
        assertThat(images.get(0).getLabel()).as("manual images stay untouched").isEqualTo("Own");
        assertThat(images.get(1).getImagePath()).isEqualTo(second);
        assertThat(file(first)).as("the replaced file is removed after commit").doesNotExist();
    }

    @Test
    void givenRolledBackGt7Apply_whenTheTransactionEnds_thenRemoveTheStagedFile() throws Exception {
        // given
        String staged = storeGt7("staged.jpg");
        // when
        tx.executeWithoutResult(status -> {
            gallery.applyGt7CarImages(car, List.of(new CatalogImageService.Gt7Shot("2_01", staged, 1920, 1080)));
            status.setRollbackOnly();
        });
        // then
        assertThat(file(staged)).doesNotExist();
        assertThat(gallery.carImages(car)).isEmpty();
    }

    @Test
    void givenCarWithoutGt7Id_whenOpeningOrLoadingGt7Images_thenHideTheButtonAndExplain() throws Exception {
        // when / then
        mvc.perform(get("/admin/cars/{id}/edit", car)).andExpect(content().string(not(containsString("Load GT7 images"))));
        mvc.perform(post("/admin/cars/{id}/images/gt7", car))
                .andExpect(redirectedUrl("/admin/cars/" + car + "/edit#car-images")).andExpect(flash().attribute("galleryError", "This car has no GT7 ID"));
        var withId = cars.findById(car).orElseThrow();
        withId.setGt7Id("car365");
        cars.saveAndFlush(withId);
        mvc.perform(get("/admin/cars/{id}/edit", car)).andExpect(content().string(containsString("Load GT7 images")));
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
                .andExpect(content().string(containsString("id=\"image-" + first + "-up\"")))
                .andExpect(content().string(containsString("Move image 2 of 2 up")));
        mvc.perform(get("/admin/cars/new")).andExpect(content().string(not(containsString("-images\""))));
        mvc.perform(get("/admin/tracks/new")).andExpect(content().string(not(containsString("-images\""))));
    }

    private String storeGt7(String name) throws java.io.IOException {
        return files.storeBytes("car-images", car, name, TestImages.jpeg(1920, 1080));
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
