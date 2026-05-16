package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.ctc.admin.TestDataService;
import org.flywaydb.core.Flyway;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * SC5 regression integration test — covers SC1..SC5 from ROADMAP Phase 62 plus
 * D-22 (empty-state), D-26 (a11y), D-04 (archive cross-link integrity), and D-19
 * (alltime pages existence). Runs as a Surefire IT per D-24 (included in default
 * {@code ./mvnw verify}, not Failsafe).
 *
 * <p>This class is the canonical regression gate for Phase 62. Plans 1-5 each ship
 * per-page-type tests; Plan 6 consolidates the cross-cutting assertions here so
 * that a single test class proves the public site renders correctly across
 * phase/group permutations. The overlap with per-page tests is INTENTIONAL.
 *
 * <p>Fixtures used (all seeded by {@link TestDataService#seed()}):
 * <ul>
 *   <li><strong>Season 2023</strong> (GROUPS REGULAR + PLAYOFF) — slug {@code 2023-1-season-2023}
 *       — covers SC1, SC2, SC3, D-26, multi-phase a11y.</li>
 *   <li><strong>Season 2026</strong> (single REGULAR LEAGUE) — slug {@code 2026-4-regular-season}
 *       — covers SC4 byte-identity against Plan 0 golden baseline.</li>
 *   <li><strong>Season 2024 — Empty Phase</strong> (REGULAR only, zero results) —
 *       slug {@code 2024-3-season-2024-empty-phase} — covers D-22 empty-state banner.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
@Tag("integration")
class SiteGeneratorPhaseAwarenessIT {

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
        // alive across Spring context reloads). Without this, TestDataService.seed() short-circuits
        // because seasonRepository.count() > 0 from preceding tests.
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
     * SC1: GROUPS-layout REGULAR phase generates one HTML file per group plus the legacy
     * combined {@code standings.html}. Combined view shows the Group column; per-group view
     * hides it (D-32).
     *
     * <p>Season 2023 has GROUPS REGULAR with Group A and Group B.
     * Display label "2023 | #1 | Season 2023" → slug "2023-1-season-2023".
     */
    @Test
    void givenGroupsLayoutSeason_whenGenerate_thenPerGroupAndCombinedFilesExist() throws IOException {
        // given
        Path seasonDir = tempDir.resolve("season").resolve("2023-1-season-2023");

        // then — per-group files exist
        assertThat(seasonDir.resolve("standings.html")).exists();
        assertThat(seasonDir.resolve("standings-regular-group-group-a.html")).exists();
        assertThat(seasonDir.resolve("standings-regular-group-group-b.html")).exists();

        // Combined view shows Group column
        Document combined = Jsoup.parse(Files.readString(seasonDir.resolve("standings.html")));
        assertNotNull(combined.selectFirst("th:contains(Group)"),
                "Combined view must show Group column");

        // Per-group view hides Group column (D-32)
        Document groupA = Jsoup.parse(Files.readString(
                seasonDir.resolve("standings-regular-group-group-a.html")));
        assertThat(groupA.selectFirst("th:contains(Group)"))
                .as("Per-group view must NOT show Group column")
                .isNull();
    }

    /**
     * SC2: multi-phase season renders a phase-tab row at the top of {@code standings.html}.
     * Season 2023 has REGULAR + PLAYOFF — two phases → tab row visible with ≥2 tabs.
     */
    @Test
    void givenMultiPhaseSeason_whenGenerate_thenPhaseTabRowVisible() throws IOException {
        // when
        Document doc = Jsoup.parse(Files.readString(
                tempDir.resolve("season").resolve("2023-1-season-2023").resolve("standings.html")));

        // then
        var tabRow = doc.selectFirst("nav.phase-tab-row");
        assertNotNull(tabRow, "Multi-phase standings.html must contain a phase-tab row");
        assertThat(tabRow.attr("role")).isEqualTo("tablist");
        assertThat(doc.select("nav.phase-tab-row a.phase-tab").size())
                .as("Multi-phase 2023 must have at least 2 phase tabs (REGULAR + PLAYOFF)")
                .isGreaterThanOrEqualTo(2);
    }

    /**
     * SC3: the PLAYOFF tab in the phase-tab row points at {@code playoff.html} (D-08).
     * No {@code standings-playoff.html} is ever generated.
     */
    @Test
    void givenMultiPhaseSeason_whenGenerate_thenPlayoffTabLinksToBracket() throws IOException {
        // when
        Document doc = Jsoup.parse(Files.readString(
                tempDir.resolve("season").resolve("2023-1-season-2023").resolve("standings.html")));

        // then
        var playoffTab = doc.select("nav.phase-tab-row a.phase-tab").stream()
                .filter(a -> "Playoff".equalsIgnoreCase(a.text()) || a.attr("href").endsWith("playoff.html"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Phase-tab row must contain a PLAYOFF tab"));
        assertThat(playoffTab.attr("href")).endsWith("playoff.html");

        // D-08 invariant: standings-playoff.html is NEVER generated
        assertThat(tempDir.resolve("season").resolve("2023-1-season-2023")
                .resolve("standings-playoff.html")).doesNotExist();
    }

    /**
     * SC4: the legacy {@code standings.html} for a single-REGULAR-LEAGUE season must be
     * byte-identical to the Plan 0 golden baseline.
     *
     * <p>Season 2026 is a single-REGULAR-LEAGUE season (no PLAYOFF, no GROUPS).
     * Slug: "2026 | #4 | Regular Season" → "2026-4-regular-season".
     */
    @Test
    void givenLeagueOnlySeason_whenGenerate_thenOutputIsByteIdenticalToBaseline() throws IOException {
        // given
        Path baseline = Path.of("src/test/resources/sitegen/baseline/single-league-standings.html");
        Path generated = tempDir.resolve("season").resolve("2026-4-regular-season")
                .resolve("standings.html");

        // then
        assertThat(generated).exists();
        assertThat(Files.readString(generated))
                .as("LEAGUE-only standings.html must be byte-identical to pre-Phase-62 baseline")
                .isEqualTo(Files.readString(baseline));
    }

    /**
     * D-26: phase-tab row carries {@code role="tablist"} on the {@code <nav>}, and each tab
     * anchor carries {@code role="tab"} plus a non-null {@code aria-selected} ("true" | "false").
     * Group sub-tab row carries the same a11y attributes.
     */
    @Test
    void givenMultiPhaseSeason_whenGenerate_thenTabRowHasA11yAttributes() throws IOException {
        // when
        Document doc = Jsoup.parse(Files.readString(
                tempDir.resolve("season").resolve("2023-1-season-2023").resolve("standings.html")));

        // then — phase-tab row a11y
        var tabRow = doc.selectFirst("nav.phase-tab-row");
        assertNotNull(tabRow, "Phase-tab row <nav> must be present");
        assertThat(tabRow.attr("role")).isEqualTo("tablist");
        var firstTab = tabRow.selectFirst("a.phase-tab");
        assertNotNull(firstTab, "At least one .phase-tab anchor must be present");
        assertThat(firstTab.attr("role")).isEqualTo("tab");
        assertThat(firstTab.attr("aria-selected"))
                .as("Each phase tab must have aria-selected (true|false)")
                .isIn("true", "false");

        // Group sub-tab row a11y (D-26)
        var groupRow = doc.selectFirst("nav.group-tab-row");
        assertNotNull(groupRow, "GROUPS-layout REGULAR phase must show group-tab-row");
        assertThat(groupRow.attr("role")).isEqualTo("tablist");
        var combinedTab = groupRow.selectFirst("a.group-tab.active");
        assertNotNull(combinedTab, "Combined sub-tab must be active on the legacy standings.html");
        assertThat(combinedTab.attr("aria-selected")).isEqualTo("true");
    }

    /**
     * SC2 cross-cut: phase-tab-row also appears on {@code matchdays.html} and
     * {@code driver-ranking.html} for multi-phase seasons (D-10 / D-11 consistency).
     */
    @Test
    void givenMultiPhaseSeason_whenGenerate_thenAllPageTypesEmitPhaseTabRow() throws IOException {
        // given
        Path seasonDir = tempDir.resolve("season").resolve("2023-1-season-2023");

        // then — all three list-page types show the phase-tab row
        for (String page : new String[]{"standings.html", "matchdays.html", "driver-ranking.html"}) {
            Document doc = Jsoup.parse(Files.readString(seasonDir.resolve(page)));
            assertNotNull(doc.selectFirst("nav.phase-tab-row"),
                    "Multi-phase season must show phase-tab-row on " + page);
        }
    }

    /**
     * D-22: empty REGULAR phase renders {@code standings.html} with the full PhaseTeam
     * roster at 0 points plus an empty-state banner (heading "No results recorded yet.").
     *
     * <p>Fixture: {@code Season 2024 — Empty Phase} added in Plan 6 Task 0 of this plan.
     * Display label "2024 | #3 | Season 2024 — Empty Phase" → slug "2024-3-season-2024-empty-phase".
     */
    @Test
    void givenEmptyPhaseSeason_whenGenerate_thenEmptyStateBannerAndRosterVisible() throws IOException {
        // given
        Path generated = tempDir.resolve("season").resolve("2024-3-season-2024-empty-phase")
                .resolve("standings.html");

        // then — file exists
        assertThat(generated)
                .as("Empty-phase season's standings.html must be generated per D-22")
                .exists();

        Document doc = Jsoup.parse(Files.readString(generated));

        // Empty-state banner present
        var banner = doc.selectFirst("div.empty-phase-banner");
        assertNotNull(banner, "Empty-state banner must appear when phase has zero results");
        var heading = banner.selectFirst("h2");
        assertNotNull(heading, "Empty-state banner must contain an h2 heading");
        assertThat(heading.text())
                .as("Banner heading per UI-SPEC copywriting contract")
                .isEqualTo("No results recorded yet.");

        // Roster rows present at 0 points (4 teams seeded via PhaseTeam)
        int rowCount = doc.select("tbody tr").size();
        assertThat(rowCount)
                .as("Empty-phase fixture seeds 4 teams — all must appear in standings table")
                .isGreaterThanOrEqualTo(4);
    }

    /**
     * D-04 backward-compat: every {@code season/...} link in {@code archive.html} must
     * resolve to an existing generated file. Broken cross-links would surface as 404s on
     * the GitHub Pages deployment.
     */
    @Test
    void givenGeneratedSite_whenParseArchiveLinks_thenAllSeasonLinksResolveToExistingFiles()
            throws IOException {
        // given
        Path archive = tempDir.resolve("archive.html");
        assertThat(archive).exists();

        // when / then
        Document doc = Jsoup.parse(Files.readString(archive));
        for (var anchor : doc.select("a[href]")) {
            String href = anchor.attr("href");
            if (href.startsWith("season/")) {
                Path resolved = tempDir.resolve(href);
                assertThat(Files.exists(resolved))
                        .withFailMessage("archive.html link '%s' does not resolve to a generated file", href)
                        .isTrue();
            }
        }
    }

    /**
     * D-19 sanity: alltime pages still exist after Phase 62 cross-phase aggregation was
     * switched from REGULAR-only to all-phases (Plan 62-05 behavior change).
     */
    @Test
    void givenGeneratedSite_whenGenerate_thenAlltimePagesExist() {
        // then
        assertThat(tempDir.resolve("alltime-standings.html")).exists();
        assertThat(tempDir.resolve("alltime-driver-ranking.html")).exists();
    }
}
