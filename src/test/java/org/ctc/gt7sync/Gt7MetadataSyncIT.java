package org.ctc.gt7sync;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import org.ctc.TestHelper;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.CarCategory;
import org.ctc.domain.model.Drivetrain;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class Gt7MetadataSyncIT {
    @Autowired private TestHelper testHelper;
    @Autowired private Gt7SyncService syncService;
    @Autowired private CarRepository cars;
    @Autowired private RaceRepository races;
    @Autowired private SeasonRepository seasons;
    @Autowired private EntityManager entityManager;
    @MockitoBean private Gt7ScraperService scraper;
    @MockitoBean private GtPlusCarYearSource years;

    @Test
    void givenCarUsedInSeasonAndRace_whenApplyingMetadata_thenStoredRelationshipsAndImageSurvive() throws Exception {
        // given
        var fixture = testHelper.createFullSeasonFixture("Test_MetadataSync");
        var car = new Car("Test_MetadataSync", "Test Car '20");
        car.setGt7Id("car999999");
        car.setImageUrl("/uploads/test-metadata-original.png");
        car = cars.save(car);
        fixture.race().setCar(car);
        races.save(fixture.race());
        fixture.season().getCars().add(car);
        seasons.save(fixture.season());
        entityManager.flush();
        var carId = car.getId();
        var raceId = fixture.race().getId();
        var seasonId = fixture.season().getId();
        when(scraper.scrapeCars()).thenReturn(List.of(new Gt7ScraperService.ScrapedCar(
                "car999999", "Test_MetadataSync", "Test Car '20", "https://example.test/changed.png",
                CarCategory.GR_3, Drivetrain.FR, "Gr.3", "FR")));
        when(scraper.scrapeTracks()).thenReturn(List.of());
        when(years.fetchCandidates()).thenReturn(Map.of("car999999",
                new GtPlusCarYearSource.YearCandidate(2020, "GTPlus code 999999 year 2020", null)));
        var preview = syncService.fetchAndPreview();
        // when
        var result = syncService.executeSync(List.of(), List.of(), List.of("car999999"), preview.previewId());
        entityManager.flush();
        entityManager.clear();
        // then
        assertThat(result.carsUpdated()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        var updated = cars.findById(carId).orElseThrow();
        assertThat(updated.getCategory()).isEqualTo(CarCategory.GR_3);
        assertThat(updated.getDrivetrain()).isEqualTo(Drivetrain.FR);
        assertThat(updated.getModelYear()).isEqualTo(2020);
        assertThat(updated.getImageUrl()).isEqualTo("/uploads/test-metadata-original.png");
        assertThat(races.findById(raceId).orElseThrow().getCar().getId()).isEqualTo(carId);
        assertThat(seasons.findById(seasonId).orElseThrow().getCars()).extracting(Car::getId).contains(carId);
    }
}
