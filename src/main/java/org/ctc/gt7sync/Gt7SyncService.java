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
import org.ctc.gt7sync.Gt7SyncPreview.CarEntry;
import org.ctc.gt7sync.Gt7SyncPreview.SyncStatus;
import org.ctc.gt7sync.Gt7SyncPreview.TrackEntry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
		var carsFuture = CompletableFuture.supplyAsync(() -> {
			try {
				return scraperService.scrapeCars();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
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
		var errors = Collections.synchronizedList(new ArrayList<String>());
		int carsImported = 0;
		int tracksImported = 0;

		// Step 1: persist all car entities (fast, DB only).
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

		// Step 2: persist all track entities (fast, DB only).
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

		// Step 3: download images in parallel (IO only), then switch back to the main thread for DB writes.
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
				} catch (IOException e) {
					trackImageResults.add(new ImageResult<>(task, null, e));
				}
			}));
		}

		CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0])).join();

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
				result.task.track.setImageUrl(result.localPath);
				trackRepository.save(result.task.track);
			}
		}

		long durationSeconds = (System.currentTimeMillis() - startTime) / 1000;
		log.info("GT7 sync completed in {}s: {} cars, {} tracks, {} errors",
				durationSeconds, carsImported, tracksImported, errors.size());

		return new SyncResult(carsImported, tracksImported, errors, durationSeconds);
	}

	private record CarImageTask(Car car, String imageUrl, String gt7Id) {
	}

	private record TrackImageTask(Track track, String imageUrl, String trackId) {
	}

	private record ImageResult<T>(T task, String localPath, Exception error) {
	}

	public record SyncResult(int carsImported, int tracksImported, List<String> errors, long durationSeconds) {
	}
}
