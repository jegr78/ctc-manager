package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.CarImage;
import org.ctc.domain.model.CarImageKind;
import org.ctc.domain.model.ImageSource;
import org.ctc.domain.repository.CarRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.TestImages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

@CtcDevSpringBootContext
@Tag("integration")
class CarCutoutServiceIT {
    @Autowired CarCutoutService cutouts;
    @Autowired CatalogImageService gallery;
    @Autowired CarRepository cars;
    @Autowired FileStorageService files;
    @Autowired ImportLockService imports;
    UUID car;

    @BeforeEach
    void setUp() {
        car = cars.saveAndFlush(new Car("Test", "Test cutout car " + UUID.randomUUID().toString().substring(0, 8))).getId();
    }

    @AfterEach
    void cleanUp() {
        if (imports.isLocked()) imports.unlock();
        gallery.deleteAllForCar(car);
        cars.deleteById(car);
    }

    @Test
    void givenCarWithStudio_whenCreateCutout_thenOneAutoCutoutStored() throws Exception {
        // given
        gallery.uploadCarImages(car, List.of(studio()), CarImageKind.STUDIO, null);

        // when
        boolean created = cutouts.createCutout(car);

        // then
        assertThat(created).isTrue();
        var cutout = gallery.firstCarImage(car, CarImageKind.CUTOUT).orElseThrow();
        assertThat(cutout.getSource()).isEqualTo(ImageSource.AUTO);
        assertThat(cutout.getSourceKey()).isEqualTo(CatalogImageService.AUTO_CUTOUT_KEY);
        assertThat(files.exists(cutout.getImagePath())).isTrue();
        var stored = ImageIO.read(files.resolve(cutout.getImagePath()).toFile());
        assertThat(stored.getColorModel().hasAlpha()).as("stored cutout keeps transparency").isTrue();
        assertThat(cutout.getWidth()).isEqualTo(stored.getWidth());
    }

    @Test
    void givenCarWithoutStudio_whenCreateCutout_thenFalseAndNothingStored() {
        // when / then
        assertThat(cutouts.createCutout(car)).isFalse();
        assertThat(gallery.carImages(car)).isEmpty();
    }

    @Test
    void givenExistingAutoCutout_whenCreateCutoutAgain_thenReplacedAndOldFileDeletedAfterCommit() throws Exception {
        // given
        gallery.uploadCarImages(car, List.of(studio()), CarImageKind.STUDIO, null);
        cutouts.createCutout(car);
        var first = gallery.firstCarImage(car, CarImageKind.CUTOUT).orElseThrow();

        // when
        cutouts.createCutout(car);

        // then
        var autos = gallery.carImages(car).stream().filter(image -> image.getSource() == ImageSource.AUTO).toList();
        assertThat(autos).singleElement().satisfies(image -> {
            assertThat(image.getId()).as("the row is reused").isEqualTo(first.getId());
            assertThat(image.getImagePath()).isNotEqualTo(first.getImagePath());
        });
        assertThat(files.exists(first.getImagePath())).as("old file deleted after commit").isFalse();
    }

    @Test
    void givenManualCutoutFirst_whenCreateCutout_thenManualKeepsSortPosition() throws Exception {
        // given
        gallery.uploadCarImages(car, List.of(studio()), CarImageKind.STUDIO, null);
        gallery.uploadCarImages(car, List.of(new MockMultipartFile("files", "manual.png", "image/png", TestImages.png(64, 32))),
                CarImageKind.CUTOUT, null);

        // when
        cutouts.createCutout(car);

        // then
        assertThat(gallery.firstCarImage(car, CarImageKind.CUTOUT)).map(CarImage::getSource).contains(ImageSource.MANUAL);
    }

    @Test
    void givenRestoreLockHeld_whenCreateCutout_thenRejectedWithoutWrite() throws Exception {
        // given
        gallery.uploadCarImages(car, List.of(studio()), CarImageKind.STUDIO, null);
        imports.tryLock();

        // when / then
        assertThatThrownBy(() -> cutouts.createCutout(car)).isInstanceOf(BusinessRuleException.class).hasMessage("A restore is running.");
        imports.unlock();
        assertThat(gallery.firstCarImage(car, CarImageKind.CUTOUT)).isEmpty();
    }

    private static MockMultipartFile studio() throws Exception {
        var image = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(new Color(25, 25, 25));
        g.fillRect(0, 0, 640, 360);
        g.setColor(new Color(200, 20, 30));
        g.fillRoundRect(150, 150, 340, 100, 80, 80);
        g.dispose();
        var out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return new MockMultipartFile("files", "studio.jpg", "image/jpeg", out.toByteArray());
    }
}
