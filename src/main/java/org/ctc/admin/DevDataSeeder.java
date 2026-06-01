package org.ctc.admin;

import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.repository.RaceLineupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.sitegen.SiteGeneratorService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

	private final TestDataService testDataService;
	private final SiteGeneratorService siteGeneratorService;
	private final RaceLineupRepository raceLineupRepository;

	@Override
	public void run(String... args) {
		testDataService.seed();
		// The demo profile renders the dev seed verbatim, so the guest example must be present here.
		verifyGuestExample();
		generateSite();
	}

	private void verifyGuestExample() {
		long guestCount = raceLineupRepository.findAll().stream()
				.filter(RaceLineup::isGuest)
				.count();
		if (guestCount == 0) {
			log.warn("dev seed contains no guest lineup — /gsd-auto-uat + visual reference will lack guest data");
		} else {
			log.info("dev seed contains {} guest lineup(s) for /gsd-auto-uat + visual reference", guestCount);
		}
	}

	private void generateSite() {
		try {
			var result = siteGeneratorService.generate();
			log.info("Site generated: {} pages, {} errors", result.getPagesGenerated(), result.getErrors().size());
		} catch (Exception e) {
			log.warn("Site generation skipped: {}", e.getMessage());
		}
	}
}
