package org.ctc.gt7sync;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.FileStorageService;
import org.ctc.gt7sync.Gt7ScraperService.*;
import org.ctc.gt7sync.Gt7SyncPreview.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class Gt7SyncServiceTest {

    @Mock Gt7ScraperService scraperService;
    @Mock CarRepository carRepository;
    @Mock TrackRepository trackRepository;
    @Mock FileStorageService fileStorageService;
    @InjectMocks Gt7SyncService syncService;

    private static final List<ScrapedCar> SCRAPED_CARS = List.of(
            new ScrapedCar("car102", "Nissan", "Skyline GTS-R (R31) '87", "https://example.com/car102.png"),
            new ScrapedCar("car205", "Toyota", "Sports 800 '65", "https://example.com/car205.png"),
            new ScrapedCar("car310", "Alfa Romeo", "4C Gr.3", "https://example.com/car310.png")
    );

    private static final List<ScrapedTrack> SCRAPED_TRACKS = List.of(
            new ScrapedTrack("0457d4", "Deep Forest Raceway", "Switzerland", "c81494", "https://example.com/track1.png"),
            new ScrapedTrack("12ceac", "Nurburgring Nordschleife", "Germany", "246f6d", "https://example.com/track2.png")
    );

    @Test
    void shouldPreviewWithNewAndExistingCars() throws IOException {
        when(scraperService.scrapeCars()).thenReturn(SCRAPED_CARS);
        when(scraperService.scrapeTracks()).thenReturn(SCRAPED_TRACKS);

        // car102 exists by gt7Id, car205 exists by manufacturer+name, car310 is new
        when(carRepository.existsByGt7Id("car102")).thenReturn(true);
        when(carRepository.existsByGt7Id("car205")).thenReturn(false);
        when(carRepository.existsByManufacturerAndName("Toyota", "Sports 800 '65")).thenReturn(true);
        when(carRepository.existsByGt7Id("car310")).thenReturn(false);
        when(carRepository.existsByManufacturerAndName("Alfa Romeo", "4C Gr.3")).thenReturn(false);

        when(trackRepository.existsByName("Deep Forest Raceway")).thenReturn(true);
        when(trackRepository.existsByName("Nurburgring Nordschleife")).thenReturn(false);

        Gt7SyncPreview preview = syncService.fetchAndPreview();

        assertThat(preview.getNewCarCount()).isEqualTo(1);
        assertThat(preview.getExistingCarCount()).isEqualTo(2);
        assertThat(preview.getNewTrackCount()).isEqualTo(1);
        assertThat(preview.getExistingTrackCount()).isEqualTo(1);

        var alfaEntry = preview.getCars().stream()
                .filter(c -> c.gt7Id().equals("car310")).findFirst().orElseThrow();
        assertThat(alfaEntry.status()).isEqualTo(SyncStatus.NEW);

        var nissanEntry = preview.getCars().stream()
                .filter(c -> c.gt7Id().equals("car102")).findFirst().orElseThrow();
        assertThat(nissanEntry.status()).isEqualTo(SyncStatus.EXISTS);
    }

    @Test
    void shouldPrioritizeGt7IdMatchOverNameMatch() throws IOException {
        when(scraperService.scrapeCars()).thenReturn(SCRAPED_CARS);
        when(scraperService.scrapeTracks()).thenReturn(List.of());

        // car102 exists by gt7Id — should not check manufacturer+name
        when(carRepository.existsByGt7Id("car102")).thenReturn(true);
        when(carRepository.existsByGt7Id("car205")).thenReturn(false);
        when(carRepository.existsByManufacturerAndName("Toyota", "Sports 800 '65")).thenReturn(false);
        when(carRepository.existsByGt7Id("car310")).thenReturn(false);
        when(carRepository.existsByManufacturerAndName("Alfa Romeo", "4C Gr.3")).thenReturn(false);

        Gt7SyncPreview preview = syncService.fetchAndPreview();

        var nissanEntry = preview.getCars().stream()
                .filter(c -> c.gt7Id().equals("car102")).findFirst().orElseThrow();
        assertThat(nissanEntry.status()).isEqualTo(SyncStatus.EXISTS);

        // Toyota and Alfa are new
        assertThat(preview.getNewCarCount()).isEqualTo(2);
    }

    @Test
    void shouldImportOnlySelectedCars() throws IOException {
        when(scraperService.scrapeCars()).thenReturn(SCRAPED_CARS);

        when(carRepository.existsByGt7Id(anyString())).thenReturn(false);
        when(carRepository.existsByManufacturerAndName(anyString(), anyString())).thenReturn(false);

        var savedCar = new Car("Alfa Romeo", "4C Gr.3");
        savedCar.setGt7Id("car310");
        var id = UUID.randomUUID();
        savedCar.setId(id);
        when(carRepository.save(any(Car.class))).thenReturn(savedCar);
        when(fileStorageService.storeFromUrl(eq("cars"), eq(id), anyString(), eq("car310.png")))
                .thenReturn("/uploads/cars/" + id + "/car310.png");

        // Only select car310
        var result = syncService.executeSync(List.of("car310"), List.of());

        assertThat(result.carsImported()).isEqualTo(1);
        assertThat(result.tracksImported()).isEqualTo(0);
        verify(carRepository, times(2)).save(any(Car.class)); // save + save with imageUrl
    }

    @Test
    void shouldImportOnlySelectedTracks() throws IOException {
        when(scraperService.scrapeTracks(true)).thenReturn(SCRAPED_TRACKS);
        when(trackRepository.existsByName(anyString())).thenReturn(false);
        var savedTrack = new Track("Deep Forest Raceway", "Switzerland");
        savedTrack.setId(UUID.randomUUID());
        when(trackRepository.save(any(Track.class))).thenReturn(savedTrack);
        when(fileStorageService.storeFromUrl(eq("tracks"), any(UUID.class), anyString(), anyString()))
                .thenReturn("/uploads/tracks/img.png");

        var result = syncService.executeSync(List.of(), List.of("Deep Forest Raceway"));

        assertThat(result.tracksImported()).isEqualTo(1);
        assertThat(result.carsImported()).isEqualTo(0);
        verify(trackRepository, times(2)).save(any(Track.class)); // save + save with imageUrl
    }

    @Test
    void shouldSkipAlreadyExistingDuringExecute() throws IOException {
        when(scraperService.scrapeCars()).thenReturn(SCRAPED_CARS);
        when(carRepository.existsByGt7Id("car102")).thenReturn(true);

        var result = syncService.executeSync(List.of("car102"), List.of());

        assertThat(result.carsImported()).isEqualTo(0);
        verify(carRepository, never()).save(any(Car.class));
    }
}
