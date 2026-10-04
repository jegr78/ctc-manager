package org.ctc.sitegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.ctc.admin.TestDataService;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
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
class TeamProfilePageGeneratorTest {

    static final Path tempDir = SitegenTestDir.create("team-profile");

    @DynamicPropertySource
    static void siteOutputDir(DynamicPropertyRegistry registry) {
        registry.add("ctc.site.output-dir", () -> tempDir.toString());
    }

    @Autowired private SiteGeneratorService siteGeneratorService;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private SeasonPhaseService seasonPhaseService;
    @Autowired private StandingsService standingsService;
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
    void givenLeagueOnlySeasonTeam_whenGenerate_thenNoPhaseBreakdownSection() throws IOException {
        Path teamProfile = tempDir.resolve("season").resolve("2026-4-regular-season")
                .resolve("team").resolve("adr.html");
        assertThat(teamProfile).exists();
        String html = Files.readString(teamProfile);
        assertThat(html).doesNotContain("Phase Breakdown");
    }

    @Test
    void givenLeagueOnlySeasonTeam_whenGenerate_thenLegacyByteIdentical() throws IOException {
        Path baseline = Path.of("src/test/resources/sitegen/baseline/single-league-team-profile.html");
        Path generated = tempDir.resolve("season").resolve("2026-4-regular-season")
                .resolve("team").resolve("adr.html");
        assertThat(generated).exists();
        assertThat(canonicalize(Files.readString(generated)))
                .isEqualTo(canonicalize(Files.readString(baseline)));
    }

    private static String canonicalize(String html) {
        return collapseWhitespace(normalizeOptionalLogo(normalizeUuids(html)));
    }

    private static String normalizeUuids(String html) {
        return html.replaceAll(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                "00000000-0000-0000-0000-000000000000");
    }

    private static String normalizeOptionalLogo(String html) {
        return html.replaceAll(
                "<img src=\"[^\"]*\\.png\" class=\"team-logo\"[^>]*>",
                "");
    }

    private static String collapseWhitespace(String html) {
        return html.replaceAll("\\s+", " ").trim();
    }

    @Test
    void givenMultiPhaseSeasonTeam_whenGenerate_thenPhaseBreakdownSectionVisible() throws IOException {
        Path teamProfile = tempDir.resolve("season").resolve("2023-1-season-2023")
                .resolve("team").resolve("adr.html");
        assertThat(teamProfile).exists();
        Document doc = Jsoup.parse(Files.readString(teamProfile));
        var heading = doc.select("h2.section-title").stream()
                .filter(h -> "Phase Breakdown".equals(h.text()))
                .findFirst()
                .orElse(null);
        assertNotNull(heading,
                "Multi-phase team-profile.html must contain a 'Phase Breakdown' section heading");

        var breakdownSection = heading.parent();
        assertNotNull(breakdownSection, "Phase Breakdown heading must have a parent section");
        var rows = breakdownSection.select("table tbody tr");
        assertThat(rows.size()).as("Phase Breakdown table must list at least 2 phases").isGreaterThanOrEqualTo(2);
    }

    @Test
    void givenMultiPhaseSeasonTeam_whenGenerate_thenStandingsPanelUsesCombinedView() throws IOException {
        Path teamProfile = tempDir.resolve("season").resolve("2023-1-season-2023")
                .resolve("team").resolve("adr.html");
        Document doc = Jsoup.parse(Files.readString(teamProfile));



        var firstSectionTitle = doc.selectFirst("h2.section-title");
        assertNotNull(firstSectionTitle, "team-profile.html must still contain its main standings panel");
        assertThat(firstSectionTitle.text()).isEqualTo("Record");
        var recordSection = firstSectionTitle.parent();
        assertNotNull(recordSection);
        var season = seasonRepository.findByYearAndNumber(2023, 1).getFirst();
        var phase = seasonPhaseService.findRegularPhase(season.getId());
        var standing = standingsService.calculateStandings(phase.getId(), null).stream()
                .filter(s -> s.getTeam().getShortName().equals("ADR")).findFirst().orElseThrow();
        assertThat(recordSection.select(".profile-metrics dd").eachText()).containsExactly(
                String.valueOf(standing.getPoints()), String.valueOf(standing.getPlayed()),
                String.valueOf(standing.getWins()), String.valueOf(standing.getDraws()),
                String.valueOf(standing.getLosses()), standing.getPointsRatio());
    }

    @Test
    void givenMultiPhaseSeasonTeam_whenGenerate_thenSingleProfileUrl() {
        Path teamDir = tempDir.resolve("season").resolve("2023-1-season-2023").resolve("team");
        assertThat(teamDir.resolve("adr-regular.html")).doesNotExist();
        assertThat(teamDir.resolve("adr-playoff.html")).doesNotExist();
        assertThat(teamDir.resolve("adr-placement.html")).doesNotExist();

        assertThat(teamDir.resolve("adr.html")).exists();
    }
}
