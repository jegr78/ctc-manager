package org.ctc.admin.controller;

import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class RaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private MatchdayRepository matchdayRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private RaceRepository raceRepository;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private TrackRepository trackRepository;

    @Autowired
    private RaceScoringRepository raceScoringRepository;

    @Autowired
    private MatchScoringRepository matchScoringRepository;

    @Autowired
    private SeasonDriverRepository seasonDriverRepository;

    @Autowired
    private RaceAttachmentRepository raceAttachmentRepository;

    @Autowired
    private TestHelper testHelper;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private Season season;
    private Matchday matchday;
    private Team home;
    private Team away;
    private Race race;

    @BeforeEach
    void setUp() {
        var rs = raceScoringRepository.save(new RaceScoring("RT RS " + java.util.UUID.randomUUID().toString().substring(0, 4), "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
        var ms = matchScoringRepository.save(new MatchScoring("RT MS " + java.util.UUID.randomUUID().toString().substring(0, 4), 3, 1, 0));
        var s = new Season("Race Test Season");
        s.setRaceScoring(rs);
        s.setMatchScoring(ms);
        season = seasonRepository.save(s);
        matchday = matchdayRepository.save(new Matchday(season, "RT Matchday", 1));
        home = teamRepository.save(new Team("Home Racing", "HRC"));
        away = teamRepository.save(new Team("Away Racing", "ARC"));
        var match = matchRepository.save(new Match(matchday, home, away));
        var r = new Race();
        r.setMatchday(matchday);
        r.setMatch(match);
        race = raceRepository.save(r);
    }

    @Test
    void shouldListRaces() throws Exception {
        mockMvc.perform(get("/admin/races"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/races"))
                .andExpect(model().attributeExists("races", "seasons", "raceScores"));
    }

    @Test
    void shouldListRacesByMatchday() throws Exception {
        mockMvc.perform(get("/admin/races").param("matchdayId", matchday.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/races"))
                .andExpect(model().attributeExists("races", "matchday"));
    }

    @Test
    void shouldListRacesBySeason() throws Exception {
        mockMvc.perform(get("/admin/races").param("seasonId", season.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/races"))
                .andExpect(model().attributeExists("races", "selectedSeasonId"));
    }

    @Test
    void shouldShowRaceDetail() throws Exception {
        mockMvc.perform(get("/admin/races/" + race.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-detail"))
                .andExpect(model().attributeExists("race"));
    }

    @Test
    void shouldShowNewRaceForm() throws Exception {
        mockMvc.perform(get("/admin/races/new").param("matchdayId", matchday.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-form"))
                .andExpect(model().attributeExists("raceForm", "matchdays", "teams"));
    }

    @Test
    void shouldShowRaceEditForm() throws Exception {
        mockMvc.perform(get("/admin/races/" + race.getId() + "/edit"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-form"))
                .andExpect(model().attributeExists("raceForm", "matchdays", "teams", "seasonCars", "seasonTracks"));
    }

    @Test
    void shouldShowRaceResultsForm() throws Exception {
        mockMvc.perform(get("/admin/races/" + race.getId() + "/results"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-results"))
                .andExpect(model().attributeExists("raceForm", "race", "raceScoring"));
    }

    // --- POST /admin/races/save ---

    @Test
    void shouldCreateNewRace() throws Exception {
        mockMvc.perform(post("/admin/races/save")
                        .param("matchdayId", matchday.getId().toString())
                        .param("homeTeamId", home.getId().toString())
                        .param("awayTeamId", away.getId().toString())
)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races?matchdayId=" + matchday.getId()))
                .andExpect(flash().attributeExists("successMessage"));
    }

    // --- POST /admin/races/{id}/results ---

    @Test
    void shouldSaveResultsClearAndRepopulate() throws Exception {
        var driver1 = driverRepository.save(new Driver("psn_home1", "HomeDriver1"));
        var driver2 = driverRepository.save(new Driver("psn_away1", "AwayDriver1"));

        mockMvc.perform(post("/admin/races/" + race.getId() + "/results")
                        .param("results[0].driverId", driver1.getId().toString())
                        .param("results[0].position", "1")
                        .param("results[0].qualiPosition", "1")
                        .param("results[0].fastestLap", "true")
                        .param("results[1].driverId", driver2.getId().toString())
                        .param("results[1].position", "2")
                        .param("results[1].qualiPosition", "2")
                        .param("results[1].fastestLap", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races/" + race.getId() + "/results"))
                .andExpect(flash().attributeExists("successMessage"));

        var saved = raceRepository.findById(race.getId()).orElseThrow();
        assertEquals(2, saved.getResults().size());

        // Save again with only one driver to verify clear-and-repopulate
        mockMvc.perform(post("/admin/races/" + race.getId() + "/results")
                        .param("results[0].driverId", driver1.getId().toString())
                        .param("results[0].position", "1")
                        .param("results[0].qualiPosition", "1")
                        .param("results[0].fastestLap", "false"))
                .andExpect(status().is3xxRedirection());

        var updated = raceRepository.findById(race.getId()).orElseThrow();
        assertEquals(1, updated.getResults().size());
    }

    // --- POST /admin/races/{id}/quick-score ---

    @Test
    void quickScoreShouldRedirectToValidReturnUrl() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/quick-score")
                        .param("homeScore", "10")
                        .param("awayScore", "8")
                        .param("returnUrl", "/admin/matchdays/" + matchday.getId()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/matchdays/" + matchday.getId()))
                .andExpect(flash().attributeExists("successMessage"));

        var saved = raceRepository.findById(race.getId()).orElseThrow();
        assertEquals(10, saved.getHomeScore());
        assertEquals(8, saved.getAwayScore());
    }

    @Test
    void quickScoreShouldRejectAbsoluteUrlRedirect() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/quick-score")
                        .param("homeScore", "5")
                        .param("awayScore", "3")
                        .param("returnUrl", "https://evil.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races"));
    }

    @Test
    void quickScoreShouldRejectProtocolRelativeUrlRedirect() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/quick-score")
                        .param("homeScore", "5")
                        .param("awayScore", "3")
                        .param("returnUrl", "//evil.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races"));
    }

    @Test
    void quickScoreShouldFallbackWhenReturnUrlMissing() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/quick-score")
                        .param("homeScore", "7")
                        .param("awayScore", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races"));
    }

    // --- POST /admin/races/{id}/attachments/link ---

    @Test
    void shouldAddValidLink() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/attachments/link")
                        .param("name", "Race Replay")
                        .param("url", "https://youtube.com/watch?v=abc123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races/" + race.getId()))
                .andExpect(flash().attribute("successMessage", "Link added: Race Replay"));
    }

    @Test
    void shouldRejectJavascriptLink() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/attachments/link")
                        .param("name", "XSS Attempt")
                        .param("url", "javascript:alert(1)"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races/" + race.getId()))
                .andExpect(flash().attribute("errorMessage", "Link must start with http:// or https://"));
    }

    @Test
    void shouldRejectDataUriLink() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/attachments/link")
                        .param("name", "Data URI")
                        .param("url", "data:text/html,<script>alert(1)</script>"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races/" + race.getId()))
                .andExpect(flash().attribute("errorMessage", "Link must start with http:// or https://"));
    }

    // --- POST /admin/races/{id}/delete ---

    @Test
    void shouldDeleteRace() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races?matchdayId=" + matchday.getId()))
                .andExpect(flash().attributeExists("successMessage"));

        assertFalse(raceRepository.findById(race.getId()).isPresent());
    }

    // --- GET /admin/races/used-selections ---

    @Test
    void shouldReturnUsedSelectionsAsJson() throws Exception {
        var car = carRepository.save(new Car("Toyota", "GR Supra"));
        season.getCars().add(car);
        seasonRepository.save(season);
        race.setCar(car);
        raceRepository.save(race);

        mockMvc.perform(get("/admin/races/used-selections")
                        .param("seasonId", season.getId().toString())
                        .param("homeTeamId", home.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedCarIds").isArray())
                .andExpect(jsonPath("$.usedCarIds[0]").value(car.getId().toString()))
                .andExpect(jsonPath("$.usedTrackIds").isArray());
    }

    @Test
    void shouldReturnUsedSelectionsExcludingCurrentRace() throws Exception {
        var car = carRepository.save(new Car("Nissan", "GT-R"));
        season.getCars().add(car);
        seasonRepository.save(season);
        race.setCar(car);
        raceRepository.save(race);

        mockMvc.perform(get("/admin/races/used-selections")
                        .param("seasonId", season.getId().toString())
                        .param("homeTeamId", home.getId().toString())
                        .param("excludeRaceId", race.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usedCarIds").isEmpty());
    }

    // --- Uniqueness validation ---

    @Test
    void shouldRejectDuplicateCarForSameHomeTeam() throws Exception {
        var car = carRepository.save(new Car("Honda", "NSX"));
        season.getCars().add(car);
        seasonRepository.save(season);
        race.setCar(car);
        raceRepository.save(race);

        // Create a second matchday for the second race
        var matchday2 = matchdayRepository.save(new Matchday(season, "RT Matchday 2", 2));

        mockMvc.perform(post("/admin/races/save")
                        .param("matchdayId", matchday2.getId().toString())
                        .param("homeTeamId", home.getId().toString())
                        .param("awayTeamId", away.getId().toString())
                        .param("carId", car.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage",
                        home.getShortName() + " has already used " + car.getDisplayName() + " this season"));
    }

    // --- Pool validation ---

    @Test
    void shouldRejectCarNotInSeasonPool() throws Exception {
        var car = carRepository.save(new Car("Ferrari", "488"));
        // Intentionally NOT adding car to season pool

        mockMvc.perform(post("/admin/races/save")
                        .param("matchdayId", matchday.getId().toString())
                        .param("homeTeamId", home.getId().toString())
                        .param("awayTeamId", away.getId().toString())
                        .param("carId", car.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "Car is not in this season's pool"));
    }

    @Test
    void shouldRejectTrackNotInSeasonPool() throws Exception {
        var track = trackRepository.save(new Track("Silverstone", "UK"));
        // Intentionally NOT adding track to season pool

        mockMvc.perform(post("/admin/races/save")
                        .param("matchdayId", matchday.getId().toString())
                        .param("homeTeamId", home.getId().toString())
                        .param("awayTeamId", away.getId().toString())
                        .param("trackId", track.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "Track is not in this season's pool"));
    }

    // --- POST /admin/races/{id}/attachments/upload ---

    @Test
    void shouldUploadFileAttachment() throws Exception {
        var file = new MockMultipartFile("file", "test-image.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        mockMvc.perform(multipart("/admin/races/" + race.getId() + "/attachments/upload")
                        .file(file))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races/" + race.getId()))
                .andExpect(flash().attribute("successMessage", "File uploaded: test-image.png"));
    }

    // --- POST /admin/races/attachments/{id}/delete ---

    @Test
    void shouldDeleteLinkAttachment() throws Exception {
        var attachment = raceAttachmentRepository.save(
                new RaceAttachment(race, AttachmentType.LINK, "Test Link", "https://example.com"));

        mockMvc.perform(post("/admin/races/attachments/" + attachment.getId() + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races/" + race.getId()))
                .andExpect(flash().attribute("successMessage", "Attachment deleted"));

        assertFalse(raceAttachmentRepository.findById(attachment.getId()).isPresent());
    }

    @Test
    void shouldDeleteFileAttachment() throws Exception {
        // Create a file attachment with a URL that points to a non-existent file (delete is best-effort)
        var attachment = raceAttachmentRepository.save(
                new RaceAttachment(race, AttachmentType.FILE, "Test File", "/uploads/races/" + race.getId() + "/test.png"));

        mockMvc.perform(post("/admin/races/attachments/" + attachment.getId() + "/delete"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races/" + race.getId()))
                .andExpect(flash().attribute("successMessage", "Attachment deleted"));

        assertFalse(raceAttachmentRepository.findById(attachment.getId()).isPresent());
    }

    // --- GET /admin/races/attachments/{id}/download ---

    @Test
    void shouldDownloadFileAttachment() throws Exception {
        // Create actual file on disk
        Path raceDir = Paths.get(uploadDir).toAbsolutePath().normalize()
                .resolve("races").resolve(race.getId().toString());
        Files.createDirectories(raceDir);
        Path testFile = raceDir.resolve("test-download.png");
        Files.write(testFile, new byte[]{1, 2, 3, 4});

        var attachment = raceAttachmentRepository.save(
                new RaceAttachment(race, AttachmentType.FILE, "Download Test",
                        "/uploads/races/" + race.getId() + "/test-download.png"));

        mockMvc.perform(get("/admin/races/attachments/" + attachment.getId() + "/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("Download Test")));

        // Cleanup
        Files.deleteIfExists(testFile);
    }

    @Test
    void shouldReturnBadRequestForLinkDownload() throws Exception {
        var attachment = raceAttachmentRepository.save(
                new RaceAttachment(race, AttachmentType.LINK, "Not a file", "https://example.com"));

        mockMvc.perform(get("/admin/races/attachments/" + attachment.getId() + "/download"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnNotFoundForMissingFile() throws Exception {
        var attachment = raceAttachmentRepository.save(
                new RaceAttachment(race, AttachmentType.FILE, "Missing",
                        "/uploads/races/" + race.getId() + "/nonexistent.png"));

        mockMvc.perform(get("/admin/races/attachments/" + attachment.getId() + "/download"))
                .andExpect(status().isNotFound());
    }

    // --- POST /admin/races/{id}/delete (already covered above but adding race with results) ---

    // --- Duplicate track validation ---

    @Test
    void shouldRejectDuplicateTrackForSameHomeTeam() throws Exception {
        var track = trackRepository.save(new Track("Test Suzuka", "JP"));
        season.getTracks().add(track);
        seasonRepository.save(season);
        race.setTrack(track);
        raceRepository.save(race);

        var matchday2 = matchdayRepository.save(new Matchday(season, "RT Matchday DT", 3));

        mockMvc.perform(post("/admin/races/save")
                        .param("matchdayId", matchday2.getId().toString())
                        .param("homeTeamId", home.getId().toString())
                        .param("awayTeamId", away.getId().toString())
                        .param("trackId", track.getId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage",
                        home.getShortName() + " has already used " + track.getName() + " this season"));
    }

    // --- Race detail with results ---

    @Test
    void shouldShowRaceDetailWithResults() throws Exception {
        var driver1 = driverRepository.save(new Driver("psn_detail_h", "DetailHomeDriver"));
        var driver2 = driverRepository.save(new Driver("psn_detail_a", "DetailAwayDriver"));
        seasonDriverRepository.save(new SeasonDriver(season, driver1, home));
        seasonDriverRepository.save(new SeasonDriver(season, driver2, away));

        // Save results first
        mockMvc.perform(post("/admin/races/" + race.getId() + "/results")
                        .param("results[0].driverId", driver1.getId().toString())
                        .param("results[0].position", "1")
                        .param("results[0].qualiPosition", "1")
                        .param("results[0].fastestLap", "true")
                        .param("results[1].driverId", driver2.getId().toString())
                        .param("results[1].position", "2")
                        .param("results[1].qualiPosition", "2")
                        .param("results[1].fastestLap", "false"))
                .andExpect(status().is3xxRedirection());

        // Now view the detail page with results
        mockMvc.perform(get("/admin/races/" + race.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-detail"))
                .andExpect(model().attributeExists("race", "homeTotal", "awayTotal", "driverTeamMap"));
    }

    // --- Race detail with results-graphic flags ---

    @Test
    void shouldShowRaceDetailWithResultsGraphicFlags() throws Exception {
        mockMvc.perform(get("/admin/races/" + race.getId()))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/race-detail"))
                .andExpect(model().attributeExists("canGenerateResults", "resultsMissing", "resultsExist"))
                .andExpect(model().attribute("resultsMissing", true))
                .andExpect(model().attribute("canGenerateResults", false));
    }

    // --- POST /admin/races/{id}/generate-results ---

    @Test
    void generateResults_withoutResults_shouldShowError() throws Exception {
        mockMvc.perform(post("/admin/races/" + race.getId() + "/generate-results"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/races/" + race.getId()))
                .andExpect(flash().attributeExists("errorMessage"));
    }

    // --- List races with scores ---

    @Test
    void shouldListRacesWithScores() throws Exception {
        // Set up a match with scores
        race.getMatch().setHomeScore(10);
        race.getMatch().setAwayScore(8);
        matchRepository.save(race.getMatch());

        mockMvc.perform(get("/admin/races"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("raceScores"));
    }
}
