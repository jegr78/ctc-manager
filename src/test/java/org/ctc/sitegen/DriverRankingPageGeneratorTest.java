package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import javax.sql.DataSource;
import org.ctc.admin.TestDataService;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.service.DriverRankingService;
import org.ctc.domain.service.SeasonPhaseService;
import org.flywaydb.core.Flyway;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.BeforeAll;
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
 * Phase 62 Plan 3 — phase-aware driver-ranking page tests.
 *
 * <p>Verifies the rewritten {@link DriverRankingPageGenerator} +
 * {@code templates/site/driver-ranking.html} against:
 * <ul>
 *   <li>SC4 backward-compat: single-REGULAR-LEAGUE seasons render driver-ranking.html with
 *       no phase-tab row (Season 2026 fixture).</li>
 *   <li>D-11 SC4-clean: legacy {@code /season/{slug}/driver-ranking.html} keeps the
 *       cross-phase aggregated data source ({@code aggregateAcrossPhases}) — the legacy URL
 *       row count equals the aggregated ranking size.</li>
 *   <li>D-11 per-phase variants {@code driver-ranking-{phaseSlug}.html} use
 *       {@code calculateRankingForPhase} (multi-phase 2023 fixture). Per-phase URL row count
 *       equals {@code calculateRankingForPhase} size.</li>
 *   <li>D-11 PLAYOFF reconciliation per UI-SPEC line 333: when PLAYOFF has driver data,
 *       {@code driver-ranking-playoff.html} IS generated; if PLAYOFF has no driver data,
 *       it is skipped (and the PLAYOFF entry is omitted from the tab row).</li>
 *   <li>D-04 / D-26 phase-tab row markup: first tab labeled "All Phases" (UI-SPEC line 263)
 *       linking to the legacy URL; {@code role=tablist} on the {@code <nav>}, {@code role=tab}
 *       and {@code aria-selected} on each anchor.</li>
 *   <li>"All Phases" semantics: active on the legacy URL, inactive on per-phase variants.</li>
 * </ul>
 *
 * <p>Fixtures used (TestDataService):
 * <ul>
 *   <li>Season 2023 (slug {@code 2023-1-season-2023}) — REGULAR (GROUPS) + PLAYOFF (4-team
 *       semifinal with race results, TestDataService line 943).</li>
 *   <li>Season 2026 (slug {@code 2026-4-regular-season}) — single-REGULAR-LEAGUE.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DriverRankingPageGeneratorTest {

    static final Path tempDir = SitegenTestDir.create("driver-ranking");

    @DynamicPropertySource
    static void siteOutputDir(DynamicPropertyRegistry registry) {
        registry.add("ctc.site.output-dir", () -> tempDir.toString());
    }

    private UUID season2023Id;

    @Autowired private SiteGeneratorService siteGeneratorService;
    @Autowired private TestDataService testDataService;
    @Autowired private DataSource dataSource;
    @Autowired private DriverRankingService driverRankingService;
    @Autowired private SeasonPhaseService seasonPhaseService;
    @Autowired private SeasonRepository seasonRepository;

    @MockitoBean private YouTubeScraperService youTubeScraperService;

    @BeforeAll
    void setUp() {
        given(youTubeScraperService.scrapeVideoId(anyString(), anyString()))
                .willReturn("dQw4w9WgXcQ");

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

        // Cache Season 2023's id for data-source assertions
        this.season2023Id = seasonRepository.findByYearAndNumber(2023, 1).stream()
                .findFirst()
                .orElseThrow(() -> new AssertionError("Season 2023 fixture missing"))
                .getId();

        try {
            siteGeneratorService.generate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * SC4 backward-compat: single-REGULAR-LEAGUE driver-ranking.html exists and has no phase-tab row.
     */
    @Test
    void givenLeagueOnlySeason_whenGenerate_thenLegacyDriverRankingExists() throws IOException {
        Path file = tempDir.resolve("season").resolve("2026-4-regular-season").resolve("driver-ranking.html");
        assertThat(file).exists();
        assertThat(Files.readString(file)).doesNotContain("phase-tab-row");
    }

    /**
     * SC4-clean (D-11): for the LEAGUE-only fixture, the legacy driver-ranking.html data is the
     * cross-phase aggregated ranking. Asserts the table row count equals
     * {@code aggregateAcrossPhases.size()} for Season 2026 (which has only a REGULAR phase, so
     * the aggregated count equals the REGULAR-only count).
     */
    @Test
    void givenLeagueOnlySeason_whenGenerate_thenLegacyDataMatchesAggregateAcrossPhases() throws IOException {
        var s2026 = seasonRepository.findByYearAndNumber(2026, 4).stream().findFirst().orElseThrow();
        Document doc = Jsoup.parse(Files.readString(
                tempDir.resolve("season").resolve("2026-4-regular-season").resolve("driver-ranking.html")));
        int rowCount = doc.select("tbody tr").size();
        var phaseIds = seasonPhaseService.findAllPhases(s2026.getId()).stream()
                .map(SeasonPhase::getId).toList();
        int expected = driverRankingService.aggregateAcrossPhases(phaseIds, s2026.getId()).size();
        assertThat(rowCount).isEqualTo(expected);
    }

    /**
     * D-11 per-phase variants: multi-phase Season 2023 generates {@code driver-ranking-regular.html}
     * and (because PLAYOFF has driver data via TestDataService line 943) also
     * {@code driver-ranking-playoff.html}. UI-SPEC line 333 explicitly authorizes the PLAYOFF
     * driver-ranking variant, separate from D-08 which governs standings-playoff.html (NEVER
     * generated).
     */
    @Test
    void givenMultiPhaseSeason_whenGenerate_thenPerPhaseVariantsExist() {
        Path seasonDir = tempDir.resolve("season").resolve("2023-1-season-2023");
        assertThat(seasonDir.resolve("driver-ranking-regular.html")).exists();

        var allPhases = seasonPhaseService.findAllPhases(season2023Id);
        var playoff = allPhases.stream()
                .filter(p -> p.getPhaseType() == PhaseType.PLAYOFF).findFirst().orElseThrow();
        boolean playoffHasDrivers = !driverRankingService.calculateRankingForPhase(playoff.getId()).isEmpty();
        if (playoffHasDrivers) {
            assertThat(seasonDir.resolve("driver-ranking-playoff.html")).exists();
        } else {
            assertThat(seasonDir.resolve("driver-ranking-playoff.html")).doesNotExist();
        }
    }

    /**
     * D-04 / UI-SPEC line 263: the FIRST anchor in the phase-tab row for a multi-phase season
     * is labeled exactly "All Phases" and its href points to the legacy URL ({@code driver-ranking.html}).
     */
    @Test
    void givenMultiPhaseSeason_whenGenerate_thenPhaseTabRowFirstTabIsAllPhases() throws IOException {
        Document doc = Jsoup.parse(Files.readString(
                tempDir.resolve("season").resolve("2023-1-season-2023").resolve("driver-ranking.html")));
        var firstTab = doc.selectFirst("nav.phase-tab-row a.phase-tab");
        assertNotNull(firstTab, "Multi-phase driver-ranking.html must contain a phase-tab row with at least one tab");
        assertThat(firstTab.text()).isEqualTo("All Phases");
        assertThat(firstTab.attr("href")).endsWith("driver-ranking.html");
    }

    /**
     * D-26 a11y attributes on the phase-tab row. The first tab on the legacy URL is "All Phases"
     * and is the active default → {@code aria-selected="true"}.
     */
    @Test
    void givenMultiPhaseSeason_whenGenerate_thenPhaseTabRowVisibleWithA11y() throws IOException {
        Document doc = Jsoup.parse(Files.readString(
                tempDir.resolve("season").resolve("2023-1-season-2023").resolve("driver-ranking.html")));
        var tabRow = doc.selectFirst("nav.phase-tab-row");
        assertNotNull(tabRow, "Phase-tab row <nav> must be present on multi-phase driver-ranking.html");
        assertThat(tabRow.attr("role")).isEqualTo("tablist");
        var firstTab = tabRow.selectFirst("a.phase-tab");
        assertNotNull(firstTab, "At least one .phase-tab anchor must be present");
        assertThat(firstTab.attr("role")).isEqualTo("tab");
        assertThat(firstTab.attr("aria-selected")).isEqualTo("true");
    }

    /**
     * On {@code driver-ranking-regular.html}, the "All Phases" tab is inactive (the REGULAR tab is
     * active). Verifies the active-flag flips correctly per variant.
     */
    @Test
    void givenMultiPhaseSeason_whenGenerateRegularVariant_thenAllPhasesTabIsInactive() throws IOException {
        Document doc = Jsoup.parse(Files.readString(
                tempDir.resolve("season").resolve("2023-1-season-2023").resolve("driver-ranking-regular.html")));
        var allPhasesTab = doc.select("nav.phase-tab-row a.phase-tab").stream()
                .filter(a -> "All Phases".equals(a.text()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("All Phases tab must be present in the row"));
        assertThat(allPhasesTab.attr("aria-selected")).isEqualTo("false");

        // The REGULAR tab must be active
        var regularTab = doc.select("nav.phase-tab-row a.phase-tab").stream()
                .filter(a -> a.attr("href").endsWith("driver-ranking-regular.html"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("REGULAR tab must be present in the row"));
        assertThat(regularTab.attr("aria-selected")).isEqualTo("true");
    }

    /**
     * D-11 SC4-clean data-source contract: legacy {@code driver-ranking.html} for a multi-phase
     * season uses {@code aggregateAcrossPhases} — the table row count must equal the aggregated
     * ranking size (NOT the REGULAR-phase-only size).
     */
    @Test
    void givenMultiPhaseSeason_whenGenerate_thenLegacyDataMatchesAggregateAcrossPhases() throws IOException {
        Document doc = Jsoup.parse(Files.readString(
                tempDir.resolve("season").resolve("2023-1-season-2023").resolve("driver-ranking.html")));
        int rowCount = doc.select("tbody tr").size();

        var allPhases = seasonPhaseService.findAllPhases(season2023Id);
        var phaseIds = allPhases.stream().map(SeasonPhase::getId).toList();
        int expected = driverRankingService.aggregateAcrossPhases(phaseIds, season2023Id).size();
        assertThat(rowCount).isEqualTo(expected);
    }

    /**
     * D-11 per-phase data-source contract: {@code driver-ranking-regular.html} uses
     * {@code calculateRankingForPhase(regularPhase)} — the row count must equal that size.
     */
    @Test
    void givenMultiPhaseSeason_whenGenerateRegularVariant_thenDataMatchesCalculateRankingForPhase() throws IOException {
        Document doc = Jsoup.parse(Files.readString(
                tempDir.resolve("season").resolve("2023-1-season-2023").resolve("driver-ranking-regular.html")));
        int rowCount = doc.select("tbody tr").size();

        var regular = seasonPhaseService.findAllPhases(season2023Id).stream()
                .filter(p -> p.getPhaseType() == PhaseType.REGULAR)
                .findFirst()
                .orElseThrow();
        int expected = driverRankingService.calculateRankingForPhase(regular.getId()).size();
        assertThat(rowCount).isEqualTo(expected);
    }
}
