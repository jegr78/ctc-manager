package org.ctc.gt7sync;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.CarMetadataService;
import org.ctc.domain.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class Gt7MetadataSyncTest {
    private final Gt7ScraperService scraper = mock(Gt7ScraperService.class);
    private final GtPlusCarYearSource years = mock(GtPlusCarYearSource.class);
    private final CarRepository cars = mock(CarRepository.class);
    private final TrackRepository tracks = mock(TrackRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final Gt7SyncService service = new Gt7SyncService(scraper, cars, tracks, storage, new CarMetadataService(), years, new Gt7SyncPreviewStore());
    private final Car car = new Car("Test", "Test Car '20");

    @BeforeEach
    void givenExistingCarAndSources() throws Exception {
        car.setId(UUID.randomUUID());
        car.setGt7Id("car3531");
        car.setImageUrl("/uploads/test-original.png");
        when(scraper.scrapeCars()).thenReturn(List.of(new Gt7ScraperService.ScrapedCar("car3531", "Test", "Test Car '20", "https://example.test/new.png", CarCategory.GR_3, Drivetrain.FR, "Gr.3", "FR")));
        when(scraper.scrapeTracks()).thenReturn(List.of());
        when(years.fetchCandidates()).thenReturn(Map.of("car3531", new GtPlusCarYearSource.YearCandidate(2020, "GTPlus code 3531 year 2020", null)));
        when(cars.findAllByGt7Id("car3531")).thenReturn(List.of(car));
        when(cars.findAllByGt7IdForUpdate("car3531")).thenReturn(List.of(car));
    }

    @Test
    void givenSelectedMetadataUpdate_whenExecutingReviewedPreview_thenIdentityAndImageStayUnchanged() throws Exception {
        // given
        var id = car.getId();
        var preview = service.fetchAndPreview();
        assertThat(preview.getMetadataUpdateCount()).isEqualTo(1);
        // when
        var result = service.executeSync(List.of(), List.of(), List.of("car3531"), preview.previewId());
        // then
        assertThat(result.carsUpdated()).isEqualTo(1);
        assertThat(car.getId()).isEqualTo(id);
        assertThat(car.getImageUrl()).isEqualTo("/uploads/test-original.png");
        assertThat(car.getModelYear()).isEqualTo(2020);
        assertThat(car.getCategory()).isEqualTo(CarCategory.GR_3);
        verify(cars).save(car);
        verifyNoInteractions(storage);
    }

    @Test
    void givenChangedSourceAfterPreview_whenExecuting_thenUnseenMetadataCannotApply() throws Exception {
        // given
        var preview = service.fetchAndPreview();
        when(years.fetchCandidates()).thenReturn(Map.of("car3531", new GtPlusCarYearSource.YearCandidate(2099, "changed source", null)));
        // when
        var result = service.executeSync(List.of(), List.of(), List.of("car3531"), preview.previewId());
        // then
        assertThat(result.carsUpdated()).isZero();
        assertThat(result.errors()).anyMatch(message -> message.contains("changed"));
        assertThat(car.getCategory()).isNull();
        assertThat(car.getModelYear()).isNull();
        verify(cars, never()).save(any());
    }

    @Test
    void givenSecondarySourceFailure_whenPreviewing_thenOfficialMetadataStillAvailable() throws Exception {
        // given
        when(years.fetchCandidates()).thenThrow(new java.io.IOException("secondary offline"));
        // when
        var preview = service.fetchAndPreview();
        // then
        assertThat(preview.cars()).hasSize(1);
        assertThat(preview.cars().getFirst().metadata().category()).isEqualTo(CarCategory.GR_3);
        assertThat(preview.warnings()).anyMatch(message -> message.contains("year"));
    }

    @Test
    void givenOnlyLegacyNameMatch_whenPreviewing_thenIdentityRequiresManualReview() throws Exception {
        // given
        when(cars.findAllByGt7Id("car3531")).thenReturn(List.of());
        when(cars.findAllByManufacturerAndName("Test", "Test Car '20")).thenReturn(List.of(car));
        car.setGt7Id(null);
        // when
        var preview = service.fetchAndPreview();
        // then
        assertThat(preview.cars().getFirst().status()).isEqualTo(Gt7SyncPreview.SyncStatus.AMBIGUOUS);
        assertThat(preview.getNewCarCount()).isZero();
        assertThat(preview.getMetadataUpdateCount()).isZero();
        assertThat(car.getGt7Id()).isNull();
    }
    @Test
    void givenLocalManualChangeAfterPreview_whenExecuting_thenRequiresFreshReview() throws Exception {
        // given
        var preview = service.fetchAndPreview();
        car.setCategory(CarCategory.GR_4);
        car.setCategoryOverride(true);
        // when
        var result = service.executeSync(List.of(), List.of(), List.of("car3531"), preview.previewId());
        // then
        assertThat(result.carsUpdated()).isZero();
        assertThat(result.errors()).anyMatch(message -> message.contains("changed"));
        assertThat(car.getCategory()).isEqualTo(CarCategory.GR_4);
        verify(cars, never()).save(any());
    }

    @Test
    void givenNewCarWithoutPreviewToken_whenExecutingNativeSelection_thenRequiresReview() throws Exception {
        // given
        when(cars.findAllByGt7IdForUpdate("car3531")).thenReturn(List.of());
        when(cars.save(any(Car.class))).thenAnswer(invocation -> invocation.getArgument(0));
        // when
        var result = service.executeSync(List.of("car3531"), List.of(), List.of(), null);
        // then
        assertThat(result.carsImported()).isZero();
        assertThat(result.errors()).anyMatch(message -> message.contains("preview"));
        verify(cars, never()).save(any());
        verifyNoInteractions(storage);
    }

    @Test
    void givenDuplicateSourceIdentity_whenExecuting_thenCannotImportOrUpdate() throws Exception {
        // given
        var source = scraper.scrapeCars().getFirst();
        when(scraper.scrapeCars()).thenReturn(List.of(source, source));
        var preview = service.fetchAndPreview();
        // when
        var result = service.executeSync(List.of(), List.of(), List.of("car3531"), preview.previewId());
        // then
        assertThat(preview.cars()).allMatch(entry -> entry.status() == Gt7SyncPreview.SyncStatus.AMBIGUOUS);
        assertThat(result.carsUpdated()).isZero();
        assertThat(result.errors()).anyMatch(message -> message.contains("Identity"));
        verify(cars, never()).save(any());
    }

}
