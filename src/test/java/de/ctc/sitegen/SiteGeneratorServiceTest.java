package de.ctc.sitegen;

import de.ctc.domain.model.*;
import de.ctc.domain.repository.*;
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
    private de.ctc.domain.service.ScoringService scoringService;

    @TempDir
    Path tempDir;

    private Season season;

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

        season = new Season("Gen Test " + uniqueSuffix);
        season.setActive(true);
        seasonRepository.save(season);

        var tnr = teamRepository.save(new Team("The Neutrals Racing " + uniqueSuffix, "GTNR" + uniqueSuffix));
        var p1r = teamRepository.save(new Team("Project One Racing " + uniqueSuffix, "GP1R" + uniqueSuffix));

        var driver1 = driverRepository.save(new Driver("gen_panic_" + uniqueSuffix, "panicpotato"));
        var driver2 = driverRepository.save(new Driver("gen_levit_" + uniqueSuffix, "LEVITIUS"));
        var driver3 = driverRepository.save(new Driver("gen_valky_" + uniqueSuffix, "P1R_Valkyrie"));
        var driver4 = driverRepository.save(new Driver("gen_motor_" + uniqueSuffix, "motorstormhero"));

        seasonDriverRepository.save(new SeasonDriver(season, driver1, tnr));
        seasonDriverRepository.save(new SeasonDriver(season, driver2, tnr));
        seasonDriverRepository.save(new SeasonDriver(season, driver3, p1r));
        seasonDriverRepository.save(new SeasonDriver(season, driver4, p1r));

        var matchday = matchdayRepository.save(new Matchday(season, "Spieltag 1", 1));
        var race = new Race(matchday, tnr, p1r);
        race.setTrack("Tsukuba");
        race.setCar("Mazda RX-Vision GT3");

        var r1 = new RaceResult(race, driver1, 1, 1, false);
        var r2 = new RaceResult(race, driver2, 3, 3, false);
        var r3 = new RaceResult(race, driver3, 2, 2, true);
        var r4 = new RaceResult(race, driver4, 4, 4, false);
        scoringService.calculatePoints(r1);
        scoringService.calculatePoints(r2);
        scoringService.calculatePoints(r3);
        scoringService.calculatePoints(r4);
        race.getResults().add(r1);
        race.getResults().add(r2);
        race.getResults().add(r3);
        race.getResults().add(r4);
        raceRepository.save(race);

        // Override output dir for test
        siteGeneratorService.setOutputDir(tempDir.toString());
    }

    private Path seasonDir() {
        return tempDir.resolve("season").resolve(slugify("Gen Test " + uniqueSuffix));
    }

    private String slugify(String input) {
        return input.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    @Test
    void shouldGenerateIndexPage() {
        var result = siteGeneratorService.generate();

        assertFalse(result.hasErrors(), "Errors: " + result.getErrors());
        assertTrue(result.getPagesGenerated() > 0);
        assertTrue(Files.exists(tempDir.resolve("index.html")));
    }

    @Test
    void shouldGenerateStandingsPage() {
        siteGeneratorService.generate();
        assertTrue(Files.exists(seasonDir().resolve("standings.html")), "standings.html should exist");
    }

    @Test
    void shouldGenerateDriverRankingPage() {
        siteGeneratorService.generate();
        assertTrue(Files.exists(seasonDir().resolve("driver-ranking.html")), "driver-ranking.html should exist");
    }

    @Test
    void shouldGenerateMatchdayPage() {
        siteGeneratorService.generate();
        assertTrue(Files.exists(seasonDir().resolve("matchday/spieltag-1.html")), "matchday page should exist");
    }

    @Test
    void shouldGenerateTeamProfilePages() {
        siteGeneratorService.generate();

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
    void shouldGenerateDriverProfilePages() {
        siteGeneratorService.generate();

        var driverDir = seasonDir().resolve("driver");
        assertTrue(Files.exists(driverDir), "driver directory should exist");
        try (var files = Files.list(driverDir)) {
            assertTrue(files.count() >= 4, "Should have at least 4 driver profiles");
        } catch (IOException e) {
            fail("Could not list driver directory: " + e.getMessage());
        }
    }

    @Test
    void shouldGenerateArchivePage() {
        siteGeneratorService.generate();
        assertTrue(Files.exists(tempDir.resolve("archive.html")));
    }

    @Test
    void shouldContainCorrectStandingsData() throws IOException {
        siteGeneratorService.generate();

        var html = Files.readString(seasonDir().resolve("standings.html"));
        var doc = Jsoup.parse(html);

        var rows = doc.select("tbody tr");
        assertFalse(rows.isEmpty(), "Standings table should have rows");
    }

    @Test
    void shouldContainCorrectDriverRankingData() throws IOException {
        siteGeneratorService.generate();

        var html = Files.readString(seasonDir().resolve("driver-ranking.html"));
        var doc = Jsoup.parse(html);

        var tableText = doc.select("table").text();
        assertTrue(tableText.contains("gen_panic_" + uniqueSuffix), "Ranking should contain driver PSN IDs");
    }

    @Test
    void shouldHaveWorkingInternalLinks() throws IOException {
        siteGeneratorService.generate();

        var indexHtml = Files.readString(tempDir.resolve("index.html"));
        var doc = Jsoup.parse(indexHtml);

        var links = doc.select("a[href]");
        assertFalse(links.isEmpty(), "Index page should contain links");
    }
}
