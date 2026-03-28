package de.ctc.gt7sync;

import de.ctc.domain.model.*;
import de.ctc.domain.repository.*;
import de.ctc.domain.service.FileStorageService;
import de.ctc.gt7sync.Gt7SyncPreview.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class Gt7SyncService {

    private final Gt7ScraperService scraperService;
    private final CarRepository carRepository;
    private final TrackRepository trackRepository;
    private final FileStorageService fileStorageService;

    public Gt7SyncPreview fetchAndPreview() throws IOException {
        // Fetch cars and tracks in parallel
        var carsFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try { return scraperService.scrapeCars(); }
            catch (IOException e) { throw new java.io.UncheckedIOException(e); }
        });
        var tracksFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            try { return scraperService.scrapeTracks(); }
            catch (IOException e) { throw new java.io.UncheckedIOException(e); }
        });

        List<Gt7ScraperService.ScrapedCar> scrapedCars;
        List<Gt7ScraperService.ScrapedTrack> scrapedTracks;
        try {
            scrapedCars = carsFuture.join();
            scrapedTracks = tracksFuture.join();
        } catch (java.util.concurrent.CompletionException e) {
            if (e.getCause() instanceof IOException ioe) throw ioe;
            if (e.getCause() instanceof java.io.UncheckedIOException uioe) throw uioe.getCause();
            throw new IOException("Failed to fetch GT7 data", e.getCause());
        }

        var carEntries = scrapedCars.stream().map(sc -> {
            boolean exists = carRepository.existsByGt7Id(sc.gt7Id())
                    || carRepository.existsByManufacturerAndName(sc.manufacturer(), sc.name());
            return new CarEntry(sc.gt7Id(), sc.manufacturer(), sc.name(), sc.imageUrl(),
                    exists ? SyncStatus.EXISTS : SyncStatus.NEW);
        }).sorted(Comparator.comparing(CarEntry::manufacturer).thenComparing(CarEntry::name)).toList();

        var trackEntries = scrapedTracks.stream().map(st -> {
            boolean exists = trackRepository.existsByName(st.name());
            return new TrackEntry(st.id(), st.name(), st.country(),
                    exists ? SyncStatus.EXISTS : SyncStatus.NEW);
        }).sorted(Comparator.comparing(TrackEntry::name)).toList();

        return new Gt7SyncPreview(carEntries, trackEntries);
    }

    @Transactional
    public SyncResult executeSync(List<String> selectedCarGt7Ids, List<String> selectedTrackNames) throws IOException {
        var errors = new ArrayList<String>();
        int carsImported = 0;
        int tracksImported = 0;

        if (!selectedCarGt7Ids.isEmpty()) {
            var scrapedCars = scraperService.scrapeCars();
            var selectedCars = scrapedCars.stream()
                    .filter(sc -> selectedCarGt7Ids.contains(sc.gt7Id()))
                    .toList();

            for (var sc : selectedCars) {
                if (carRepository.existsByGt7Id(sc.gt7Id())
                        || carRepository.existsByManufacturerAndName(sc.manufacturer(), sc.name())) {
                    continue;
                }
                var car = new Car(sc.manufacturer(), sc.name());
                car.setGt7Id(sc.gt7Id());
                car = carRepository.save(car);

                try {
                    String localPath = fileStorageService.storeFromUrl("cars", car.getId(), sc.imageUrl(), sc.gt7Id() + ".png");
                    car.setImageUrl(localPath);
                    carRepository.save(car);
                } catch (Exception e) {
                    log.warn("Failed to download image for car {}: {}", sc.gt7Id(), e.getMessage());
                    errors.add("Image download failed for " + sc.manufacturer() + " " + sc.name());
                }
                carsImported++;
            }
        }

        if (!selectedTrackNames.isEmpty()) {
            var scrapedTracks = scraperService.scrapeTracks(true);
            var selectedTracks = scrapedTracks.stream()
                    .filter(st -> selectedTrackNames.contains(st.name()))
                    .toList();

            for (var st : selectedTracks) {
                if (trackRepository.existsByName(st.name())) {
                    continue;
                }
                var track = new Track(st.name(), st.country());
                track = trackRepository.save(track);

                // Download image if available
                if (st.imageUrl() != null) {
                    try {
                        String localPath = fileStorageService.storeFromUrl("tracks", track.getId(),
                                st.imageUrl(), st.id() + ".png");
                        track.setImageUrl(localPath);
                        trackRepository.save(track);
                    } catch (Exception e) {
                        log.warn("Failed to download image for track {}: {}", st.name(), e.getMessage());
                        errors.add("Image download failed for " + st.name());
                    }
                }
                tracksImported++;
            }
        }

        return new SyncResult(carsImported, tracksImported, errors);
    }

    public record SyncResult(int carsImported, int tracksImported, List<String> errors) {}
}
