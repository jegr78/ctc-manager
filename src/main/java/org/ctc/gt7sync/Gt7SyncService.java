package org.ctc.gt7sync;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.FileStorageService;
import org.ctc.gt7sync.Gt7SyncPreview.*;
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
        long startTime = System.currentTimeMillis();
        var errors = java.util.Collections.synchronizedList(new ArrayList<String>());
        int carsImported = 0;
        int tracksImported = 0;

        // Phase 1: Create all car entities (fast, DB only)
        var carsToDownload = new ArrayList<CarImageTask>();
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
                carsToDownload.add(new CarImageTask(car, sc.imageUrl(), sc.gt7Id()));
                carsImported++;
            }
        }

        // Phase 2: Create all track entities (fast, DB only)
        var tracksToDownload = new ArrayList<TrackImageTask>();
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
                if (st.imageUrl() != null) {
                    tracksToDownload.add(new TrackImageTask(track, st.imageUrl(), st.id()));
                }
                tracksImported++;
            }
        }

        // Phase 3: Download all images in parallel
        var allFutures = new ArrayList<java.util.concurrent.CompletableFuture<Void>>();

        for (var task : carsToDownload) {
            allFutures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    String localPath = fileStorageService.storeFromUrl("cars", task.car.getId(), task.imageUrl, task.gt7Id + ".png");
                    task.car.setImageUrl(localPath);
                    carRepository.save(task.car);
                } catch (Exception e) {
                    log.warn("Image download failed for car {}: {}", task.gt7Id, e.getMessage());
                    errors.add("Image download failed for " + task.car.getManufacturer() + " " + task.car.getName());
                }
            }));
        }

        for (var task : tracksToDownload) {
            allFutures.add(java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    String localPath = fileStorageService.storeFromUrl("tracks", task.track.getId(), task.imageUrl, task.trackId + ".png");
                    task.track.setImageUrl(localPath);
                    trackRepository.save(task.track);
                } catch (Exception e) {
                    log.warn("Image download failed for track {}: {}", task.track.getName(), e.getMessage());
                    errors.add("Image download failed for " + task.track.getName());
                }
            }));
        }

        java.util.concurrent.CompletableFuture.allOf(allFutures.toArray(new java.util.concurrent.CompletableFuture[0])).join();

        long durationSeconds = (System.currentTimeMillis() - startTime) / 1000;
        log.info("GT7 sync completed in {}s: {} cars, {} tracks, {} errors",
                durationSeconds, carsImported, tracksImported, errors.size());

        return new SyncResult(carsImported, tracksImported, errors, durationSeconds);
    }

    private record CarImageTask(Car car, String imageUrl, String gt7Id) {}
    private record TrackImageTask(Track track, String imageUrl, String trackId) {}

    public record SyncResult(int carsImported, int tracksImported, List<String> errors, long durationSeconds) {}
}
