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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
class SiteGeneratorServiceTest {

    private String uniqueSuffix;

    @Autowired
    private SiteGeneratorService siteGeneratorService;

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
    private org.ctc.domain.service.ScoringService scoringService;

    @TempDir
    Path tempDir;

    private Season season;
    private Race testRace;
    private Driver driver1;

    @BeforeEach
    void setUp() {
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

        season = new Season("Gen Test " + uniqueSuffix, 2026, 1);
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
    }

    private Path seasonDir() {
        return tempDir.resolve("season").resolve(slugify(season.getDisplayLabel()));
    }

    private String slugify(String input) {
        return input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
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
}
