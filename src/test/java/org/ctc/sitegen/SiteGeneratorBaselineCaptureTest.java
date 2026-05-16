package org.ctc.sitegen;

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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

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
@Disabled("Run manually to refresh SC4 baselines. See Phase 62 Plan 0 / Plan 4 docs.")
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

    /**
     * Phase 62 Plan 4 — capture pre-Phase-62 team-profile and driver-profile baselines for the
     * SC4 byte-identity invariant. Run ONCE (with @Disabled removed) BEFORE the Plan 4 template
     * rewrite to lock the legacy markup of a single-REGULAR-LEAGUE team-profile and driver-profile.
     *
     * <p>Baseline candidates (from TestDataService Season 2026, single REGULAR LEAGUE phase):
     * <ul>
     *   <li>Team: ADR (parent team participating in Season 2026)</li>
     *   <li>Driver: ADR_Driver01 (PSN slug: {@code adr-driver01})</li>
     * </ul>
     */
    @Test
    void captureLeagueOnlyTeamAndDriverProfileBaselines(@TempDir Path outputDir) throws IOException {
        given(youTubeScraperService.scrapeVideoId(anyString(), anyString()))
                .willReturn("dQw4w9WgXcQ");
        siteProperties.setOutputDir(outputDir.toString());
        testDataService.seed();
        siteGeneratorService.generate();

        Path seasonDir = outputDir.resolve("season").resolve("2026-4-regular-season");
        Path generatedTeam = seasonDir.resolve("team").resolve("adr.html");
        Path generatedDriver = seasonDir.resolve("driver").resolve("adr-driver01.html");

        Path teamBaseline = Path.of("src/test/resources/sitegen/baseline/single-league-team-profile.html");
        Path driverBaseline = Path.of("src/test/resources/sitegen/baseline/single-league-driver-profile.html");
        Files.createDirectories(teamBaseline.getParent());
        Files.copy(generatedTeam, teamBaseline, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(generatedDriver, driverBaseline, StandardCopyOption.REPLACE_EXISTING);
    }
}
