package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.ctc.admin.TestDataService;
import org.ctc.domain.model.Season;
import org.ctc.domain.service.PlayoffBracketViewService;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import java.util.List;
import java.util.UUID;
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
class MatchdaysPageGeneratorTest {

    static final Path tempDir = SitegenTestDir.create("matchdays");

    @DynamicPropertySource
    static void siteOutputDir(DynamicPropertyRegistry registry) {
        registry.add("ctc.site.output-dir", () -> tempDir.toString());
    }

    @Autowired private SiteGeneratorService siteGeneratorService;
    @Autowired private TemplateEngine templateEngine;
    @Autowired private TestDataService testDataService;
    @Autowired private DataSource dataSource;

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
        try {
            siteGeneratorService.generate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void givenLeagueOnlySeason_whenGenerateIndex_thenLegacyMatchdaysHtmlExists() throws IOException {
        Path file = tempDir.resolve("season").resolve("2026-4-regular-season").resolve("matchdays.html");
        assertThat(file).exists();
        assertThat(Files.readString(file)).doesNotContain("phase-tab-row");
    }

    @Test
    void givenLeagueOnlySeason_whenGenerateIndex_thenLegacyContainsOnlyRegularPhaseMatchdays() throws IOException {
        Path file = tempDir.resolve("season").resolve("2026-4-regular-season").resolve("matchdays.html");
        Document doc = Jsoup.parse(Files.readString(file));
        // Season 2026 has no PLAYOFF — its matchdays must all appear; PLAYOFF labels must not.
        assertThat(doc.text()).doesNotContain("Playoffs");
    }

    @Test
    void givenMultiPhaseSeason_whenGenerateIndex_thenLegacyContainsOnlyRegularPhaseMatchdays() throws IOException {
        Path file = tempDir.resolve("season").resolve("2023-1-season-2023").resolve("matchdays.html");
        Document doc = Jsoup.parse(Files.readString(file));
        var tableText = doc.select(".matchday-index").text();
        assertThat(tableText).doesNotContain("2023 Playoffs");
        assertThat(doc.select(".matchday-index a").size()).isEqualTo(6);
    }

    @Test
    void givenMultiPhaseSeason_whenGenerateIndex_thenPerPhaseVariantsExist() {
        Path seasonDir = tempDir.resolve("season").resolve("2023-1-season-2023");
        assertThat(seasonDir.resolve("matchdays-regular.html")).exists();
        assertThat(seasonDir.resolve("matchdays-playoff.html")).doesNotExist();
    }

    @Test
    void givenGroupsLayoutSeason_whenGenerateIndex_thenPerGroupVariantsExist() {
        Path seasonDir = tempDir.resolve("season").resolve("2023-1-season-2023");
        assertThat(seasonDir.resolve("matchdays-regular-group-group-a.html")).exists();
        assertThat(seasonDir.resolve("matchdays-regular-group-group-b.html")).exists();
    }

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
                .orElseThrow(() -> new AssertionError("PLAYOFF tab href must end with playoff.html"));
        assertThat(playoffTab.attr("href")).endsWith("playoff.html");
    }

    @Test
    void givenMultiPhaseSeason_whenGenerateIndex_thenTabRowHasA11yAttributes() throws IOException {
        Document doc = Jsoup.parse(
                Files.readString(tempDir.resolve("season").resolve("2023-1-season-2023").resolve("matchdays.html")));
        var firstTab = doc.selectFirst("nav.phase-tab-row a.phase-tab");
        assertNotNull(firstTab, "At least one .phase-tab anchor must be present");
        assertThat(firstTab.attr("role")).isEqualTo("tab");
        assertThat(firstTab.attr("aria-selected")).isIn("true", "false");
    }

    @Test
    void givenGroupsLayoutSeason_whenGenerateLegacyIndex_thenGroupSubTabHrefsIncludePhaseSlug() throws IOException {
        Document doc = Jsoup.parse(
                Files.readString(tempDir.resolve("season").resolve("2023-1-season-2023").resolve("matchdays.html")));
        var groupTabs = doc.select("nav.group-tab-row a.group-tab");
        assertThat(groupTabs).as("Legacy matchdays.html must render Combined + per-group sub-tabs").hasSizeGreaterThan(1);
        for (var tab : groupTabs) {
            String href = tab.attr("href");
            String label = tab.text();
            if ("Combined".equalsIgnoreCase(label)) {
                assertThat(href).as("Combined sub-tab on legacy matchdays.html").isEqualTo("matchdays.html");
            } else {
                assertThat(href)
                        .as("Group '%s' sub-tab href on legacy matchdays.html must include phase slug", label)
                        .matches("matchdays-regular-group-[a-z0-9-]+\\.html")
                        .doesNotMatch("matchdays-group-[a-z0-9-]+\\.html");
                assertThat(tempDir.resolve("season").resolve("2023-1-season-2023").resolve(href).toFile())
                        .as("Group sub-tab href '%s' must point to an actually generated file", href)
                        .exists();
            }
        }
    }

    @Test
    void givenLeagueOnlySeason_whenGenerateIndex_thenNoTabRowAndNoGroupRow() throws IOException {
        String html = Files.readString(
                tempDir.resolve("season").resolve("2026-4-regular-season").resolve("matchdays.html"));
        assertThat(html).doesNotContain("phase-tab-row");
        assertThat(html).doesNotContain("group-tab-row");
    }
    @Test
    void givenRaceResults_whenGenerateDetails_thenMobileAndFullTableKeepValuesAndProfileLinks() throws IOException {
        Path file = tempDir.resolve("season/2026-4-regular-season/matchday/matchday-1.html");
        var doc = Jsoup.parse(Files.readString(file));
        var script = doc.selectFirst("script[src$='/js/race-results.js']");
        assertThat(script).isNotNull();
        assertThat(file.getParent().resolve(script.attr("src")).normalize()).exists();
        var cards = doc.select(".race-card:has(.race-driver-results)");
        assertThat(cards).isNotEmpty();
        for (var card : cards) {
            var rows = card.select("tbody tr");
            var mobile = card.select(".driver-result");
            assertThat(mobile).hasSameSizeAs(rows);
            for (int i = 0; i < rows.size(); i++) {
                var cells = rows.get(i).select("td");
                var driver = mobile.get(i);
                assertThat(driver.select(".result-driver-name").text()).isEqualTo(cells.get(0).text());
                assertThat(driver.select(".result-team").text()).isEqualTo(cells.get(1).text());
                assertThat(driver.select(".result-position > span:last-child").text()).isEqualTo(cells.get(2).text());
                assertThat(driver.select(".result-quali").text()).isEqualTo(cells.get(3).text());
                assertThat(driver.select(".result-fastest-lap").text())
                        .isEqualTo(cells.get(4).text().isEmpty() ? "No" : "Yes");
                assertThat(driver.select(".result-points-value").text()).isEqualTo(cells.get(5).text());
                var href = driver.selectFirst("a.entity-link").attr("href");
                assertThat(href).isEqualTo(cells.get(0).selectFirst("a").attr("href"));
                assertThat(file.getParent().resolve(href).normalize()).exists();
            }
        }
    }

    @Test
    void givenGroupAndPlayoffMatchdays_whenGenerateDetails_thenReturnLinksRetainContext() throws IOException {
        Path dir = tempDir.resolve("season/2023-1-season-2023/matchday");
        for (var entry : java.util.Map.of(
                "group-a-matchday-1.html", "../matchdays-regular-group-group-a.html",
                "2023-playoffs.html", "../playoff.html").entrySet()) {
            var doc = Jsoup.parse(Files.readString(dir.resolve(entry.getKey())));
            var link = doc.selectFirst(".matchday-back-link");
            assertThat(link).isNotNull();
            assertThat(link.attr("href")).isEqualTo(entry.getValue());
            assertThat(dir.resolve(link.attr("href")).normalize()).exists();
        }
    }

    @Test
    void givenSubteamRace_whenGenerateDetails_thenScoreIncludesItsDriverPoints() throws IOException {
        var doc = Jsoup.parse(Files.readString(tempDir.resolve(
                "season/2026-4-regular-season/matchday/matchday-1.html")));
        var card = doc.select(".race-card").stream()
                .filter(c -> c.select(".match-team").last().text().equals("VRX A"))
                .findFirst().orElseThrow();
        var rows = card.select("tbody tr");
        int home = rows.stream().filter(r -> r.select("td").get(1).text().equals("ADR"))
                .mapToInt(r -> Integer.parseInt(r.select("td").get(5).text())).sum();
        int away = rows.stream().filter(r -> r.select("td").get(1).text().equals("VRX A"))
                .mapToInt(r -> Integer.parseInt(r.select("td").get(5).text())).sum();
        assertThat(away).isPositive();
        assertThat(card.select(".match-score").text()).isEqualTo(home + " : " + away);
    }

    @Test
    void givenArchive_whenGenerate_thenMobileCardsKeepSeasonMetadataAndDestinations() throws IOException {
        Path file = tempDir.resolve("archive.html");
        var doc = Jsoup.parse(Files.readString(file));
        var rows = doc.select("tbody tr");
        var cards = doc.select(".archive-season");
        assertThat(rows).isNotEmpty();
        assertThat(cards).hasSameSizeAs(rows);
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i);
            var card = cards.get(i);
            assertThat(card.attr("data-year")).isEqualTo(row.attr("data-year"));
            assertThat(card.attr("data-number")).isEqualTo(row.attr("data-number"));
            assertThat(card.select(".season-meta").text()).isEqualTo(row.select(".season-meta").text());
            assertThat(card.select(".archive-period").text()).isEqualTo(row.select("td").get(1).text());
            assertThat(card.select(".archive-status").text()).isEqualTo(row.select("td").get(2).text());
            var href = card.selectFirst("a").attr("href");
            assertThat(href).isEqualTo(row.selectFirst("a").attr("href"));
            assertThat(file.getParent().resolve(href)).exists();
        }
        assertThat(doc.selectFirst("th.sortable").attr("aria-sort")).isEqualTo("descending");
        assertThat(doc.selectFirst("th.sortable button")).isNotNull();
    }

    @Test
    void givenPlayoff_whenGenerate_thenRoundNavigationTargetsAllMatchupsAndResultDetails() throws IOException {
        var doc = Jsoup.parse(Files.readString(tempDir.resolve("season/2023-1-season-2023/playoff.html")));
        var rounds = doc.select(".playoff-round");
        assertThat(rounds).isNotEmpty();
        var links = doc.select(".playoff-round-nav a");
        assertThat(links).hasSameSizeAs(rounds);
        for (int i = 0; i < rounds.size(); i++) {
            assertThat(links.get(i).attr("href")).isEqualTo("#" + rounds.get(i).id());
            assertThat(rounds.get(i).select(".bracket-matchup")).isNotEmpty();
            assertThat(rounds.get(i).selectFirst("summary").text()).contains(links.get(i).text());
        }
        assertThat(doc.select(".bracket-matchup .matchup-status")).hasSameSizeAs(doc.select(".bracket-matchup"));
    }

    @Test
    void givenCompletedAndPendingMatchups_whenRender_thenWinnersAndMissingResultsAreExplicit() {
        var completed = new PlayoffBracketViewService.MatchupView(UUID.randomUUID(), 1,
                UUID.randomUUID(), UUID.randomUUID(), "Alpha", "Bravo", null, null, 1, 2,
                42, 13, true, false, true,
                List.of(new PlayoffBracketViewService.LegView(UUID.randomUUID(), 1, 42, 13, true)));
        var pending = new PlayoffBracketViewService.MatchupView(UUID.randomUUID(), 2,
                UUID.randomUUID(), null, "Charlie", null, null, null, 3, null,
                0, 0, false, false, false,
                List.of(new PlayoffBracketViewService.LegView(UUID.randomUUID(), 1, 0, 0, false)));
        var context = new Context();
        context.setVariable("bracket", new PlayoffBracketViewService.PlayoffBracketView(UUID.randomUUID(),
                "Fixture playoffs", List.of(new PlayoffBracketViewService.RoundView("Final", 1,
                        List.of(completed, pending)))));
        context.setVariable("season", new Season("Fixture season", 2026, 98));
        context.setVariable("pageTitle", "Fixture playoffs");
        context.setVariable("currentPage", "playoff");
        context.setVariable("rootPath", ".");
        context.setVariable("assetsPath", "assets");
        var doc = Jsoup.parse(templateEngine.process("site/playoff-bracket", context));
        var matches = doc.select(".bracket-matchup");
        assertThat(matches.get(0).select(".bracket-winner").text()).isEqualTo("Winner");
        assertThat(matches.get(0).select(".winner .bracket-team-name").text()).contains("Alpha");
        assertThat(matches.get(0).select(".bracket-team-score").eachText()).containsExactly("42", "13");
        assertThat(matches.get(0).select(".playoff-legs tbody td").eachText()).containsExactly("42", "13");
        assertThat(matches.get(1).select(".bracket-winner")).isEmpty();
        assertThat(matches.get(1).select(".bracket-team-score").eachText()).containsExactly("-");
        assertThat(matches.get(1).select(".matchup-status").text()).isEqualTo("Pending");
        assertThat(matches.get(1).select(".bracket-team").last().text()).isEqualTo("TBD");
        assertThat(matches.get(1).select(".playoff-legs tbody td").eachText()).containsExactly("-", "-");
    }

}
