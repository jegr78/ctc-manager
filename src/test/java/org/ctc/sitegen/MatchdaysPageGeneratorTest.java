package org.ctc.sitegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.ctc.admin.TestDataService;
import org.flywaydb.core.Flyway;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Phase 62 Plan 2 — phase- and group-aware matchdays page tests.
 *
 * <p>Verifies the rewritten {@link MatchdaysPageGenerator#generateIndex} +
 * {@code templates/site/matchdays.html} against:
 * <ul>
 *   <li>SC4 backward-compat for single-REGULAR-LEAGUE seasons (Season 2026 fixture):
 *       legacy matchdays.html has no tab rows.</li>
 *   <li>Open Question 2 lock: legacy {@code matchdays.html} for multi-phase seasons lists
 *       REGULAR-phase matchdays only (no PLAYOFF matchdays).</li>
 *   <li>D-10 per-phase variant {@code matchdays-regular.html}.</li>
 *   <li>D-08: {@code matchdays-playoff.html} is NEVER generated.</li>
 *   <li>D-10 per-group variants {@code matchdays-regular-group-{groupSlug}.html}.</li>
 *   <li>D-26 a11y attributes ({@code role=tablist}, {@code role=tab}, {@code aria-selected}).</li>
 * </ul>
 *
 * <p>Fixtures used (TestDataService):
 * <ul>
 *   <li>Season 2023 (slug {@code 2023-1-season-2023}) — GROUPS REGULAR (Group A / Group B)
 *       + PLAYOFF (matchday "2023 Playoffs", line 933).</li>
 *   <li>Season 2026 (slug {@code 2026-4-regular-season}) — single-REGULAR-LEAGUE.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
class MatchdaysPageGeneratorTest {

    private Path tempDir;

    @Autowired private SiteGeneratorService siteGeneratorService;
    @Autowired private SiteProperties siteProperties;
    @Autowired private TestDataService testDataService;
    @Autowired private DataSource dataSource;

    @MockitoBean private YouTubeScraperService youTubeScraperService;

    @BeforeAll
    void setUp(@TempDir Path injectedTempDir) {
        given(youTubeScraperService.scrapeVideoId(anyString(), anyString()))
                .willReturn("dQw4w9WgXcQ");
        this.tempDir = injectedTempDir;
        siteProperties.setOutputDir(tempDir.toString());

        // Flyway clean+migrate guarantees a fresh DB state regardless of preceding test classes
        // having seeded data into the shared H2 in-memory DB (DB_CLOSE_DELAY=-1 keeps the DB
        // alive across Spring context reloads). See Plan 1 SUMMARY §Deviations §3.
        Flyway.configure()
                .dataSource(dataSource)
                .cleanDisabled(false)
                .locations("classpath:db/migration")
                .load()
                .clean();
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        testDataService.seed();
        try {
            siteGeneratorService.generate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * SC4 backward-compat: single-REGULAR-LEAGUE matchdays.html exists and has no phase-tab row.
     */
    @Test
    void givenLeagueOnlySeason_whenGenerateIndex_thenLegacyMatchdaysHtmlExists() throws IOException {
        Path file = tempDir.resolve("season").resolve("2026-4-regular-season").resolve("matchdays.html");
        assertThat(file).exists();
        assertThat(Files.readString(file)).doesNotContain("phase-tab-row");
    }

    /**
     * Open Question 2 lock contract: for a single-REGULAR-LEAGUE fixture, the legacy
     * matchdays.html count equals {@code findByPhaseIdOrderBySortIndexAsc(regularPhase)} count.
     * For LEAGUE-only seasons this is trivially equal; the test serves to document the contract
     * for multi-phase seasons (see next test).
     */
    @Test
    void givenLeagueOnlySeason_whenGenerateIndex_thenLegacyContainsOnlyRegularPhaseMatchdays() throws IOException {
        Path file = tempDir.resolve("season").resolve("2026-4-regular-season").resolve("matchdays.html");
        Document doc = Jsoup.parse(Files.readString(file));
        // Season 2026 has no PLAYOFF — its matchdays must all appear; PLAYOFF labels must not.
        assertThat(doc.text()).doesNotContain("Playoffs");
    }

    /**
     * Open Question 2 lock: multi-phase legacy matchdays.html lists REGULAR-phase matchdays only.
     * Season 2023 has a PLAYOFF matchday labeled "2023 Playoffs" (TestDataService line 933) —
     * it MUST NOT appear in the legacy index.
     */
    @Test
    void givenMultiPhaseSeason_whenGenerateIndex_thenLegacyContainsOnlyRegularPhaseMatchdays() throws IOException {
        Path file = tempDir.resolve("season").resolve("2023-1-season-2023").resolve("matchdays.html");
        Document doc = Jsoup.parse(Files.readString(file));
        assertThat(doc.text()).doesNotContain("2023 Playoffs");
    }

    /**
     * D-10: per-phase variant {@code matchdays-regular.html} exists for multi-phase seasons.
     * D-08: {@code matchdays-playoff.html} is NEVER generated — PLAYOFF tab links to playoff.html.
     */
    @Test
    void givenMultiPhaseSeason_whenGenerateIndex_thenPerPhaseVariantsExist() {
        Path seasonDir = tempDir.resolve("season").resolve("2023-1-season-2023");
        assertThat(seasonDir.resolve("matchdays-regular.html")).exists();
        assertThat(seasonDir.resolve("matchdays-playoff.html")).doesNotExist();
    }

    /**
     * D-10: per-group variants {@code matchdays-regular-group-{groupSlug}.html} for GROUPS-layout.
     * Season 2023 has Group A / Group B in REGULAR (TestDataService lines 207-208).
     */
    @Test
    void givenGroupsLayoutSeason_whenGenerateIndex_thenPerGroupVariantsExist() {
        Path seasonDir = tempDir.resolve("season").resolve("2023-1-season-2023");
        assertThat(seasonDir.resolve("matchdays-regular-group-group-a.html")).exists();
        assertThat(seasonDir.resolve("matchdays-regular-group-group-b.html")).exists();
    }

    /**
     * D-04: multi-phase seasons render a phase-tab row at the top of matchdays.html with the
     * PLAYOFF tab linking to playoff.html (D-08).
     */
    @Test
    void givenMultiPhaseSeason_whenGenerateIndex_thenPhaseTabRowVisible() throws IOException {
        Document doc = Jsoup.parse(
                Files.readString(tempDir.resolve("season").resolve("2023-1-season-2023").resolve("matchdays.html")));
        var tabRow = doc.selectFirst("nav.phase-tab-row");
        assertNotNull(tabRow, "Multi-phase matchdays.html must contain a phase-tab row");
        assertThat(tabRow.attr("role")).isEqualTo("tablist");
        assertThat(doc.select("nav.phase-tab-row a.phase-tab").size()).isGreaterThanOrEqualTo(2);
        var playoffTab = doc.select("nav.phase-tab-row a.phase-tab").stream()
                .filter(a -> a.attr("href").endsWith("playoff.html"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("PLAYOFF tab href must end with playoff.html (D-08)"));
        assertThat(playoffTab.attr("href")).endsWith("playoff.html");
    }

    /**
     * D-26: phase-tab row carries {@code role="tablist"} on {@code <nav>}, and each tab anchor
     * carries {@code role="tab"} plus a non-null {@code aria-selected} ("true" | "false").
     */
    @Test
    void givenMultiPhaseSeason_whenGenerateIndex_thenTabRowHasA11yAttributes() throws IOException {
        Document doc = Jsoup.parse(
                Files.readString(tempDir.resolve("season").resolve("2023-1-season-2023").resolve("matchdays.html")));
        var firstTab = doc.selectFirst("nav.phase-tab-row a.phase-tab");
        assertNotNull(firstTab, "At least one .phase-tab anchor must be present");
        assertThat(firstTab.attr("role")).isEqualTo("tab");
        assertThat(firstTab.attr("aria-selected")).isIn("true", "false");
    }

    /**
     * SC4 invariant for matchdays page: single-REGULAR-LEAGUE matchdays.html does not contain
     * either tab-row marker substring.
     */
    @Test
    void givenLeagueOnlySeason_whenGenerateIndex_thenNoTabRowAndNoGroupRow() throws IOException {
        String html = Files.readString(
                tempDir.resolve("season").resolve("2026-4-regular-season").resolve("matchdays.html"));
        assertThat(html).doesNotContain("phase-tab-row");
        assertThat(html).doesNotContain("group-tab-row");
    }
}
