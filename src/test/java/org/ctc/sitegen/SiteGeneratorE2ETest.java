package org.ctc.sitegen;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.ScoringService;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext
class SiteGeneratorE2ETest {

    private Path tempDir;

    @Autowired
    private SiteGeneratorService siteGeneratorService;

    @Autowired
    private SiteProperties siteProperties;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private SeasonDriverRepository seasonDriverRepository;

    @Autowired
    private MatchdayRepository matchdayRepository;

    @Autowired
    private RaceRepository raceRepository;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private RaceScoringRepository raceScoringRepository;

    @Autowired
    private MatchScoringRepository matchScoringRepository;

    @Autowired
    private ScoringService scoringService;

    @Autowired
    private SeasonPhaseRepository seasonPhaseRepository;

    @Autowired
    private PhaseTeamRepository phaseTeamRepository;

    @MockitoBean
    private YouTubeScraperService youTubeScraperService;

    private String uniqueSuffix;
    private Season season;

    @BeforeAll
    void setUp(@TempDir Path injectedTempDir) {
        given(youTubeScraperService.scrapeVideoId(anyString(), anyString()))
                .willReturn("dQw4w9WgXcQ");
        this.tempDir = injectedTempDir;
        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // Deactivate and hide all existing seasons (mark as "Test" so productionSeasons filter excludes them)
        seasonRepository.findAll().forEach(s -> {
            s.setActive(false);
            if (!s.getName().contains("Test")) {
                s.setName("Test_" + s.getName());
            }
            seasonRepository.save(s);
        });

        var raceScoring = raceScoringRepository.save(
                new RaceScoring("E2E RS " + uniqueSuffix, "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
        var matchScoring = matchScoringRepository.save(
                new MatchScoring("E2E MS " + uniqueSuffix, 3, 1, 0));

        season = new Season("E2E Season " + uniqueSuffix, 2026, 1);
        season.setActive(true);
        seasonRepository.save(season);

        var teamAlpha = teamRepository.save(new Team("E2E Team Alpha " + uniqueSuffix, "EALP" + uniqueSuffix));
        var teamBeta = teamRepository.save(new Team("E2E Team Beta " + uniqueSuffix, "EBET" + uniqueSuffix));

        season.addTeam(teamAlpha);
        season.addTeam(teamBeta);
        seasonRepository.save(season);

        // Phase 58 D-23: SiteGenerator routes through SeasonPhaseService.findByType(REGULAR).
        // E2E setup must include a REGULAR phase + PhaseTeam rows or the season is skipped.
        var regularPhase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 1);
        regularPhase = seasonPhaseRepository.save(regularPhase);
        phaseTeamRepository.save(new PhaseTeam(regularPhase, teamAlpha));
        phaseTeamRepository.save(new PhaseTeam(regularPhase, teamBeta));

        var driver1 = driverRepository.save(new Driver("e2e_driver1_" + uniqueSuffix, "E2E_Racer1"));
        var driver2 = driverRepository.save(new Driver("e2e_driver2_" + uniqueSuffix, "E2E_Racer2"));
        var driver3 = driverRepository.save(new Driver("e2e_driver3_" + uniqueSuffix, "E2E_Racer3"));
        var driver4 = driverRepository.save(new Driver("e2e_driver4_" + uniqueSuffix, "E2E_Racer4"));

        seasonDriverRepository.save(new SeasonDriver(season, driver1, teamAlpha));
        seasonDriverRepository.save(new SeasonDriver(season, driver2, teamAlpha));
        seasonDriverRepository.save(new SeasonDriver(season, driver3, teamBeta));
        seasonDriverRepository.save(new SeasonDriver(season, driver4, teamBeta));

        var matchday = matchdayRepository.save(org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "E2E Matchday 1", 1));
        // Phase 58 D-23: link matchday to REGULAR phase so race-result-by-phase queries find it
        matchday.setPhase(regularPhase);
        matchday = matchdayRepository.save(matchday);
        var testTrack = trackRepository.save(new Track("E2E Circuit " + uniqueSuffix, "Japan"));
        var testCar = carRepository.save(new Car("E2E Car " + uniqueSuffix, "GT3 Concept"));
        var match = matchRepository.save(new Match(matchday, teamAlpha, teamBeta));

        var race = new Race();
        race.setMatchday(matchday);
        race.setMatch(match);
        race.setTrack(testTrack);
        race.setCar(testCar);

        var r1 = new RaceResult(race, driver1, 1, 1, false);
        var r2 = new RaceResult(race, driver2, 3, 3, false);
        var r3 = new RaceResult(race, driver3, 2, 2, true);
        var r4 = new RaceResult(race, driver4, 4, 4, false);
        scoringService.calculatePoints(r1, raceScoring);
        scoringService.calculatePoints(r2, raceScoring);
        scoringService.calculatePoints(r3, raceScoring);
        scoringService.calculatePoints(r4, raceScoring);
        race.getResults().add(r1);
        race.getResults().add(r2);
        race.getResults().add(r3);
        race.getResults().add(r4);
        raceRepository.save(race);

        match.setHomeScore(r1.getPointsTotal() + r2.getPointsTotal());
        match.setAwayScore(r3.getPointsTotal() + r4.getPointsTotal());
        matchRepository.save(match);

        // Configure output directory
        siteGeneratorService.setOutputDir(tempDir.toString());

        // Configure links (D-15 / Pitfall 4)
        var youtubeLink = new SiteProperties.LinkEntry();
        youtubeLink.setName("YouTube");
        youtubeLink.setUrl("https://www.youtube.com/@CommunityTeamCup");
        var discordLink = new SiteProperties.LinkEntry();
        discordLink.setName("Discord");
        discordLink.setUrl("https://discord.gg/example");
        siteProperties.setLinks(new ArrayList<>(List.of(youtubeLink, discordLink)));

        // Generate site ONCE — all tests share the output
        var result = siteGeneratorService.generate();
        assertFalse(result.hasErrors(), "Site generation failed: " + result.getErrors());
        assertTrue(result.getPagesGenerated() > 0, "Site must generate at least one page");
    }

    // --- Helper methods ---

    private boolean isInternal(String href) {
        return !href.isEmpty()
                && !href.startsWith("http://")
                && !href.startsWith("https://")
                && !href.startsWith("#")
                && !href.startsWith("javascript:")
                && !href.startsWith("mailto:")
                && !href.startsWith("data:");
    }

    private String slugify(String input) {
        return siteGeneratorService.slugify(input);
    }

    // --- E2E-01: All internal links resolve ---

    @Test
    void whenSiteGenerated_thenAllInternalLinksResolve() throws IOException {
        var brokenLinks = new ArrayList<String>();

        try (var htmlFiles = Files.walk(tempDir)
                .filter(p -> p.toString().endsWith(".html"))) {

            htmlFiles.forEach(htmlFile -> {
                try {
                    var doc = Jsoup.parse(Files.readString(htmlFile));
                    for (var link : doc.select("a[href]")) {
                        var href = link.attr("href");
                        if (isInternal(href)) {
                            var resolved = htmlFile.getParent().resolve(href).normalize();
                            if (!Files.exists(resolved)) {
                                var relativePath = tempDir.relativize(htmlFile);
                                brokenLinks.add(relativePath + " -> " + href);
                            }
                        }
                    }
                } catch (IOException e) {
                    brokenLinks.add("Parse error: " + htmlFile.getFileName());
                }
            });
        }

        assertTrue(brokenLinks.isEmpty(),
                "Broken internal links found:\n" + String.join("\n", brokenLinks));
    }

    // --- E2E-02: All pages have nav ---

    @Test
    void whenSiteGenerated_thenAllPagesHaveNav() throws IOException {
        var violations = new ArrayList<String>();

        try (var htmlFiles = Files.walk(tempDir)
                .filter(p -> p.toString().endsWith(".html"))) {

            htmlFiles.forEach(htmlFile -> {
                try {
                    var doc = Jsoup.parse(Files.readString(htmlFile));
                    if (doc.selectFirst("nav.nav") == null) {
                        violations.add(tempDir.relativize(htmlFile).toString());
                    }
                } catch (IOException e) {
                    violations.add("Parse error: " + htmlFile.getFileName());
                }
            });
        }

        assertTrue(violations.isEmpty(),
                "Pages missing nav.nav:\n" + String.join("\n", violations));
    }

    // --- E2E-02: All pages have footer ---

    @Test
    void whenSiteGenerated_thenAllPagesHaveFooter() throws IOException {
        var violations = new ArrayList<String>();

        try (var htmlFiles = Files.walk(tempDir)
                .filter(p -> p.toString().endsWith(".html"))) {

            htmlFiles.forEach(htmlFile -> {
                try {
                    var doc = Jsoup.parse(Files.readString(htmlFile));
                    if (doc.selectFirst("footer.footer") == null) {
                        violations.add(tempDir.relativize(htmlFile).toString());
                    }
                } catch (IOException e) {
                    violations.add("Parse error: " + htmlFile.getFileName());
                }
            });
        }

        assertTrue(violations.isEmpty(),
                "Pages missing footer.footer:\n" + String.join("\n", violations));
    }

    // --- E2E-03: No page has empty main content ---

    @Test
    void whenSiteGenerated_thenNoPageHasEmptyMainContent() throws IOException {
        var violations = new ArrayList<String>();

        try (var htmlFiles = Files.walk(tempDir)
                .filter(p -> p.toString().endsWith(".html"))) {

            htmlFiles.forEach(htmlFile -> {
                try {
                    var doc = Jsoup.parse(Files.readString(htmlFile));
                    var mainContent = doc.getElementById("main-content");
                    if (mainContent == null || mainContent.children().isEmpty()) {
                        violations.add(tempDir.relativize(htmlFile).toString());
                    }
                } catch (IOException e) {
                    violations.add("Parse error: " + htmlFile.getFileName());
                }
            });
        }

        assertTrue(violations.isEmpty(),
                "Pages with empty #main-content:\n" + String.join("\n", violations));
    }

    // --- E2E-04: Landing page tile links resolve ---

    @Test
    void whenSiteGenerated_thenLandingTilesResolve() throws IOException {
        var indexPath = tempDir.resolve("index.html");
        assertTrue(Files.exists(indexPath), "index.html must exist");

        var doc = Jsoup.parse(Files.readString(indexPath));
        var tiles = doc.select(".tile-card[href]");
        assertFalse(tiles.isEmpty(), "Landing page must have tile cards");

        var brokenTileLinks = new ArrayList<String>();
        for (var tile : tiles) {
            var href = tile.attr("href");
            if (isInternal(href)) {
                var resolved = tempDir.resolve(href).normalize();
                if (!Files.exists(resolved)) {
                    brokenTileLinks.add(tile.text() + " -> " + href);
                }
            }
        }

        assertTrue(brokenTileLinks.isEmpty(),
                "Broken tile card links:\n" + String.join("\n", brokenTileLinks));
    }

    // --- E2E-05: Links page has configured links ---

    @Test
    void whenSiteGenerated_thenLinksPageHasConfiguredLinks() throws IOException {
        var linksPath = tempDir.resolve("links.html");
        assertTrue(Files.exists(linksPath), "links.html must exist");

        var doc = Jsoup.parse(Files.readString(linksPath));
        var html = doc.html();

        assertTrue(html.contains("https://www.youtube.com/@CommunityTeamCup"),
                "Links page must contain YouTube channel URL");
        assertTrue(html.contains("https://discord.gg/example"),
                "Links page must contain Discord URL");
    }

    // --- E2E-06: YouTube footer link on multiple page types ---

    @Test
    void whenSiteGenerated_thenFooterYouTubePresentOnAllPageTypes() throws IOException {
        var seasonSlug = slugify(season.getDisplayLabel());
        var pagesToCheck = List.of(
                "index.html",
                "archive.html",
                "teams.html",
                "drivers.html",
                "season/" + seasonSlug + "/standings.html"
        );

        var missing = new ArrayList<String>();
        for (var page : pagesToCheck) {
            var pagePath = tempDir.resolve(page);
            if (!Files.exists(pagePath)) {
                missing.add(page + " (file not found)");
                continue;
            }
            var doc = Jsoup.parse(Files.readString(pagePath));
            if (doc.selectFirst("a[href*='youtube.com/@CommunityTeamCup']") == null) {
                missing.add(page);
            }
        }

        assertTrue(missing.isEmpty(),
                "Pages missing YouTube footer link:\n" + String.join("\n", missing));
    }

    // --- D-17: Overview pages have season filter ---

    @Test
    void whenSiteGenerated_thenOverviewPagesHaveSeasonFilter() throws IOException {
        var teamsPath = tempDir.resolve("teams.html");
        var driversPath = tempDir.resolve("drivers.html");
        assertTrue(Files.exists(teamsPath), "teams.html must exist");
        assertTrue(Files.exists(driversPath), "drivers.html must exist");

        var teamsDoc = Jsoup.parse(Files.readString(teamsPath));
        assertNotNull(teamsDoc.selectFirst("select#season-filter"),
                "teams.html must have a season-filter dropdown");

        var driversDoc = Jsoup.parse(Files.readString(driversPath));
        assertNotNull(driversDoc.selectFirst("select#season-filter"),
                "drivers.html must have a season-filter dropdown");
    }
}
