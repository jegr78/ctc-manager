package org.ctc.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.gt7sync.Gt7SyncService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds the database with GT7 car and track data on startup.
 * Activate with: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev,demo
 */
@Slf4j
@Component
@Profile("demo")
@Order(2) // run after DevDataSeeder
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

	private final Gt7SyncService syncService;
	private final CarRepository carRepository;
	private final TrackRepository trackRepository;

	@Override
	public void run(String... args) {
		if (carRepository.count() > 0 || trackRepository.count() > 0) {
			log.info("Cars/Tracks already present, skipping GT7 sync");
			return;
		}

		log.info("Demo profile active — importing GT7 cars and tracks...");
		try {
			var preview = syncService.fetchAndPreview();
			var allCarIds = preview.cars().stream()
					.map(c -> c.gt7Id())
					.toList();
			var allTrackNames = preview.tracks().stream()
					.map(t -> t.name())
					.toList();

			var result = syncService.executeSync(allCarIds, allTrackNames);
			log.info("GT7 demo data imported: {} cars, {} tracks in {}s",
					result.carsImported(), result.tracksImported(), result.durationSeconds());
		} catch (Exception e) {
			log.warn("GT7 demo data import failed (continuing without): {}", e.getMessage());
		}
	}
}
