package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.ctc.admin.TestDataService;
import org.flywaydb.core.Flyway;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * Phase 62 Plan 1 — phase- and group-aware standings page tests.
 *
 * <p>Verifies the rewritten {@link StandingsPageGenerator} + {@code templates/site/standings.html}
 * against:
 * <ul>
 *   <li>SC4 byte-identity for single-REGULAR-LEAGUE seasons (Season 2026 fixture).</li>
 *   <li>SC1 GROUPS-layout per-group + combined files (Season 2023 fixture: Group A / Group B).</li>
 *   <li>SC2/SC3 multi-phase phase-tab row with PLAYOFF tab linking to {@code playoff.html} (D-08).</li>
 *   <li>D-26 a11y attributes ({@code role=tablist}, {@code role=tab}, {@code aria-selected}).</li>
 *   <li>D-22 / D-32 column flags ({@code showGroupColumn}, {@code showBuchholz}).</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class StandingsPageGeneratorTest {

    static final Path tempDir = SitegenTestDir.create("standings");

    @DynamicPropertySource
    static void siteOutputDir(DynamicPropertyRegistry registry) {
        registry.add("ctc.site.output-dir", () -> tempDir.toString());
    }

    @Autowired private SiteGeneratorService siteGeneratorService;
    @Autowired private TestDataService testDataService;
    @Autowired private DataSource dataSource;

    @MockitoBean private YouTubeScraperService youTubeScraperService;

    @BeforeAll
    void setUp() {
        given(youTubeScraperService.scrapeVideoId(anyString(), anyString()))
                .willReturn("dQw4w9WgXcQ");

        // Flyway clean+migrate guarantees a fresh DB state regardless of preceding test classes
        // having seeded data into the shared H2 in-memory DB (DB_CLOSE_DELAY=-1 keeps the DB
        // alive across Spring context reloads). Without this, TestDataService.seed() short-circuits
        // because seasonRepository.count() > 0 from preceding tests, leaving Season 2026 / 2023
        // fixtures absent.
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
     * SC4 byte-identity invariant: the legacy {@code /season/2026-4-regular-season/standings.html}
     * generated for a single-REGULAR-LEAGUE season must equal the Plan 0 golden snapshot.
     *
     * <p>Season 2026 is a single-REGULAR-LEAGUE-format season (no PLAYOFF, no GROUPS). The slug
     * derives from displayLabel "2026 | #4 | Regular Season" → "2026-4-regular-season".
     */
    @Test
    void givenLeagueOnlySeason_whenGenerate_thenOutputIsByteIdenticalToBaseline() throws IOException {
        Path baseline = Path.of("src/test/resources/sitegen/baseline/single-league-standings.html");
        Path generated = tempDir.resolve("season").resolve("2026-4-regular-season").resolve("standings.html");
        assertThat(generated).exists();
        assertThat(Files.readString(generated)).isEqualTo(Files.readString(baseline));
    }

    /**
     * SC1: GROUPS-layout REGULAR phase generates one HTML file per group plus the legacy combined
     * {@code standings.html}. Combined view shows the Group column; per-group view hides it (D-32).
     *
     * <p>Season 2023 has GROUPS REGULAR with Group A and Group B (TestDataService line 207-210).
     * Display label "2023 | #1 | Season 2023" → slug "2023-1-season-2023".
     */
    @Test
    void givenGroupsLayoutSeason_whenGenerate_thenPerGroupAndCombinedFilesExist() throws IOException {
        Path seasonDir = tempDir.resolve("season").resolve("2023-1-season-2023");
        assertThat(seasonDir.resolve("standings.html")).exists();
        assertThat(seasonDir.resolve("standings-regular-group-group-a.html")).exists();
        assertThat(seasonDir.resolve("standings-regular-group-group-b.html")).exists();

        Document combined = Jsoup.parse(Files.readString(seasonDir.resolve("standings.html")));
        assertNotNull(combined.selectFirst("th:contains(Group)"), "Combined view must show Group column");

        Document groupA = Jsoup.parse(Files.readString(seasonDir.resolve("standings-regular-group-group-a.html")));
        assertThat(groupA.selectFirst("th:contains(Group)")).isNull();
    }

    /**
     * SC2 / SC3: multi-phase season renders a phase-tab row at the top of {@code standings.html}.
     * Season 2023 has REGULAR + PLAYOFF (TestDataService line 929) — two phases, tab row visible.
     */
    @Test
    void givenMultiPhaseSeason_whenGenerate_thenPhaseTabRowVisible() throws IOException {
        Document doc = Jsoup.parse(
                Files.readString(tempDir.resolve("season").resolve("2023-1-season-2023").resolve("standings.html")));
        assertNotNull(doc.selectFirst("nav.phase-tab-row[role=tablist]"),
                "Multi-phase standings.html must contain a phase-tab row with role=tablist");
        assertThat(doc.select("nav.phase-tab-row a.phase-tab").size()).isGreaterThanOrEqualTo(2);
    }

    /**
     * D-08: PLAYOFF tab in the phase-tab row points at {@code playoff.html} (the existing bracket page),
     * never at a {@code standings-playoff.html}.
     */
    @Test
    void givenMultiPhaseSeason_whenGenerate_thenPlayoffTabLinksToBracket() throws IOException {
        Document doc = Jsoup.parse(
                Files.readString(tempDir.resolve("season").resolve("2023-1-season-2023").resolve("standings.html")));
        var playoffTab = doc.select("nav.phase-tab-row a.phase-tab").stream()
                .filter(a -> "Playoff".equalsIgnoreCase(a.text()) || a.attr("href").endsWith("playoff.html"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Phase-tab row must contain a PLAYOFF tab"));
        assertThat(playoffTab.attr("href")).endsWith("playoff.html");
    }

    /**
     * D-08 invariant: {@code standings-playoff.html} is NEVER generated. The PLAYOFF tab links to
     * {@code playoff.html} (the existing bracket) instead.
     */
    @Test
    void givenPlayoffPhaseWithoutResults_whenGenerate_thenStandingsPlayoffNotGenerated() {
        Path seasonDir = tempDir.resolve("season").resolve("2023-1-season-2023");
        assertThat(seasonDir.resolve("standings-playoff.html")).doesNotExist();
    }

    /**
     * D-26: phase-tab row carries {@code role="tablist"} on the {@code <nav>}, and each tab anchor
     * carries {@code role="tab"} plus a non-null {@code aria-selected} ("true" | "false").
     */
    @Test
    void givenMultiPhaseSeason_whenGenerate_thenTabRowHasA11yAttributes() throws IOException {
        Document doc = Jsoup.parse(
                Files.readString(tempDir.resolve("season").resolve("2023-1-season-2023").resolve("standings.html")));
        var tabRow = doc.selectFirst("nav.phase-tab-row");
        assertNotNull(tabRow, "Phase-tab row <nav> must be present");
        assertThat(tabRow.attr("role")).isEqualTo("tablist");
        var firstTab = tabRow.selectFirst("a.phase-tab");
        assertNotNull(firstTab, "At least one .phase-tab anchor must be present");
        assertThat(firstTab.attr("role")).isEqualTo("tab");
        assertThat(firstTab.attr("aria-selected")).isIn("true", "false");
    }

    /**
     * D-26 (group sub-tab row): same a11y attributes as the phase-tab row. The active sub-tab on
     * the combined-view page is "Combined" → {@code aria-selected="true"}.
     */
    @Test
    void givenGroupsLayoutSeason_whenGenerate_thenGroupSubTabRowHasA11yAttributes() throws IOException {
        Document doc = Jsoup.parse(
                Files.readString(tempDir.resolve("season").resolve("2023-1-season-2023").resolve("standings.html")));
        var groupRow = doc.selectFirst("nav.group-tab-row");
        assertNotNull(groupRow, "Group sub-tab row <nav> must be present on a GROUPS-layout combined view");
        assertThat(groupRow.attr("role")).isEqualTo("tablist");
        var combinedTab = groupRow.selectFirst("a.group-tab.active");
        assertNotNull(combinedTab, "Combined sub-tab must be active on the legacy standings.html");
        assertThat(combinedTab.attr("aria-selected")).isEqualTo("true");
    }

    /**
     * Legacy {@code standings.html} group sub-tab hrefs MUST point to the actual per-phase
     * group files {@code standings-{phaseSlug}-group-{groupSlug}.html}, not to
     * {@code standings-group-{groupSlug}.html} (which does not exist — there is no
     * legacy group variant). Caught during Plan 7 visual sweep — broken click-through
     * on the GROUPS multi-phase landing page.
     */
    @Test
    void givenGroupsLayoutSeason_whenGenerateLegacyView_thenGroupSubTabHrefsIncludePhaseSlug() throws IOException {
        Document doc = Jsoup.parse(
                Files.readString(tempDir.resolve("season").resolve("2023-1-season-2023").resolve("standings.html")));
        var groupTabs = doc.select("nav.group-tab-row a.group-tab");
        assertThat(groupTabs).as("Legacy standings.html must render Combined + per-group sub-tabs").hasSizeGreaterThan(1);
        for (var tab : groupTabs) {
            String href = tab.attr("href");
            String label = tab.text();
            if ("Combined".equalsIgnoreCase(label)) {
                assertThat(href).as("Combined sub-tab on legacy standings.html").isEqualTo("standings.html");
            } else {
                assertThat(href)
                        .as("Group '%s' sub-tab href on legacy standings.html must include phase slug", label)
                        .matches("standings-regular-group-[a-z0-9-]+\\.html")
                        .doesNotMatch("standings-group-[a-z0-9-]+\\.html");
                assertThat(tempDir.resolve("season").resolve("2023-1-season-2023").resolve(href).toFile())
                        .as("Group sub-tab href '%s' must point to an actually generated file", href)
                        .exists();
            }
        }
    }

    /**
     * D-32: GROUPS combined view shows the Group column with the team's group name.
     */
    @Test
    void givenGroupsLayoutSeason_whenGenerateCombined_thenShowGroupColumnTrue() throws IOException {
        Document doc = Jsoup.parse(
                Files.readString(tempDir.resolve("season").resolve("2023-1-season-2023").resolve("standings.html")));
        assertNotNull(doc.selectFirst("th:contains(Group)"), "Combined view must show Group column");
        var firstRow = doc.selectFirst("tbody tr");
        assertNotNull(firstRow, "Combined view must have at least one row");
        var groupCells = firstRow.select("td");
        assertThat(groupCells.stream().anyMatch(td -> td.text().matches("Group [AB]")))
                .as("First row must contain a 'Group A' or 'Group B' cell")
                .isTrue();
    }

    /**
     * D-33: Buchholz column appears only on per-group + Swiss-format pages. The current
     * TestDataService has no Swiss + GROUPS fixture (Season 2024 is SWISS but LEAGUE-layout;
     * Season 2023 is GROUPS but ROUND_ROBIN). Disabled with a clear deferral note for Plan 6.
     */
    @Test
    @Disabled("requires Swiss + GROUPS fixture; deferred to Plan 5/6 fixture extension")
    void givenGroupsSwissLayoutSeason_whenGeneratePerGroup_thenShowBuchholzColumn() {
        // Placeholder for the Swiss + GROUPS Buchholz column check.
    }
}
