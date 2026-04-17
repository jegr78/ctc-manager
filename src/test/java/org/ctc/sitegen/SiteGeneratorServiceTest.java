package org.ctc.sitegen;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class SiteGeneratorServiceTest {

    private String uniqueSuffix;

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
    private RaceLineupRepository raceLineupRepository;

    @Autowired
    private PlayoffRepository playoffRepository;

    @Autowired
    private SeasonTeamRepository seasonTeamRepository;

    @Autowired
    private org.ctc.domain.service.ScoringService scoringService;

    @MockitoBean
    private YouTubeScraperService youTubeScraperService;

    @TempDir
    Path tempDir;

    private Season season;
    private Race testRace;
    private Driver driver1;

    @BeforeEach
    void setUp() {
        given(youTubeScraperService.scrapeVideoId(anyString(), anyString()))
                .willReturn("dQw4w9WgXcQ");

        uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // Deactivate any previously active seasons
        seasonRepository.findAll().forEach(s -> {
            if (s.isActive()) {
                s.setActive(false);
                seasonRepository.save(s);
            }
        });

        var raceScoring = raceScoringRepository.save(
                new RaceScoring("Gen RS " + uniqueSuffix, "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
        var matchScoring = matchScoringRepository.save(
                new MatchScoring("Gen MS " + uniqueSuffix, 3, 1, 0));

        season = new Season("Gen Season " + uniqueSuffix, 2026, 1);
        season.setActive(true);
        season.setRaceScoring(raceScoring);
        season.setMatchScoring(matchScoring);
        seasonRepository.save(season);

        var tnr = teamRepository.save(new Team("The Neutrals Racing " + uniqueSuffix, "GTNR" + uniqueSuffix));
        var p1r = teamRepository.save(new Team("Project One Racing " + uniqueSuffix, "GP1R" + uniqueSuffix));

        season.addTeam(tnr);
        season.addTeam(p1r);
        seasonRepository.save(season);

        driver1 = driverRepository.save(new Driver("gen_panic_" + uniqueSuffix, "panicpotato"));
        var driver2 = driverRepository.save(new Driver("gen_levit_" + uniqueSuffix, "LEVITIUS"));
        var driver3 = driverRepository.save(new Driver("gen_valky_" + uniqueSuffix, "P1R_Valkyrie"));
        var driver4 = driverRepository.save(new Driver("gen_motor_" + uniqueSuffix, "motorstormhero"));

        seasonDriverRepository.save(new SeasonDriver(season, driver1, tnr));
        seasonDriverRepository.save(new SeasonDriver(season, driver2, tnr));
        seasonDriverRepository.save(new SeasonDriver(season, driver3, p1r));
        seasonDriverRepository.save(new SeasonDriver(season, driver4, p1r));

        var matchday = matchdayRepository.save(new Matchday(season, "Spieltag 1", 1));
        var testTrack = trackRepository.save(new Track("Tsukuba " + uniqueSuffix, "Japan"));
        var testCar = carRepository.save(new Car("Mazda " + uniqueSuffix, "RX-Vision GT3"));
        var match = matchRepository.save(new Match(matchday, tnr, p1r));
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
        testRace = raceRepository.save(race);

        // Set match scores
        match.setHomeScore(r1.getPointsTotal() + r2.getPointsTotal());
        match.setAwayScore(r3.getPointsTotal() + r4.getPointsTotal());
        matchRepository.save(match);

        // Override output dir for test
        siteGeneratorService.setOutputDir(tempDir.toString());

        // Reset links to default (empty-state test mutates this singleton bean)
        var defaultLink = new SiteProperties.LinkEntry();
        defaultLink.setName("YouTube");
        defaultLink.setUrl("https://www.youtube.com/@CommunityTeamCup");
        siteProperties.setLinks(new java.util.ArrayList<>(List.of(defaultLink)));
    }

    private Path seasonDir() {
        return tempDir.resolve("season").resolve(slugify(season.getDisplayLabel()));
    }

    private String slugify(String input) {
        return siteGeneratorService.slugify(input);
    }

    @Test
    void givenActiveSeason_whenGenerate_thenCreatesIndexPage() {
        // when
        var result = siteGeneratorService.generate();

        // then
        assertFalse(result.hasErrors(), "Errors: " + result.getErrors());
        assertTrue(result.getPagesGenerated() > 0);
        assertTrue(Files.exists(tempDir.resolve("index.html")));
    }

    @Test
    void givenActiveSeason_whenGenerate_thenCreatesStandingsPage() {
        // when
        siteGeneratorService.generate();

        // then
        assertTrue(Files.exists(seasonDir().resolve("standings.html")), "standings.html should exist");
    }

    @Test
    void givenActiveSeason_whenGenerate_thenCreatesDriverRankingPage() {
        // when
        siteGeneratorService.generate();

        // then
        assertTrue(Files.exists(seasonDir().resolve("driver-ranking.html")), "driver-ranking.html should exist");
    }

    @Test
    void givenMatchdayData_whenGenerate_thenCreatesMatchdayPage() {
        // when
        siteGeneratorService.generate();

        // then
        assertTrue(Files.exists(seasonDir().resolve("matchday/spieltag-1.html")), "matchday page should exist");
    }

    @Test
    void givenTwoTeams_whenGenerate_thenCreatesTeamProfilePages() {
        // when
        siteGeneratorService.generate();

        // then
        var teamDir = seasonDir().resolve("team");
        assertTrue(Files.exists(teamDir), "team directory should exist");
        // Check that at least 2 team profiles were generated
        try (var files = Files.list(teamDir)) {
            assertTrue(files.count() >= 2, "Should have at least 2 team profiles");
        } catch (IOException e) {
            fail("Could not list team directory: " + e.getMessage());
        }
    }

    @Test
    void givenFourDrivers_whenGenerate_thenCreatesDriverProfilePages() {
        // when
        siteGeneratorService.generate();

        // then
        var driverDir = seasonDir().resolve("driver");
        assertTrue(Files.exists(driverDir), "driver directory should exist");
        try (var files = Files.list(driverDir)) {
            assertTrue(files.count() >= 4, "Should have at least 4 driver profiles");
        } catch (IOException e) {
            fail("Could not list driver directory: " + e.getMessage());
        }
    }

    @Test
    void givenActiveSeason_whenGenerate_thenCreatesArchivePage() {
        // when
        siteGeneratorService.generate();

        // then
        assertTrue(Files.exists(tempDir.resolve("archive.html")));
    }

    @Test
    void givenRaceResults_whenGenerate_thenStandingsPageContainsRows() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("standings.html"));
        var doc = Jsoup.parse(html);

        var rows = doc.select("tbody tr");
        assertFalse(rows.isEmpty(), "Standings table should have rows");
    }

    @Test
    void givenDriversWithPsnIds_whenGenerate_thenDriverRankingContainsDriverPsnIds() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("driver-ranking.html"));
        var doc = Jsoup.parse(html);

        var tableText = doc.select("table").text();
        assertTrue(tableText.contains("gen_panic_" + uniqueSuffix), "Ranking should contain driver PSN IDs");
    }

    @Test
    void givenGeneratedSite_whenReadIndexPage_thenContainsInternalLinks() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var indexHtml = Files.readString(tempDir.resolve("index.html"));
        var doc = Jsoup.parse(indexHtml);

        var links = doc.select("a[href]");
        assertFalse(links.isEmpty(), "Index page should contain links");
    }

    @Test
    void givenRaceLineupWithSubTeam_whenGenerate_thenDriverAttributedToSubTeam() throws IOException {
        // given — create sub-team and RaceLineup pointing driver1 to sub-team
        var subTeam = new Team("Sub Team " + uniqueSuffix, "GSUB" + uniqueSuffix);
        subTeam.setParentTeam(testRace.getHomeTeam());
        teamRepository.save(subTeam);
        raceLineupRepository.save(new RaceLineup(testRace, driver1, subTeam));

        // when
        siteGeneratorService.generate();

        // then — matchday page should show sub-team short name for driver1
        var html = Files.readString(seasonDir().resolve("matchday/spieltag-1.html"));
        assertTrue(html.contains("GSUB" + uniqueSuffix),
                "Driver1 should be attributed to sub-team via RaceLineup, not parent team");
    }

    @Test
    void givenByeRaceInSeason_whenGenerate_thenCompletesWithoutNPE() {
        // given — add a bye race on a separate matchday
        var byeMatchday = matchdayRepository.save(new Matchday(season, "Bye Matchday", 2));
        var homeTeam = teamRepository.findAll().stream()
                .filter(t -> t.getShortName().startsWith("GTNR"))
                .findFirst().orElseThrow();
        var byeMatch = new Match(byeMatchday, homeTeam, null);
        byeMatch.setBye(true);
        matchRepository.save(byeMatch);

        var byeRace = new Race();
        byeRace.setMatchday(byeMatchday);
        byeRace.setMatch(byeMatch);
        raceRepository.save(byeRace);

        // when
        var result = siteGeneratorService.generate();

        // then
        assertFalse(result.hasErrors(), "Bye race should not cause errors: " + result.getErrors());
        assertTrue(result.getPagesGenerated() > 0);
    }

    @Test
    void givenSeason_whenGenerate_thenArchiveContainsCorrectSeasonSlug() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(tempDir.resolve("archive.html"));
        var doc = Jsoup.parse(html);
        String expectedSlug = slugify(season.getDisplayLabel());
        var links = doc.select("a[href*='season/" + expectedSlug + "/standings.html']");
        assertFalse(links.isEmpty(),
                "Archive should link to season/" + expectedSlug + "/standings.html but found: "
                + doc.select("a[href*='season/']").stream().map(e -> e.attr("href")).toList());
    }

    @Test
    void givenActiveSeason_whenGenerate_thenNavDriverRankingLinksToActiveSeason() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(tempDir.resolve("index.html"));
        var doc = Jsoup.parse(html);
        String expectedSlug = slugify(season.getDisplayLabel());
        var links = doc.select("a[href*='season/" + expectedSlug + "/driver-ranking.html']");
        assertFalse(links.isEmpty(),
                "Nav Driver Ranking should link to season/" + expectedSlug + "/driver-ranking.html");
    }

    @Test
    void givenActiveSeason_whenGenerate_thenRootPagesHaveNoAbsolutePaths() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(tempDir.resolve("index.html"));
        var doc = Jsoup.parse(html);
        var absoluteLinks = doc.select("a[href^='/']");
        assertTrue(absoluteLinks.isEmpty(),
                "Root-level pages should have no absolute /... links but found: "
                + absoluteLinks.stream().map(e -> e.attr("href")).toList());
    }

    @Test
    void givenTeamWithLogo_whenGenerate_thenLogoCopiedAndLinkedRelatively() throws IOException {
        // given — create a fake logo file in a separate temp uploadDir
        // NOTE: uploadBase must be OUTSIDE tempDir (the output dir) so cleanOutputDirectory
        // does not delete it before generate() copies logos to assets.
        var uploadBase = Files.createTempDirectory("ctc-test-uploads-");
        var logoRelPath = "teams/test-uuid/test-logo.png";
        var logoFile = uploadBase.resolve(logoRelPath);
        Files.createDirectories(logoFile.getParent());
        Files.writeString(logoFile, "fake-png-content");

        // Set uploadDir on the service to point to our temp uploads
        siteGeneratorService.setUploadDir(uploadBase.toString());

        // Set logoUrl on first team (matching the /uploads/ prefix convention)
        var teams = teamRepository.findAll();
        var testTeam = teams.stream()
                .filter(t -> t.getShortName().startsWith("GTNR" + uniqueSuffix))
                .findFirst().orElseThrow();
        testTeam.setLogoUrl("/uploads/" + logoRelPath);
        teamRepository.save(testTeam);

        // when
        siteGeneratorService.generate();

        // then — logo file copied to assets
        var copiedLogo = tempDir.resolve("assets/img/logos/" + logoRelPath);
        assertTrue(Files.exists(copiedLogo),
                "Logo should be copied to assets/img/logos/" + logoRelPath);

        // then — team-profile HTML uses relative path, not /uploads/
        var teamProfileDir = seasonDir().resolve("team");
        var profileSlug = slugify(testTeam.getShortName());
        var profileHtml = Files.readString(teamProfileDir.resolve(profileSlug + ".html"));
        var profileDoc = Jsoup.parse(profileHtml);
        var logoImgs = profileDoc.select("img.team-logo");
        assertFalse(logoImgs.isEmpty(), "Team profile should have a logo image");
        var imgSrc = logoImgs.first().attr("src");
        assertFalse(imgSrc.startsWith("/uploads/"),
                "Logo src should be a relative static asset path, not /uploads/... but was: " + imgSrc);
        assertTrue(imgSrc.contains("img/logos/"),
                "Logo src should contain img/logos/ path but was: " + imgSrc);
    }

    // --- CONT-01: Season year/number display ---

    @Test
    void givenSeason_whenGenerate_thenStandingsHasSeasonMeta() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("standings.html"));
        var doc = Jsoup.parse(html);
        var meta = doc.select(".season-meta");
        assertFalse(meta.isEmpty(), ".season-meta element should exist on standings page");
        assertTrue(meta.text().contains("2026"), "season-meta should contain year 2026");
        assertTrue(meta.text().contains("#1"), "season-meta should contain season number #1");
    }

    @Test
    void givenSeason_whenGenerate_thenMatchdayHasSeasonMeta() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("matchday/spieltag-1.html"));
        var doc = Jsoup.parse(html);
        var meta = doc.select(".season-meta");
        assertFalse(meta.isEmpty(), ".season-meta element should exist on matchday page");
        assertTrue(meta.text().contains("2026"), "season-meta should contain year 2026");
        assertTrue(meta.text().contains("#1"), "season-meta should contain season number #1");
    }

    @Test
    void givenSeason_whenGenerate_thenDriverRankingHasSeasonMeta() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("driver-ranking.html"));
        var doc = Jsoup.parse(html);
        var meta = doc.select(".season-meta");
        assertFalse(meta.isEmpty(), ".season-meta element should exist on driver-ranking page");
        assertTrue(meta.text().contains("2026"), "season-meta should contain year 2026");
        assertTrue(meta.text().contains("#1"), "season-meta should contain season number #1");
    }

    @Test
    void givenSeason_whenGenerate_thenHeroContainsCommunityTeamCupTitle() throws IOException {
        // when
        siteGeneratorService.generate();

        // then — Phase 48: hero h1 must contain "COMMUNITY TEAM CUP" (D-09)
        var html = Files.readString(tempDir.resolve("index.html"));
        var doc = Jsoup.parse(html);
        var heroTitle = doc.selectFirst(".hero h1");
        assertNotNull(heroTitle, "Hero section must have an h1 element");
        assertTrue(heroTitle.text().toUpperCase().contains("COMMUNITY TEAM CUP"),
                "Hero h1 must contain 'COMMUNITY TEAM CUP' but was: " + heroTitle.text());
    }

    @Test
    void givenSeason_whenGenerate_thenArchiveShowsYearAndNumber() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(tempDir.resolve("archive.html"));
        var doc = Jsoup.parse(html);
        var meta = doc.select(".season-meta");
        assertFalse(meta.isEmpty(), ".season-meta element should exist in archive");
        assertTrue(meta.text().contains("2026"), "archive season-meta should contain year 2026");
        assertTrue(meta.text().contains("#1"), "archive season-meta should contain season number #1");
    }

    @Test
    void givenSeason_whenGenerate_thenTeamProfileHasSeasonMeta() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var teamDir = seasonDir().resolve("team");
        try (var files = Files.list(teamDir)) {
            var firstProfile = files.filter(p -> p.toString().endsWith(".html")).findFirst().orElseThrow();
            var html = Files.readString(firstProfile);
            var doc = Jsoup.parse(html);
            var meta = doc.select(".season-meta");
            assertFalse(meta.isEmpty(), ".season-meta element should exist on team profile page");
            assertTrue(meta.text().contains("2026"), "team profile season-meta should contain year 2026");
            assertTrue(meta.text().contains("#1"), "team profile season-meta should contain season number #1");
        }
    }

    @Test
    void givenSeason_whenGenerate_thenDriverProfileHasSeasonMeta() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var driverDir = seasonDir().resolve("driver");
        try (var files = Files.list(driverDir)) {
            var firstProfile = files.filter(p -> p.toString().endsWith(".html")).findFirst().orElseThrow();
            var html = Files.readString(firstProfile);
            var doc = Jsoup.parse(html);
            var meta = doc.select(".season-meta");
            assertFalse(meta.isEmpty(), ".season-meta element should exist on driver profile page");
            assertTrue(meta.text().contains("2026"), "driver profile season-meta should contain year 2026");
            assertTrue(meta.text().contains("#1"), "driver profile season-meta should contain season number #1");
        }
    }

    // --- CONT-06: Test season filtering ---

    @Test
    void givenTestSeason_whenGenerate_thenNoSeasonPagesCreated() {
        // given — create a second season whose name contains "Test"
        var testSeason = new Season("Test Throwaway " + uniqueSuffix, 2025, 99);
        testSeason.setRaceScoring(season.getRaceScoring());
        testSeason.setMatchScoring(season.getMatchScoring());
        seasonRepository.save(testSeason);
        var testSeasonDir = tempDir.resolve("season").resolve(
                slugify(testSeason.getDisplayLabel()));

        // when
        siteGeneratorService.generate();

        // then
        assertFalse(Files.exists(testSeasonDir),
                "Test season should not generate any pages");
    }

    @Test
    void givenTestSeason_whenGenerate_thenNotInArchive() throws IOException {
        // given — create a second season whose name contains "Test"
        var testSeason = new Season("Test Throwaway " + uniqueSuffix, 2025, 99);
        testSeason.setRaceScoring(season.getRaceScoring());
        testSeason.setMatchScoring(season.getMatchScoring());
        seasonRepository.save(testSeason);

        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(tempDir.resolve("archive.html"));
        var doc = Jsoup.parse(html);
        assertFalse(doc.select("tbody tr").stream()
                        .anyMatch(row -> row.text().contains("Test Throwaway")),
                "Test season should not appear in archive");
    }

    // --- CONT-07: Empty match-meta and period column ---

    @Test
    void givenRaceWithNoTrackOrCar_whenGenerate_thenMatchMetaAbsent() throws IOException {
        // given — remove track and car from the existing race
        testRace.setTrack(null);
        testRace.setCar(null);
        raceRepository.save(testRace);

        // when
        siteGeneratorService.generate();

        // then — check matchday page
        var html = Files.readString(seasonDir().resolve("matchday/spieltag-1.html"));
        var doc = Jsoup.parse(html);
        assertTrue(doc.select(".match-meta").isEmpty(),
                "match-meta should not render when both track and car are null");
    }

    @Test
    void givenRaceWithOnlyTrack_whenGenerate_thenMatchMetaPresent() throws IOException {
        // given — remove car but keep track
        testRace.setCar(null);
        raceRepository.save(testRace);

        // when
        siteGeneratorService.generate();

        // then — match-meta should still render with just the track
        var html = Files.readString(seasonDir().resolve("matchday/spieltag-1.html"));
        var doc = Jsoup.parse(html);
        assertFalse(doc.select(".match-meta").isEmpty(),
                "match-meta should render when track is present (even if car is null)");
    }

    @Test
    void givenSeasonWithNoDates_whenGenerate_thenPeriodCellEmpty() throws IOException {
        // given — setUp() season has null startDate and endDate by default

        // when
        siteGeneratorService.generate();

        // then — archive period cell should be empty (no "null" text, no orphaned separator)
        var html = Files.readString(tempDir.resolve("archive.html"));
        var doc = Jsoup.parse(html);
        var rows = doc.select("tbody tr");
        assertFalse(rows.isEmpty(), "archive should have at least one season row");
        // Find the period cell for our season — check all tds for "null" text
        for (var row : rows) {
            var cells = row.select("td");
            for (var cell : cells) {
                assertFalse(cell.text().contains("null"),
                        "Period cell should not contain 'null' text; found: " + cell.text());
            }
        }
    }

    // --- CONT-02, CONT-03, CONT-04, CONT-08: Entity cross-links ---

    @Test
    void givenTeamInStandings_whenGenerate_thenTeamNameLinksToTeamProfile() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("standings.html"));
        var doc = Jsoup.parse(html);
        var teamLinks = doc.select("tbody td a.entity-link[href*='team/']");
        assertFalse(teamLinks.isEmpty(), "Standings should contain team profile links");
        assertTrue(teamLinks.stream().anyMatch(a -> a.attr("href").endsWith(".html")),
                "Team links should end with .html");
    }

    @Test
    void givenDriverInRanking_whenGenerate_thenDriverPsnIdLinksToDriverProfile() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("driver-ranking.html"));
        var doc = Jsoup.parse(html);
        var driverLinks = doc.select("tbody td a.entity-link[href*='driver/']");
        assertFalse(driverLinks.isEmpty(), "Driver ranking should contain driver profile links");
        assertTrue(driverLinks.stream().anyMatch(a -> a.attr("href").endsWith(".html")),
                "Driver links should end with .html");
    }

    @Test
    void givenRaceResults_whenGenerate_thenMatchdayDriverNamesLinkToDriverProfiles() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("matchday/spieltag-1.html"));
        var doc = Jsoup.parse(html);
        var driverLinks = doc.select("td a.entity-link[href*='driver/']");
        assertFalse(driverLinks.isEmpty(), "Matchday results should contain driver profile links");
    }

    @Test
    void givenTeamWithDrivers_whenGenerate_thenTeamProfileHasDriversSectionWithLinks() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var teamDir = seasonDir().resolve("team");
        try (var files = Files.list(teamDir)) {
            var firstProfile = files.filter(p -> p.toString().endsWith(".html")).findFirst().orElseThrow();
            var doc = Jsoup.parse(Files.readString(firstProfile));
            var driversHeading = doc.select("h2.section-title:contains(Drivers)");
            assertFalse(driversHeading.isEmpty(), "Team profile should have a 'Drivers' section heading");
            var driverLinks = doc.select("a.entity-link[href*='driver/']");
            assertFalse(driverLinks.isEmpty(), "Team profile should contain driver profile links");
        }
    }

    // Removed: givenActiveSeason_whenGenerate_thenIndexStandingsTeamNamesLinkToTeamProfiles (Phase 48: D-14 — standings table removed from index)
    // Removed: givenActiveSeason_whenGenerate_thenIndexDoesNotRenderMatchResults (Phase 48: D-15 — replaced by whenGenerate_thenIndexHasNoMatchGrid)

    // --- CONT-05: Season subnav, matchday index page ---

    @Test
    void givenSeason_whenGenerate_thenStandingsHasSubnav() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("standings.html"));
        var doc = Jsoup.parse(html);
        assertFalse(doc.select(".subnav").isEmpty(), "Season pages should have subnav");
        assertFalse(doc.select(".subnav-link").isEmpty(), "Season pages should have subnav links");
        assertEquals(1, doc.select(".subnav-link[href*='standings.html']").size(), "Subnav should contain Standings link");
        assertEquals(1, doc.select(".subnav-link[href*='matchdays.html']").size(), "Subnav should contain Matchdays link");
        assertEquals(1, doc.select(".subnav-link[href*='driver-ranking.html']").size(), "Subnav should contain Driver Ranking link");
    }

    @Test
    void givenSeason_whenGenerate_thenCreatesMatchdayIndexPage() {
        // when
        siteGeneratorService.generate();

        // then
        assertTrue(Files.exists(seasonDir().resolve("matchdays.html")),
                "matchdays.html should exist in season directory");
    }

    @Test
    void givenSeason_whenGenerate_thenSubnavMatchdaysLinkCorrect() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("standings.html"));
        var doc = Jsoup.parse(html);
        var matchdaysLinks = doc.select(".subnav-link[href*='matchdays.html']");
        assertFalse(matchdaysLinks.isEmpty(),
                "Subnav should contain a link to matchdays.html");
    }

    // --- UX-02: Active nav state ---

    @Test
    void givenStandingsPage_whenGenerate_thenStandingsNavItemActive() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("standings.html"));
        var doc = Jsoup.parse(html);
        var activeLinks = doc.select(".subnav-link.active");
        assertEquals(1, activeLinks.size(), "Exactly one subnav item should be active");
        assertEquals("Standings", activeLinks.first().text());
    }

    // --- UX-03: Breadcrumbs ---

    @Test
    void givenSeason_whenGenerate_thenStandingsHasBreadcrumb() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("standings.html"));
        var doc = Jsoup.parse(html);
        assertFalse(doc.select(".breadcrumb").isEmpty(), "Season pages should have breadcrumb");
        assertFalse(doc.select(".breadcrumb-current").isEmpty(),
                "Breadcrumb should have a current-page item");
    }

    @Test
    void givenSeason_whenGenerate_thenBreadcrumbCurrentNotLink() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("standings.html"));
        var doc = Jsoup.parse(html);
        var current = doc.select(".breadcrumb-current");
        assertFalse(current.isEmpty(), "Breadcrumb current item should exist");
        assertTrue(current.select("a").isEmpty(),
                "Breadcrumb current item should NOT be a link");
        assertTrue(current.text().contains("Standings"),
                "Breadcrumb current should say 'Standings'");
    }

    @Test
    void givenArchivePage_whenGenerate_thenNoBreadcrumb() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(tempDir.resolve("archive.html"));
        var doc = Jsoup.parse(html);
        assertTrue(doc.select(".breadcrumb").isEmpty(),
                "Archive page should have no breadcrumb");
    }

    // --- UX-01: Skip-link ---

    @Test
    void givenLayout_whenGenerate_thenSkipLinkIsFirstBodyChild() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(tempDir.resolve("index.html"));
        var doc = Jsoup.parse(html);
        var firstBodyChild = doc.body().children().first();
        assertNotNull(firstBodyChild, "Body should have children");
        assertEquals("a", firstBodyChild.tagName(), "First body child should be <a> skip-link");
        assertEquals("#main-content", firstBodyChild.attr("href"), "Skip-link should target #main-content");
        assertTrue(firstBodyChild.hasClass("skip-link"), "Skip-link should have class 'skip-link'");
    }

    // --- UX-04: Winner highlight ---

    @Test
    void givenRaceWithResults_whenGenerate_thenMatchdayShowsWinnerHighlight() throws IOException {
        // when
        siteGeneratorService.generate();

        // then — find the matchday HTML file in the season directory
        var seasonDir = seasonDir();
        var matchdayDir = seasonDir.resolve("matchday");
        List<Path> matchdayFiles;
        try (var stream = Files.list(matchdayDir)) {
            matchdayFiles = stream
                    .filter(p -> p.toString().endsWith(".html"))
                    .toList();
        }
        assertFalse(matchdayFiles.isEmpty(), "Should have at least one matchday HTML file");

        var html = Files.readString(matchdayFiles.getFirst());
        var doc = Jsoup.parse(html);
        var winners = doc.select(".match-team-winner");
        assertFalse(winners.isEmpty(), "Matchday should show at least one winner highlight when race has results");
    }

    // --- UX-06: Footer links ---

    @Test
    void givenActiveSeason_whenGenerate_thenFooterContainsUsefulLinks() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(tempDir.resolve("index.html"));
        var doc = Jsoup.parse(html);
        var footerLinks = doc.select(".footer .footer-link");
        assertFalse(footerLinks.isEmpty(), "Footer should contain .footer-link elements");
        assertTrue(footerLinks.stream().anyMatch(a -> "#".equals(a.attr("href"))),
                "Footer should have a 'Top' link with href='#'");
        assertTrue(footerLinks.stream().anyMatch(a -> a.attr("href").contains("archive.html")),
                "Footer should have an Archive link");
    }

    // --- LINK-05, LINK-06: Footer YouTube link ---

    @Test
    void givenLayout_whenGenerate_thenFooterContainsYouTubeLink() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(tempDir.resolve("index.html"));
        var doc = Jsoup.parse(html);
        var youtubeLink = doc.selectFirst(".footer .footer-link[href='https://www.youtube.com/@CommunityTeamCup']");
        assertNotNull(youtubeLink, "Footer should contain YouTube link");
        assertEquals("_blank", youtubeLink.attr("target"), "YouTube link must open in new tab");
        assertEquals("noopener", youtubeLink.attr("rel"), "YouTube link must have rel=noopener");
        assertEquals("YouTube", youtubeLink.text(), "YouTube link text must be 'YouTube'");
    }

    @Test
    void givenSeasonPage_whenGenerate_thenFooterContainsYouTubeLink() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("standings.html"));
        var doc = Jsoup.parse(html);
        var youtubeLink = doc.selectFirst(".footer .footer-link[href='https://www.youtube.com/@CommunityTeamCup']");
        assertNotNull(youtubeLink, "Season subpage footer should contain YouTube link");
        assertEquals("_blank", youtubeLink.attr("target"), "YouTube link must open in new tab");
        assertEquals("noopener", youtubeLink.attr("rel"), "YouTube link must have rel=noopener");
        assertEquals("YouTube", youtubeLink.text(), "YouTube link text must be 'YouTube'");
    }

    // --- UX-07: Nav toggle aria-label ---

    @Test
    void givenLayout_whenGenerate_thenNavToggleLabelHasAriaLabel() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(tempDir.resolve("index.html"));
        var doc = Jsoup.parse(html);
        var label = doc.selectFirst("label.nav-toggle-label");
        assertNotNull(label, "Nav toggle label should exist");
        assertEquals("Toggle navigation menu", label.attr("aria-label"),
                "Nav toggle label should have correct aria-label");
        assertEquals("button", label.attr("role"),
                "Nav toggle label should have role=button");

        // Verify aria-label is NOT on the input
        var input = doc.selectFirst("input.nav-toggle-input");
        assertNotNull(input, "Nav toggle input should exist");
        assertTrue(input.attr("aria-label").isEmpty(),
                "Nav toggle input should NOT have aria-label");
    }

    // --- Phase 42: Navigation Gap Closure ---

    // UX-02: Top-nav active state for index and archive pages

    // Removed: givenIndexPage_whenGenerate_thenStandingsTopNavActive (Phase 48: D-19 — currentPage changed to "home", index no longer highlights any nav item)

    @Test
    void givenArchivePage_whenGenerate_thenArchiveTopNavActive() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(tempDir.resolve("archive.html"));
        var doc = Jsoup.parse(html);
        var activeTopNavLinks = doc.select(".nav-links .nav-link-active");
        assertFalse(activeTopNavLinks.isEmpty(),
                "Archive page should have an active top-nav item");
        assertEquals("Archive", activeTopNavLinks.first().text(),
                "Archive page should highlight Archive in top-nav");
    }

    // CONT-05: Playoff subnav link guard

    @Test
    void givenSeasonWithoutPlayoff_whenGenerate_thenSubnavHasNoPlayoffLink() throws IOException {
        // given — default test season has no playoff record

        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("standings.html"));
        var doc = Jsoup.parse(html);
        var playoffLinks = doc.select(".subnav-link[href*='playoff.html']");
        assertTrue(playoffLinks.isEmpty(),
                "Season without playoff should NOT show Playoff link in subnav");
    }

    @Test
    void givenSeasonWithPlayoff_whenGenerate_thenSubnavHasPlayoffLink() throws IOException {
        // given
        var playoff = new Playoff(season, "Playoff " + uniqueSuffix);
        playoffRepository.save(playoff);

        // when
        siteGeneratorService.generate();

        // then
        var html = Files.readString(seasonDir().resolve("standings.html"));
        var doc = Jsoup.parse(html);
        var playoffLinks = doc.select(".subnav-link[href*='playoff.html']");
        assertEquals(1, playoffLinks.size(),
                "Season with playoff should show Playoff link in subnav");
    }

    // --- Phase 44: Clean Output Directory ---

    // CLEAN-01: Stale file removal

    @Test
    void givenStaleFile_whenGenerate_thenStaleFileIsRemoved() throws IOException {
        // given
        var staleFile = tempDir.resolve("stale-page.html");
        Files.writeString(staleFile, "<html>stale</html>");

        // when
        var result = siteGeneratorService.generate();

        // then
        assertFalse(result.hasErrors(), "Errors: " + result.getErrors());
        assertFalse(Files.exists(staleFile), "Stale file should be removed before generation");
    }

    @Test
    void givenStaleNestedDirectory_whenGenerate_thenNestedDirectoryIsRemoved() throws IOException {
        // given
        var staleDir = tempDir.resolve("old-season").resolve("old-subdir");
        Files.createDirectories(staleDir);
        Files.writeString(staleDir.resolve("old-page.html"), "<html>old</html>");

        // when
        siteGeneratorService.generate();

        // then
        assertFalse(Files.exists(staleDir), "Stale nested directory should be removed");
        assertFalse(Files.exists(staleDir.getParent()), "Stale parent directory should be removed");
    }

    // CLEAN-02: Non-existent output directory

    @Test
    void givenNonExistentOutputDir_whenGenerate_thenCreatesAndGeneratesPages() throws IOException {
        // given
        var freshDir = tempDir.resolve("fresh-output");
        siteGeneratorService.setOutputDir(freshDir.toString());

        // when
        var result = siteGeneratorService.generate();

        // then
        assertFalse(result.hasErrors(), "Errors: " + result.getErrors());
        assertTrue(result.getPagesGenerated() > 0);
        assertTrue(Files.exists(freshDir.resolve("index.html")));
    }

    // --- Phase 46: Configurable Links Page ---

    // LINK-07: links.html page exists

    @Test
    void whenGenerate_thenCreatesLinksPage() {
        // when
        var result = siteGeneratorService.generate();

        // then
        assertFalse(result.hasErrors(), "Errors: " + result.getErrors());
        assertTrue(Files.exists(tempDir.resolve("links.html")),
                "links.html should exist in output root");
    }

    // LINK-08, LINK-09: Configured links render as clickable elements

    @Test
    void whenGenerate_thenLinksPageContainsConfiguredLinks() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var linksFile = tempDir.resolve("links.html");
        assertTrue(Files.exists(linksFile), "links.html must exist");
        var doc = Jsoup.parse(Files.readString(linksFile));
        var linkCards = doc.select(".link-card");
        assertFalse(linkCards.isEmpty(), "Links page should contain at least one .link-card");
        var firstLink = linkCards.first().selectFirst("a");
        assertNotNull(firstLink, "Link card should contain an <a> element");
        assertEquals("_blank", firstLink.attr("target"), "External link must open in new tab");
        assertEquals("noopener", firstLink.attr("rel"), "External link must have rel=noopener");
    }

    // LINK-09: Empty state shows message

    @Test
    void givenNoLinksConfigured_whenGenerate_thenLinksPageShowsEmptyState() throws IOException {
        // given
        siteProperties.setLinks(List.of());

        // when
        siteGeneratorService.generate();

        // then
        var linksFile = tempDir.resolve("links.html");
        assertTrue(Files.exists(linksFile), "links.html must exist even with no configured links");
        var html = Files.readString(linksFile);
        assertTrue(html.contains("No links configured."), "Empty state message should appear");
        var doc = Jsoup.parse(html);
        assertTrue(doc.select(".link-card").isEmpty(), "No link cards should render when links list is empty");
    }

    // LINK-10: Shared layout (nav, footer) on links page

    @Test
    void whenGenerate_thenLinksPageHasSharedLayout() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var linksFile = tempDir.resolve("links.html");
        assertTrue(Files.exists(linksFile), "links.html must exist");
        var doc = Jsoup.parse(Files.readString(linksFile));
        assertNotNull(doc.selectFirst("nav.nav"), "Links page must have top navigation");
        assertNotNull(doc.selectFirst("footer.footer"), "Links page must have footer");
        assertNotNull(doc.selectFirst(".breadcrumb"), "Links page must have breadcrumbs");
    }

    // --- Phase 47: Teams & Drivers Overview Pages ---

    // OVER-01: teams.html exists

    @Test
    void whenGenerate_thenCreatesTeamsOverviewPage() {
        // when
        siteGeneratorService.generate();

        // then
        assertTrue(Files.exists(tempDir.resolve("teams.html")),
                "teams.html must exist in output root");
    }

    // OVER-02: drivers.html exists

    @Test
    void whenGenerate_thenCreatesDriversOverviewPage() {
        // when
        siteGeneratorService.generate();

        // then
        assertTrue(Files.exists(tempDir.resolve("drivers.html")),
                "drivers.html must exist in output root");
    }

    // OVER-03: season filter on teams page

    @Test
    void givenMultipleSeasons_whenGenerate_thenTeamsPageHasSeasonFilter() throws IOException {
        // given — create a second production season with a team
        var season2 = new Season("Second Season " + uniqueSuffix, 2025, 1);
        season2.setRaceScoring(season.getRaceScoring());
        season2.setMatchScoring(season.getMatchScoring());
        seasonRepository.save(season2);
        var extraTeam = teamRepository.save(new Team("Filter Team " + uniqueSuffix, "FLT" + uniqueSuffix));
        season2.addTeam(extraTeam);
        seasonRepository.save(season2);

        // when
        siteGeneratorService.generate();

        // then
        var doc = Jsoup.parse(Files.readString(tempDir.resolve("teams.html")));
        assertNotNull(doc.selectFirst("select#season-filter"), "Season filter dropdown must exist");
        assertFalse(doc.select("select#season-filter option").isEmpty(), "Filter must have option elements");
        assertFalse(doc.select(".overview-card[data-seasons]").isEmpty(), "Cards must have data-seasons attribute");
    }

    // OVER-04: team names and season tags

    @Test
    void givenTeams_whenGenerate_thenTeamsOverviewShowsNamesAndSeasons() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var doc = Jsoup.parse(Files.readString(tempDir.resolve("teams.html")));
        var cards = doc.select(".overview-card");
        assertFalse(cards.isEmpty(), "Teams overview must contain overview cards");
        var html = doc.html();
        assertTrue(html.contains("GTNR" + uniqueSuffix) || html.contains("GP1R" + uniqueSuffix),
                "Teams overview must show team short names");
        assertFalse(doc.select(".season-tag").isEmpty(), "Teams overview must show season tags");
    }

    // OVER-05: driver PSN IDs and team names

    @Test
    void givenDrivers_whenGenerate_thenDriversOverviewShowsPsnIdAndTeams() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var doc = Jsoup.parse(Files.readString(tempDir.resolve("drivers.html")));
        var cards = doc.select(".overview-card");
        assertFalse(cards.isEmpty(), "Drivers overview must contain overview cards");
        var html = doc.html();
        assertTrue(html.contains("gen_panic_" + uniqueSuffix),
                "Drivers overview must show driver PSN ID");
        assertTrue(html.contains("GTNR" + uniqueSuffix) || html.contains("GP1R" + uniqueSuffix),
                "Drivers overview must show team name(s)");
    }

    // OVER-06: profile links resolve to season-specific paths

    @Test
    void givenTeamsAndDrivers_whenGenerate_thenOverviewLinksResolveToSeasonProfiles() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var teamsDoc = Jsoup.parse(Files.readString(tempDir.resolve("teams.html")));
        var teamLinks = teamsDoc.select(".overview-card a[href]");
        assertFalse(teamLinks.isEmpty(), "Team cards must have profile links");
        for (var link : teamLinks) {
            assertTrue(link.attr("href").startsWith("season/"),
                    "Team profile link must start with season/: " + link.attr("href"));
            assertTrue(link.attr("href").contains("/team/"),
                    "Team profile link must contain /team/: " + link.attr("href"));
        }

        var driversDoc = Jsoup.parse(Files.readString(tempDir.resolve("drivers.html")));
        var driverLinks = driversDoc.select(".overview-card a[href]");
        assertFalse(driverLinks.isEmpty(), "Driver cards must have profile links");
        for (var link : driverLinks) {
            assertTrue(link.attr("href").startsWith("season/"),
                    "Driver profile link must start with season/: " + link.attr("href"));
            assertTrue(link.attr("href").contains("/driver/"),
                    "Driver profile link must contain /driver/: " + link.attr("href"));
        }
    }

    // D-01 guard: sub-teams excluded from teams overview

    @Test
    void givenSubTeam_whenGenerate_thenSubTeamExcludedFromTeamsOverview() throws IOException {
        // given — create a sub-team of an existing team and add it to the season
        var parentTeam = teamRepository.findAll().stream()
                .filter(t -> t.getShortName().startsWith("GTNR" + uniqueSuffix))
                .findFirst().orElseThrow();
        var subTeam = teamRepository.save(new Team("Sub Alpha " + uniqueSuffix, "SUBA" + uniqueSuffix, parentTeam));
        seasonTeamRepository.save(new SeasonTeam(season, subTeam));

        // when
        siteGeneratorService.generate();

        // then
        var doc = Jsoup.parse(Files.readString(tempDir.resolve("teams.html")));
        var html = doc.html();
        assertFalse(html.contains("SUBA" + uniqueSuffix),
                "Sub-team must NOT appear in teams overview (D-01)");
    }

    // D-04 guard: test season not in overview filter

    @Test
    void givenTestSeason_whenGenerate_thenTestSeasonNotInOverviewFilter() throws IOException {
        // given — create a season with "Test" in name
        var testSeason = new Season("Test League " + uniqueSuffix, 2024, 1);
        testSeason.setRaceScoring(season.getRaceScoring());
        testSeason.setMatchScoring(season.getMatchScoring());
        seasonRepository.save(testSeason);
        var extraTeam = teamRepository.save(new Team("Test Only Team " + uniqueSuffix, "TOT" + uniqueSuffix));
        testSeason.addTeam(extraTeam);
        seasonRepository.save(testSeason);

        // when
        siteGeneratorService.generate();

        // then
        var doc = Jsoup.parse(Files.readString(tempDir.resolve("teams.html")));
        var options = doc.select("select#season-filter option");
        for (var option : options) {
            assertFalse(option.text().contains("Test League"),
                    "Test season must NOT appear in season filter (D-04)");
        }
    }

    // --- Phase 48: Landing Page Redesign ---

    // LAND-01/YT-01: Index page has YouTube iFrame Player API setup
    @Test
    void givenActiveSeason_whenGenerate_thenIndexHasYouTubePlayerApi() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var doc = Jsoup.parse(Files.readString(tempDir.resolve("index.html")));
        var playerDiv = doc.select("#yt-hero-player");
        assertFalse(playerDiv.isEmpty(), "Index page must have yt-hero-player div for iFrame Player API");
        var overlay = doc.select(".landing-hero-overlay");
        assertFalse(overlay.isEmpty(), "Index page must have landing-hero-overlay div");
        var scripts = doc.select("script");
        var hasApiScript = scripts.stream()
                .anyMatch(s -> s.data().contains("onYouTubeIframeAPIReady"));
        assertTrue(hasApiScript, "Index page must have onYouTubeIframeAPIReady script");
    }

    // LAND-02: Index page has 5 tile cards
    @Test
    void givenActiveSeason_whenGenerate_thenIndexHasFiveTiles() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var doc = Jsoup.parse(Files.readString(tempDir.resolve("index.html")));
        var tiles = doc.select(".tile-card");
        assertEquals(5, tiles.size(), "Index page must have 5 tile cards");
    }

    // LAND-03a: Index page has no standings table
    @Test
    void whenGenerate_thenIndexHasNoStandingsTable() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var doc = Jsoup.parse(Files.readString(tempDir.resolve("index.html")));
        assertTrue(doc.select("table").isEmpty(), "Index page must not contain a standings table");
    }

    // LAND-03b: Index page has no match-grid
    @Test
    void whenGenerate_thenIndexHasNoMatchGrid() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var doc = Jsoup.parse(Files.readString(tempDir.resolve("index.html")));
        assertTrue(doc.select(".match-grid").isEmpty(), "Index page must not contain a match grid");
    }

    // LAND-04: Standings tile links to active season standings page
    @Test
    void givenActiveSeason_whenGenerate_thenStandingsTileLinkCorrect() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var doc = Jsoup.parse(Files.readString(tempDir.resolve("index.html")));
        String expectedSlug = slugify(season.getDisplayLabel());
        var link = doc.selectFirst(".tile-card[href*='season/" + expectedSlug + "/standings.html']");
        assertNotNull(link, "Standings tile must link to active season standings page");
    }

    // D-19: Index page (home) does not highlight any top-nav item
    @Test
    void givenIndexPage_whenGenerate_thenNoTopNavItemActive() throws IOException {
        // when
        siteGeneratorService.generate();

        // then
        var doc = Jsoup.parse(Files.readString(tempDir.resolve("index.html")));
        var activeTopNavLinks = doc.select(".nav-links .nav-link-active");
        assertTrue(activeTopNavLinks.isEmpty(),
                "Index (home) page should not highlight any top-nav item");
    }

    // --- Phase 50: OVER-06 guard — 0-game team broken link prevention ---

    @Test
    void givenTeamWithZeroGames_whenGenerate_thenTeamsOverviewDoesNotLinkToMissingProfile() throws IOException {
        // given — add a third team with no races/results (0 played games)
        var zeroGameTeam = teamRepository.save(new Team("Zero Games Team " + uniqueSuffix, "GZGT" + uniqueSuffix));
        // Use repository directly to avoid duplicate SeasonTeam inserts via cascade
        seasonTeamRepository.save(new SeasonTeam(season, zeroGameTeam));

        // when
        siteGeneratorService.generate();

        // then — team should appear in teams.html text but NOT as a clickable profile link
        var doc = Jsoup.parse(Files.readString(tempDir.resolve("teams.html")));
        var html = doc.html();
        assertTrue(html.contains("GZGT" + uniqueSuffix),
                "0-game team should still appear in teams overview");

        // The team profile page should NOT exist (generateTeamProfiles skips 0-played teams)
        var seasonSlug = slugify(season.getDisplayLabel());
        var profilePath = tempDir.resolve("season/" + seasonSlug + "/team/" + slugify("GZGT" + uniqueSuffix) + ".html");
        assertFalse(Files.exists(profilePath),
                "0-game team should NOT have a generated profile page");

        // The teams.html must NOT contain a link to a non-existent profile for this team
        var brokenLinks = doc.select("a[href*='" + slugify("GZGT" + uniqueSuffix) + "']");
        assertTrue(brokenLinks.isEmpty(),
                "teams.html must NOT link to non-existent profile for 0-game team, but found: "
                + brokenLinks.stream().map(e -> e.attr("href")).toList());
    }
}
