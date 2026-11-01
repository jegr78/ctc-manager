package org.ctc.gt7sync;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.CarCategory;
import org.ctc.domain.model.Drivetrain;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.service.CarMetadataService;
import org.ctc.domain.service.CarService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@CtcDevSpringBootContext
@Tag("integration")
class Gt7MetadataFreshStateIT {
    @Autowired private Gt7SyncService syncService;
    @Autowired private CarMetadataService metadataService;
    @Autowired private CarService carService;
    @Autowired private CarRepository cars;
    @Autowired private PlatformTransactionManager transactionManager;
    @MockitoBean private Gt7ScraperService scraper;
    @MockitoBean private GtPlusCarYearSource years;

    @Test
    void givenCachedCarAndConcurrentManualChange_whenExecutingPreview_thenLockedReadRejectsStaleSnapshot() throws Exception {
        // given
        var outer = new TransactionTemplate(transactionManager);
        var writer = new TransactionTemplate(transactionManager);
        writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String manufacturer = "Test_FreshMetadata_" + UUID.randomUUID();
        UUID id = outer.execute(status -> {
            var car = new Car(manufacturer, "Test Car '20");
            car.setGt7Id("car999996");
            car.setImageUrl("/uploads/test-fresh-original.png");
            return cars.saveAndFlush(car).getId();
        });
        when(scraper.scrapeCars()).thenReturn(List.of(new Gt7ScraperService.ScrapedCar(
                "car999996", manufacturer, "Test Car '20", "https://example.test/changed.png",
                CarCategory.GR_3, Drivetrain.FR, "Gr.3", "FR")));
        when(scraper.scrapeTracks()).thenReturn(List.of());
        when(years.fetchCandidates()).thenReturn(Map.of("car999996",
                new GtPlusCarYearSource.YearCandidate(2020, "GTPlus code 999996 year 2020", null)));
        try {
            outer.executeWithoutResult(status -> {
                try {
                    var cached = cars.findById(id).orElseThrow();
                    var preview = syncService.fetchAndPreview();
                    writer.executeWithoutResult(writerStatus -> {
                        var current = cars.findByIdForUpdate(id).orElseThrow();
                        metadataService.applyManual(current, CarCategory.GR_4, Drivetrain.RR, null, true, true, true);
                        cars.saveAndFlush(current);
                    });
                    assertThat(cached.getCategory()).as("the persistence context still holds its earlier read").isNull();
                    // when
                    var result = syncService.executeSync(List.of(), List.of(), List.of("car999996"), preview.previewId());
                    // then
                    assertThat(result.carsUpdated()).as("compare the preview with the locked database state").isZero();
                    assertThat(result.errors()).anyMatch(message -> message.contains("changed"));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            outer.executeWithoutResult(status -> {
                var persisted = cars.findById(id).orElseThrow();
                assertThat(persisted.getCategory()).isEqualTo(CarCategory.GR_4);
                assertThat(persisted.isCategoryOverride()).isTrue();
                assertThat(persisted.getDrivetrain()).isEqualTo(Drivetrain.RR);
                assertThat(persisted.isDrivetrainOverride()).isTrue();
                assertThat(persisted.getModelYear()).isNull();
                assertThat(persisted.isModelYearOverride()).isTrue();
                assertThat(persisted.getImageUrl()).isEqualTo("/uploads/test-fresh-original.png");
            });
        } finally {
            outer.executeWithoutResult(status -> cars.deleteById(id));
        }
    }
    @Test
    void givenCachedCarAndConcurrentAcceptedYear_whenSavingManualClear_thenUnknownOverridePersists() {
        // given
        var outer = new TransactionTemplate(transactionManager);
        var writer = new TransactionTemplate(transactionManager);
        writer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String manufacturer = "Test_FreshManual_" + UUID.randomUUID();
        UUID id = outer.execute(status -> {
            var car = new Car(manufacturer, "Test Car '20");
            car.setGt7Id("car999995");
            return cars.saveAndFlush(car).getId();
        });
        try {
            outer.executeWithoutResult(status -> {
                var cached = cars.findById(id).orElseThrow();
                writer.executeWithoutResult(writerStatus -> {
                    var current = cars.findByIdForUpdate(id).orElseThrow();
                    metadataService.applyImported(current, metadataService.propose(current,
                            new Gt7ScraperService.ScrapedCar("car999995", manufacturer, "Test Car '20", ""),
                            new GtPlusCarYearSource.YearCandidate(2020, "GTPlus candidate 2020", null)));
                    cars.saveAndFlush(current);
                });
                assertThat(cached.getModelYear()).as("the earlier entity read is still cached").isNull();
                // when
                var saved = carService.save(id, manufacturer, "Test Car '20",
                        new CarMetadataService.ManualMetadata(CarCategory.GR_4, Drivetrain.RR, null, false, false, false));
                // then
                assertThat(saved.getModelYear()).isNull();
                assertThat(saved.isModelYearOverride()).as("clearing the now-accepted year requires an override").isTrue();
            });
            outer.executeWithoutResult(status -> {
                var persisted = cars.findById(id).orElseThrow();
                assertThat(persisted.getModelYear()).isNull();
                assertThat(persisted.isModelYearOverride()).isTrue();
                assertThat(persisted.getModelYearEvidence()).isEqualTo("Explicitly unknown model year");
                assertThat(persisted.getGt7Id()).isEqualTo("car999995");
            });
        } finally {
            outer.executeWithoutResult(status -> cars.deleteById(id));
        }
    }

}
