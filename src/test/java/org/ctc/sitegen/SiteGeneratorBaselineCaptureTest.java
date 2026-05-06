package org.ctc.sitegen;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.ctc.admin.TestDataService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Manual-only baseline capture for the SC4 byte-identity assertion in Plan 1 / Plan 6.
 *
 * <p>Run ONCE (with @Disabled removed) to refresh
 * {@code src/test/resources/sitegen/baseline/single-league-standings.html}. Re-add @Disabled
 * before commit so this class does NOT execute in the normal {@code ./mvnw verify} cycle.
 *
 * <p>The baseline captured here MUST be aligned with the same fixture context that downstream
 * byte-identity tests use ({@code testDataService.seed()} under
 * {@code @SpringBootTest @ActiveProfiles("dev")}).
 *
 * <p>Target fixture: Season 2026 (year=2026, number=4, name="Regular Season") — single REGULAR
 * phase with PhaseLayout.LEAGUE and SeasonFormat.LEAGUE, no GROUPS, no PLAYOFF. Display label
 * resolves to {@code "2026 | #4 | Regular Season"}; slug resolves to
 * {@code "2026-4-regular-season"}.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Disabled("Run manually to refresh SC4 baseline. See Phase 62 Plan 0 docs.")
class SiteGeneratorBaselineCaptureTest {

    @Autowired private SiteGeneratorService siteGeneratorService;
    @Autowired private SiteProperties siteProperties;
    @Autowired private TestDataService testDataService;

    @MockitoBean private YouTubeScraperService youTubeScraperService;

    @Test
    void captureLeagueOnlyBaseline(@TempDir Path outputDir) throws IOException {
        given(youTubeScraperService.scrapeVideoId(anyString(), anyString()))
                .willReturn("dQw4w9WgXcQ");
        siteProperties.setOutputDir(outputDir.toString());
        testDataService.seed();
        siteGeneratorService.generate();

        // Season 2026 (year=2026, number=4, name="Regular Season") — single REGULAR LEAGUE phase.
        // Slug derives from displayLabel "2026 | #4 | Regular Season" → "2026-4-regular-season".
        Path generated = outputDir.resolve("season").resolve("2026-4-regular-season").resolve("standings.html");
        Path baseline = Path.of("src/test/resources/sitegen/baseline/single-league-standings.html");
        Files.createDirectories(baseline.getParent());
        Files.copy(generated, baseline, StandardCopyOption.REPLACE_EXISTING);
    }
}
