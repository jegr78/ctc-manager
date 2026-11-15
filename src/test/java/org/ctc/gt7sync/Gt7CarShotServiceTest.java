package org.ctc.gt7sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.CarImage;
import org.ctc.domain.model.ImageSource;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.service.CatalogImageService;
import org.ctc.domain.service.CatalogImageService.Gt7Applied;
import org.ctc.domain.service.CatalogImageService.Gt7Shot;
import org.ctc.domain.service.FileStorageService;
import org.ctc.gt7sync.Gt7ScraperService.CarShot;
import org.ctc.testsupport.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Gt7CarShotServiceTest {
    @Mock CarRepository cars;
    @Mock Gt7ScraperService scraper;
    @Mock FileStorageService files;
    @Mock CatalogImageService gallery;
    @InjectMocks Gt7CarShotService service;
    final UUID carId = UUID.randomUUID();
    final Car car = new Car("Test", "Shot car");
    final byte[] scene = TestImages.jpeg(1920, 1080);
    final byte[] studio = TestImages.jpeg(1920, 1081);

    @BeforeEach
    void setUp() {
        car.setGt7Id("365");
        when(cars.findById(carId)).thenReturn(Optional.of(car));
    }

    @Test
    void givenNewChangedAndIdenticalSlots_whenLoading_thenStoreOnlyChangedFilesAndReportEachSlot() throws IOException {
        // given
        when(gallery.carImages(carId)).thenReturn(List.of(gt7("2_01", "/uploads/car-images/old-studio.jpg")));
        when(scraper.scrapeCarShots("365")).thenReturn(List.of(new CarShot("1_01", "https://gt7/a.jpg"), new CarShot("2_01", "https://gt7/b.jpg"),
                new CarShot("2_02", "https://gt7/c.jpg")));
        when(files.downloadImage("https://gt7/a.jpg", "gt7.jpg")).thenReturn(scene);
        when(files.downloadImage("https://gt7/b.jpg", "gt7.jpg")).thenReturn(studio);
        when(files.downloadImage("https://gt7/c.jpg", "gt7.jpg")).thenReturn(studio);
        when(files.hasContent("/uploads/car-images/old-studio.jpg", studio)).thenReturn(true);
        when(files.storeBytes(eq("car-images"), eq(carId), any(), any())).thenReturn("/uploads/car-images/new1.jpg", "/uploads/car-images/new2.jpg");
        when(gallery.applyGt7CarImages(eq(carId), anyList())).thenReturn(new Gt7Applied(List.of("1_01", "2_02"), List.of()));
        // when
        var result = service.load(carId);
        // then
        @SuppressWarnings("unchecked") ArgumentCaptor<List<Gt7Shot>> staged = ArgumentCaptor.forClass(List.class);
        verify(gallery).applyGt7CarImages(eq(carId), staged.capture());
        assertThat(staged.getValue()).containsExactly(new Gt7Shot("1_01", "/uploads/car-images/new1.jpg", 1920, 1080),
                new Gt7Shot("2_02", "/uploads/car-images/new2.jpg", 1920, 1081));
        assertThat(result.message()).isEqualTo("GT7 images: added 1_01, 2_02; replaced none; unchanged 2_01.");
    }

    @Test
    void givenApplyingFails_whenLoading_thenRemoveTheNewFiles() throws IOException {
        // given
        when(gallery.carImages(carId)).thenReturn(List.of());
        when(scraper.scrapeCarShots("365")).thenReturn(List.of(new CarShot("1_01", "https://gt7/a.jpg")));
        when(files.downloadImage("https://gt7/a.jpg", "gt7.jpg")).thenReturn(scene);
        when(files.storeBytes(eq("car-images"), eq(carId), any(), any())).thenReturn("/uploads/car-images/new1.jpg");
        when(gallery.applyGt7CarImages(eq(carId), anyList())).thenThrow(new IllegalStateException("database down"));
        // when / then
        assertThatThrownBy(() -> service.load(carId)).isInstanceOf(BusinessRuleException.class).hasMessage("GT7 images could not be loaded: database down");
        verify(files).delete("/uploads/car-images/new1.jpg");
    }

    @Test
    void givenFailedDownload_whenLoading_thenStoreNothingAndChangeNothing() throws IOException {
        // given
        when(gallery.carImages(carId)).thenReturn(List.of());
        when(scraper.scrapeCarShots("365")).thenReturn(List.of(new CarShot("1_01", "https://gt7/a.jpg"), new CarShot("2_01", "https://gt7/b.jpg")));
        when(files.downloadImage("https://gt7/a.jpg", "gt7.jpg")).thenReturn(scene);
        when(files.storeBytes(eq("car-images"), eq(carId), any(), any())).thenReturn("/uploads/car-images/new1.jpg");
        when(files.downloadImage("https://gt7/b.jpg", "gt7.jpg")).thenThrow(new IOException("Image download failed with HTTP 404"));
        // when / then
        assertThatThrownBy(() -> service.load(carId)).hasMessage("GT7 images could not be loaded: Image download failed with HTTP 404");
        verify(files).delete("/uploads/car-images/new1.jpg");
        verify(gallery, never()).applyGt7CarImages(any(), any());
    }

    @Test
    void givenCarWithoutShots_whenLoading_thenReportThatNothingChanged() throws IOException {
        // given
        when(gallery.carImages(carId)).thenReturn(List.of());
        when(scraper.scrapeCarShots("365")).thenReturn(List.of());
        when(gallery.applyGt7CarImages(carId, List.of())).thenReturn(new Gt7Applied(List.of(), List.of()));
        // when / then
        assertThat(service.load(carId).message()).isEqualTo("GT7 lists no car shots for this car; nothing changed.");
    }

    @Test
    void givenCarWithoutGt7Id_whenLoading_thenRejectBeforeAnyRequest() throws IOException {
        // given
        car.setGt7Id(null);
        // when / then
        assertThatThrownBy(() -> service.load(carId)).isInstanceOf(BusinessRuleException.class).hasMessage("This car has no GT7 ID");
        verify(scraper, never()).scrapeCarShots(any());
    }

    private static CarImage gt7(String slot, String path) {
        var image = new CarImage();
        image.setSource(ImageSource.GT7);
        image.setSourceKey(slot);
        image.setImagePath(path);
        return image;
    }
}
