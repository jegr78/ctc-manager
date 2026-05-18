package org.ctc.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.sitegen.SiteGeneratorService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile({"dev", "local"})
@RequiredArgsConstructor
public class DevDataSeeder implements CommandLineRunner {

	private final TestDataService testDataService;
	private final SiteGeneratorService siteGeneratorService;

	@Override
	public void run(String... args) {
		testDataService.seed();
		generateSite();
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
