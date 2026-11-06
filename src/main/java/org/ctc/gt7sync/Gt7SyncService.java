package org.ctc.gt7sync;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.service.FileStorageService;
import org.ctc.domain.service.RaceSelectionConcurrency;
import org.ctc.gt7sync.Gt7SyncPreview.CarEntry;
import org.ctc.gt7sync.Gt7SyncPreview.SyncStatus;
import org.ctc.gt7sync.Gt7SyncPreview.TrackEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.springframework.util.StringUtils.hasText;

@Slf4j
@Service
@RequiredArgsConstructor
public class Gt7SyncService {

	private final Gt7ScraperService scraperService;
	private final CarRepository carRepository;
	private final TrackRepository trackRepository;
	private final FileStorageService fileStorageService;
    private final org.ctc.domain.service.CarMetadataService metadataService;
    private final GtPlusCarYearSource yearSource;
    private final Gt7SyncPreviewStore previewStore;

	public Gt7SyncPreview fetchAndPreview() throws IOException {
		// Fetch cars and tracks in parallel
		var carsFuture = CompletableFuture.supplyAsync(() -> {
			try {
				return scraperService.scrapeCars();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
		var yearsFuture = CompletableFuture.supplyAsync(this::fetchYears);
		var tracksFuture = CompletableFuture.supplyAsync(() -> {
			try {
				return scraperService.scrapeTracks();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});

		List<Gt7ScraperService.ScrapedCar> scrapedCars;
		List<Gt7ScraperService.ScrapedTrack> scrapedTracks;
		try {
			scrapedCars = carsFuture.join();
			scrapedTracks = tracksFuture.join();
		} catch (CompletionException e) {
			if (e.getCause() instanceof IOException ioe) {
				throw ioe;
			}
			if (e.getCause() instanceof UncheckedIOException uioe) {
				throw uioe.getCause();
			}
			throw new IOException("Failed to fetch GT7 data", e.getCause());
		}

        var yearBatch = yearsFuture.join();
        var hashes = new java.util.LinkedHashMap<String, String>();
        var duplicateIds = duplicateIds(scrapedCars);
        var carEntries = scrapedCars.stream().map(sc -> {
            var match = resolve(sc, false);
            boolean ambiguous = match.ambiguous() || duplicateIds.contains(sc.gt7Id());
            var metadata = metadataService.propose(match.car(), sc, yearBatch.candidates().get(sc.gt7Id()));
            boolean selectable = !ambiguous && match.car() != null && metadataService.changes(match.car(), metadata);
            var status = ambiguous ? SyncStatus.AMBIGUOUS : match.car() == null ? SyncStatus.NEW
                    : !metadata.notices().isEmpty() ? SyncStatus.REVIEW : selectable ? SyncStatus.UPDATE : SyncStatus.EXISTS;
            if (!ambiguous) hashes.put(sc.gt7Id(), metadataService.fingerprint(match.car(), sc, metadata));
            String message = ambiguous ? "Local or source identity is ambiguous; review the GT7 ID before importing."
                    : String.join("; ", metadata.notices());
            return new CarEntry(sc.gt7Id(), sc.manufacturer(), sc.name(), sc.imageUrl(), status, metadata,
                    match.car() != null ? match.car().getId() : null, selectable, message);
        }).sorted(Comparator.comparing(CarEntry::manufacturer).thenComparing(CarEntry::name)).toList();

		var trackEntries = scrapedTracks.stream().map(st -> {
			var existing = trackRepository.findByName(st.name()).orElse(null);
            var status = existing == null ? SyncStatus.NEW
                    : hasText(existing.getImageUrl()) ? SyncStatus.EXISTS : SyncStatus.IMAGE_UPDATE;
			return new TrackEntry(st.id(), st.name(), st.country(), status,
                    existing == null ? null : existing.getId());
		}).sorted(Comparator.comparing(TrackEntry::name)).toList();

		return new Gt7SyncPreview(carEntries, trackEntries, previewStore.capture(hashes), yearBatch.warnings());
	}

	@Transactional(rollbackFor = IOException.class)
	public SyncResult executeSync(List<String> selectedCarGt7Ids, List<String> selectedTrackNames,
                                  List<String> selectedMetadata, java.util.UUID previewId) throws IOException {
		return executeSelection(selectedCarGt7Ids, selectedTrackNames, selectedMetadata, previewId, true, List.of());
    }

    @Transactional(rollbackFor = IOException.class)
    public SyncResult executeSync(List<String> selectedCars, List<String> selectedTracks,
                                  List<String> selectedMetadata, java.util.UUID previewId,
                                  List<java.util.UUID> selectedTrackImages) throws IOException {
        return executeSelection(selectedCars, selectedTracks, selectedMetadata, previewId, true, selectedTrackImages);
    }

    private SyncResult executeSelection(List<String> selectedCarGt7Ids, List<String> selectedTrackNames,
                                        List<String> selectedMetadata, java.util.UUID previewId,
                                        boolean previewRequired, List<java.util.UUID> selectedTrackImages) throws IOException {
		long startTime = System.currentTimeMillis();
		var errors = Collections.synchronizedList(new ArrayList<String>());
		int carsImported = 0;
        int carsUpdated = 0;
		int tracksImported = 0;
        int trackImagesUpdated = 0;

		var carsToDownload = new ArrayList<CarImageTask>();
        var requested = new java.util.HashSet<>(selectedCarGt7Ids);
        requested.addAll(selectedMetadata);
        if (!requested.isEmpty()) {
            var yearsFuture = CompletableFuture.supplyAsync(this::fetchYears);
            var scrapedCars = scraperService.scrapeCars();
            var yearBatch = yearsFuture.join();
            errors.addAll(yearBatch.warnings());
            var duplicateIds = duplicateIds(scrapedCars);
            var seen = new java.util.HashSet<String>();
            for (var sc : scrapedCars.stream().filter(c -> requested.contains(c.gt7Id()))
                    .sorted(Comparator.comparing(Gt7ScraperService.ScrapedCar::gt7Id)).toList()) {
                if (!seen.add(sc.gt7Id())) continue;
                var match = resolve(sc, true);
                if (match.ambiguous() || duplicateIds.contains(sc.gt7Id())) {
                    errors.add("Identity requires review for " + sc.gt7Id());
                    continue;
                }
                if (match.car() != null && !selectedMetadata.contains(sc.gt7Id())) continue;
                if (match.car() == null && !selectedCarGt7Ids.contains(sc.gt7Id())) {
                    errors.add("Metadata target changed; review " + sc.gt7Id() + " again.");
                    continue;
                }
                var metadata = metadataService.propose(match.car(), sc, yearBatch.candidates().get(sc.gt7Id()));
                if ((previewRequired || match.car() != null)
                        && !previewStore.matches(previewId, sc.gt7Id(), metadataService.fingerprint(match.car(), sc, metadata))) {
                    errors.add("Car data changed or preview expired; review " + sc.gt7Id() + " again.");
                    continue;
                }
                if (match.car() != null) {
                    if (metadataService.changes(match.car(), metadata)) {
                        metadataService.applyImported(match.car(), metadata);
                        carRepository.save(match.car());
                        carsUpdated++;
                    }
                } else {
                    var car = new Car(sc.manufacturer(), sc.name());
                    car.setGt7Id(sc.gt7Id());
                    metadataService.applyImported(car, metadata);
                    car = carRepository.save(car);
                    carsToDownload.add(new CarImageTask(car, sc.imageUrl(), sc.gt7Id()));
                    carsImported++;
                }
            }
            requested.removeAll(seen);
            requested.forEach(id -> errors.add("Unknown car selection; review " + id + " again."));
        }

		var tracksToDownload = new ArrayList<TrackImageTask>();
		if (!selectedTrackNames.isEmpty() || !selectedTrackImages.isEmpty()) {
			var scrapedTracks = scraperService.scrapeTracks(true);
            var byName = scrapedTracks.stream().collect(java.util.stream.Collectors.groupingBy(Gt7ScraperService.ScrapedTrack::name));
            for (var name : new java.util.TreeSet<>(selectedTrackNames)) {
                var matches = byName.getOrDefault(name, List.of());
                if (matches.size() != 1) {
                    errors.add("Track source is missing or ambiguous for " + name);
                    continue;
                }
                var st = matches.getFirst();
				if (trackRepository.existsByName(st.name())) {
					continue;
				}
				var track = new Track(st.name(), st.country());
				track = trackRepository.save(track);
                queueTrackImage(track, st, false, tracksToDownload, errors);
				tracksImported++;
			}
            for (var id : new java.util.TreeSet<>(selectedTrackImages)) {
                var track = trackRepository.findById(id).orElse(null);
                if (track == null) {
                    errors.add("Track image target no longer exists; compare the catalog again.");
                    continue;
                }
                if (hasText(track.getImageUrl())) continue;
                var matches = byName.getOrDefault(track.getName(), List.of());
                if (matches.size() != 1) {
                    errors.add("Track source is missing or ambiguous for " + track.getName());
                    continue;
                }
                queueTrackImage(track, matches.getFirst(), true, tracksToDownload, errors);
            }
		}

		// Download images without the persistence context, then update entities on the transaction thread.
		var carImageResults = Collections.synchronizedList(new ArrayList<ImageResult<CarImageTask>>());
		var trackImageResults = Collections.synchronizedList(new ArrayList<ImageResult<TrackImageTask>>());

		var allFutures = new ArrayList<CompletableFuture<Void>>();

		for (var task : carsToDownload) {
			allFutures.add(CompletableFuture.runAsync(() -> {
				try {
					String localPath = fileStorageService.storeFromUrl("cars", task.car.getId(), task.imageUrl, task.gt7Id + ".png");
					carImageResults.add(new ImageResult<>(task, localPath, null));
				} catch (IOException e) {
					carImageResults.add(new ImageResult<>(task, null, e));
				}
			}));
		}

		for (var task : tracksToDownload) {
			allFutures.add(CompletableFuture.runAsync(() -> {
				try {
					String localPath = fileStorageService.storeFromUrl("tracks", task.track.getId(), task.imageUrl, task.trackId + ".png");
					trackImageResults.add(new ImageResult<>(task, localPath, null));
				} catch (IOException | IllegalArgumentException e) {
					trackImageResults.add(new ImageResult<>(task, null, e));
				}
			}));
		}

        try {
		    CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0])).join();
        } finally {
            trackImageResults.stream().filter(result -> result.error == null)
                    .forEach(result -> cleanupTrackImageOnRollback(result.localPath));
        }

		// Update entities on main thread (within transaction)
		for (var result : carImageResults) {
			if (result.error != null) {
				log.warn("Image download failed for car {}: {}", result.task.gt7Id, result.error.getMessage());
				errors.add("Image download failed for " + result.task.car.getManufacturer() + " " + result.task.car.getName());
			} else {
				result.task.car.setImageUrl(result.localPath);
				carRepository.save(result.task.car);
			}
		}

		for (var result : trackImageResults) {
			if (result.error != null) {
				log.warn("Image download failed for track {}: {}", result.task.track.getName(), result.error.getMessage());
				errors.add("Image download failed for " + result.task.track.getName());
			} else {
                if (result.task.existing) {
                    int updated;
                    try {
                        updated = trackRepository.fillMissingImage(result.task.track.getId(), result.task.track.getName(),
                                result.localPath, java.time.LocalDateTime.now());
                    } catch (RuntimeException failure) {
                        throw RaceSelectionConcurrency.translate(failure,
                                "Catalog changed while importing track images. Fetch and compare again.");
                    }
                    if (updated == 1) {
                        trackImagesUpdated++;
                    } else {
                        fileStorageService.delete(result.localPath);
                        errors.add("Track image target changed; compare again for " + result.task.track.getName());
                    }
                } else {
				    result.task.track.setImageUrl(result.localPath);
				    trackRepository.save(result.task.track);
                }
			}
		}

		long durationSeconds = (System.currentTimeMillis() - startTime) / 1000;
		log.info("GT7 sync completed in {}s: {} cars, {} tracks, {} errors",
				durationSeconds, carsImported, tracksImported, errors.size());

		return new SyncResult(carsImported, tracksImported, List.copyOf(errors), durationSeconds, carsUpdated, trackImagesUpdated);
	}

    @Transactional(rollbackFor = IOException.class)
    public SyncResult executeSync(List<String> selectedCars, List<String> selectedTracks) throws IOException {
        return executeSelection(selectedCars, selectedTracks, List.of(), null, false, List.of());
    }

    private void queueTrackImage(Track track, Gt7ScraperService.ScrapedTrack source, boolean existing,
                                 List<TrackImageTask> tasks, List<String> errors) {
        if (hasText(source.imageUrl())) tasks.add(new TrackImageTask(track, source.imageUrl(), source.id(), existing));
        else errors.add("Image unavailable for " + track.getName());
    }

    private void cleanupTrackImageOnRollback(String url) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) fileStorageService.delete(url);
                }
            });
        }
    }

    private YearBatch fetchYears() {
        try {
            return new YearBatch(java.util.Map.copyOf(yearSource.fetchCandidates()), List.of());
        } catch (IOException | RuntimeException e) {
            log.warn("GTPlus model-year enrichment unavailable: {}", e.getClass().getSimpleName());
            return new YearBatch(java.util.Map.of(), List.of("Model year enrichment unavailable; official metadata can still be imported."));
        }
    }

    private CarMatch resolve(Gt7ScraperService.ScrapedCar source, boolean lock) {
        var byId = lock ? carRepository.findAllByGt7IdForUpdate(source.gt7Id()) : carRepository.findAllByGt7Id(source.gt7Id());
        if (byId.size() == 1) return new CarMatch(byId.getFirst(), false);
        if (byId.size() > 1) return new CarMatch(null, true);
        var byName = carRepository.findAllByManufacturerAndName(source.manufacturer(), source.name());
        return new CarMatch(byName.size() == 1 ? byName.getFirst() : null, !byName.isEmpty());
    }

    private java.util.Set<String> duplicateIds(List<Gt7ScraperService.ScrapedCar> cars) {
        var seen = new java.util.HashSet<String>();
        var duplicates = new java.util.HashSet<String>();
        for (var car : cars) if (!seen.add(car.gt7Id())) duplicates.add(car.gt7Id());
        return duplicates;
    }

    private record CarMatch(Car car, boolean ambiguous) {}
    private record YearBatch(java.util.Map<String, GtPlusCarYearSource.YearCandidate> candidates, List<String> warnings) {}

	private record CarImageTask(Car car, String imageUrl, String gt7Id) {
	}

	private record TrackImageTask(Track track, String imageUrl, String trackId, boolean existing) {
	}

	private record ImageResult<T>(T task, String localPath, Exception error) {
	}

	public record SyncResult(int carsImported, int tracksImported, List<String> errors, long durationSeconds, int carsUpdated,
                             int trackImagesUpdated) {
        public SyncResult(int carsImported, int tracksImported, List<String> errors, long durationSeconds) {
            this(carsImported, tracksImported, errors, durationSeconds, 0, 0);
        }
        public SyncResult(int carsImported, int tracksImported, List<String> errors, long durationSeconds, int carsUpdated) {
            this(carsImported, tracksImported, errors, durationSeconds, carsUpdated, 0);
        }
	}
}
