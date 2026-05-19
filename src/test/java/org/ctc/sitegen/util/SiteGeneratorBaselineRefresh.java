package org.ctc.sitegen.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.TestDataService;
import org.ctc.sitegen.SiteGeneratorService;
import org.ctc.sitegen.SiteProperties;
import org.ctc.sitegen.YouTubeScraperService;
import org.mockito.Mockito;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Refreshes the SC4 byte-identity baselines under {@code src/test/resources/sitegen/baseline/}.
 * Active only under the {@code baseline-refresh} Spring profile.
 *
 * <p>Invocation (test-classpath required because this bean lives under {@code src/test/java}):
 *
 * <pre>{@code
 * ./mvnw test-compile exec:java \
 *     -Dexec.mainClass=org.ctc.CtcManagerApplication \
 *     -Dexec.classpathScope=test \
 *     -Dspring.profiles.active=dev,baseline-refresh
 * }</pre>
 *
 * <p>Rewrites three files in place:
 * {@code single-league-standings.html},
 * {@code single-league-team-profile.html},
 * {@code single-league-driver-profile.html}.
 *
 * <p>Target fixture: {@link TestDataService#seed()} produces Season 2026 (year=2026, number=4,
 * "Regular Season"), single REGULAR phase, LEAGUE layout, LEAGUE format; slug resolves to
 * {@code 2026-4-regular-season}.
 */
@Slf4j
@Component
@Profile("baseline-refresh")
@RequiredArgsConstructor
public class SiteGeneratorBaselineRefresh implements CommandLineRunner {

    private static final Path BASELINE_DIR = Path.of("src/test/resources/sitegen/baseline");
    private static final String SEASON_SLUG = "2026-4-regular-season";

    private final SiteGeneratorService siteGeneratorService;
    private final SiteProperties siteProperties;
    private final TestDataService testDataService;
    private final ApplicationContext applicationContext;

    @Override
    public void run(String... args) throws Exception {
        Path tempDir = Files.createTempDirectory("baseline-refresh-");
        log.info("Refreshing SC4 baselines into tempDir={} and copying to {}", tempDir, BASELINE_DIR);

        siteProperties.setOutputDir(tempDir.toString());
        testDataService.seed();
        siteGeneratorService.generate();

        Path seasonDir = tempDir.resolve("season").resolve(SEASON_SLUG);
        Files.createDirectories(BASELINE_DIR);

        copyBaseline(seasonDir.resolve("standings.html"), BASELINE_DIR.resolve("single-league-standings.html"));
        copyBaseline(seasonDir.resolve("team").resolve("adr.html"), BASELINE_DIR.resolve("single-league-team-profile.html"));
        copyBaseline(seasonDir.resolve("driver").resolve("adr-driver01.html"), BASELINE_DIR.resolve("single-league-driver-profile.html"));

        log.info("Baseline refresh complete — 3 files updated under {}", BASELINE_DIR);
        SpringApplication.exit(applicationContext, () -> 0);
    }

    private void copyBaseline(Path source, Path target) throws java.io.IOException {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        log.info("  {} -> {}", source.getFileName(), target);
    }

    @TestConfiguration
    @Profile("baseline-refresh")
    static class YouTubeScraperMockConfig {

        @Bean
        YouTubeScraperService youTubeScraperService() {
            YouTubeScraperService mock = Mockito.mock(YouTubeScraperService.class);
            Mockito.when(mock.scrapeVideoId(Mockito.anyString(), Mockito.anyString())).thenReturn("dQw4w9WgXcQ");
            return mock;
        }
    }
}
