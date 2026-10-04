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

    @Test
    void givenLeagueOnlySeason_whenGenerate_thenLegacyDriverRankingExists() throws IOException {
        Path file = tempDir.resolve("season").resolve("2026-4-regular-season").resolve("driver-ranking.html");
        assertThat(file).exists();
        assertThat(Files.readString(file)).doesNotContain("phase-tab-row");
    }

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

    @Test
    void givenMultiPhaseSeason_whenGenerate_thenPhaseTabRowFirstTabIsAllPhases() throws IOException {
        Document doc = Jsoup.parse(Files.readString(
                tempDir.resolve("season").resolve("2023-1-season-2023").resolve("driver-ranking.html")));
        var firstTab = doc.selectFirst("nav.phase-tab-row a.phase-tab");
        assertNotNull(firstTab, "Multi-phase driver-ranking.html must contain a phase-tab row with at least one tab");
        assertThat(firstTab.text()).isEqualTo("All Phases");
        assertThat(firstTab.attr("href")).endsWith("driver-ranking.html");
    }

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

    @Test
    void givenMultiPhaseSeason_whenGenerateRegularVariant_thenAllPhasesTabIsInactive() throws IOException {
        Document doc = Jsoup.parse(Files.readString(
                tempDir.resolve("season").resolve("2023-1-season-2023").resolve("driver-ranking-regular.html")));
        var allPhasesTab = doc.select("nav.phase-tab-row a.phase-tab").stream()
                .filter(a -> "All Phases".equals(a.text()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("All Phases tab must be present in the row"));
        assertThat(allPhasesTab.attr("aria-selected")).isEqualTo("false");

        var regularTab = doc.select("nav.phase-tab-row a.phase-tab").stream()
                .filter(a -> a.attr("href").endsWith("driver-ranking-regular.html"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("REGULAR tab must be present in the row"));
        assertThat(regularTab.attr("aria-selected")).isEqualTo("true");
    }

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
    @Test
    void givenSeasonAndAlltimeRankings_whenGenerate_thenMobileValuesAndLinksMatchTables() throws IOException {
        for (String path : java.util.List.of("season/2026-4-regular-season/driver-ranking.html",
                "season/2023-1-season-2023/driver-ranking-regular.html", "alltime-driver-ranking.html")) {
            Path file = tempDir.resolve(path);
            var doc = Jsoup.parse(Files.readString(file));
            var rows = doc.select("tbody tr");
            var cards = doc.select(".ranking-driver");
            assertThat(rows).isNotEmpty();
            assertThat(cards).hasSameSizeAs(rows);
            for (int i = 0; i < rows.size(); i++) {
                var cells = rows.get(i).select("td");
                var card = cards.get(i);
                assertThat(card.select(".ranking-rank").text()).isEqualTo(cells.get(0).text());
                assertThat(card.select(".ranking-name").text()).isEqualTo(cells.get(1).selectFirst("a").text());
                assertThat(card.select(".ranking-team").text()).isEqualTo(cells.get(2).text());
                assertThat(card.select(".ranking-races").text()).isEqualTo(cells.get(3).text());
                assertThat(card.select(".ranking-best").text()).isEqualTo(cells.get(4).text());
                assertThat(card.select(".ranking-average").text()).isEqualTo(cells.get(5).text());
                assertThat(card.select(".ranking-points").text()).isEqualTo(cells.get(6).text());
                assertThat(card.select(".guest-marker")).hasSameSizeAs(cells.get(1).select(".guest-marker"));
                String href = card.selectFirst("a.entity-link").attr("href");
                assertThat(href).isEqualTo(cells.get(1).selectFirst("a").attr("href"));
                assertThat(file.getParent().resolve(href).normalize()).exists();
            }
        }
    }

    @Test
    void givenDriverHistory_whenGenerate_thenMobileResultsAndSummaryMatchRecordedResults() throws IOException {
        Path file = tempDir.resolve("season/2026-4-regular-season/driver/adr-driver01.html");
        var doc = Jsoup.parse(Files.readString(file));
        var rows = doc.select(".profile-history tbody tr");
        var cards = doc.select(".history-entry");
        assertThat(rows).isNotEmpty();
        assertThat(cards).hasSameSizeAs(rows);
        int points = 0;
        for (int i = 0; i < rows.size(); i++) {
            var cells = rows.get(i).select("td");
            var card = cards.get(i);
            assertThat(card.select(".history-matchday").text()).isEqualTo(cells.get(0).selectFirst("a").text());
            assertThat(card.select(".history-opponent").text()).isEqualTo(cells.get(1).text());
            assertThat(card.select(".history-track").text()).isEqualTo(cells.get(2).text());
            assertThat(card.select(".history-position").text()).isEqualTo("P" + cells.get(3).text());
            assertThat(card.select(".history-quali").text()).isEqualTo(cells.get(4).text());
            assertThat(card.select(".history-fastest-lap").text()).isEqualTo(cells.get(5).text().isEmpty() ? "No" : "Yes");
            assertThat(card.select(".history-points").text()).isEqualTo(cells.get(6).text());
            points += Integer.parseInt(cells.get(6).text());
            assertThat(file.getParent().resolve(card.selectFirst("a").attr("href")).normalize()).exists();
        }
        assertThat(doc.select(".profile-total-points").text()).isEqualTo(String.valueOf(points));
        assertThat(doc.select(".profile-total-races").text()).isEqualTo(String.valueOf(rows.size()));
    }

}
