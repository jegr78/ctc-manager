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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
	private SeasonPhaseRepository seasonPhaseRepository;

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
		var s = new Season("Race Test Season", 2026, 1);
		season = seasonRepository.save(s);
		// persist a REGULAR phase carrying scoring; bind matchday to it.
		var regularPhase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		regularPhase.setRaceScoring(rs);
		regularPhase.setMatchScoring(ms);
		regularPhase = seasonPhaseRepository.save(regularPhase);
		matchday = matchdayRepository.save(new Matchday(regularPhase, "RT Matchday", 1));
		home = teamRepository.save(new Team("Home Racing", "HRC"));
		away = teamRepository.save(new Team("Away Racing", "ARC"));
		var match = matchRepository.save(new Match(matchday, home, away));
		var r = new Race();
		r.setMatchday(matchday);
		r.setMatch(match);
		race = raceRepository.save(r);
	}

	@Test
	void whenGetRaces_thenReturnsRacesView() throws Exception {
		// when
		mockMvc.perform(get("/admin/races"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/races"))
				.andExpect(model().attributeExists("races", "seasons", "raceScores"));
	}

	@Test
	void givenMatchdayId_whenGetRacesByMatchday_thenReturnsFilteredRaces() throws Exception {
		// when
		mockMvc.perform(get("/admin/races").param("matchdayId", matchday.getId().toString()))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/races"))
				.andExpect(model().attributeExists("races", "matchday"));
	}

	@Test
	void givenSeasonId_whenGetRacesBySeason_thenReturnsFilteredRaces() throws Exception {
		// when
		mockMvc.perform(get("/admin/races").param("seasonId", season.getId().toString()))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/races"))
				.andExpect(model().attributeExists("races", "selectedSeasonId"));
	}

	@Test
	void givenExistingRace_whenGetRaceDetail_thenReturnsDetailView() throws Exception {
		// when
		mockMvc.perform(get("/admin/races/" + race.getId()))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/race-detail"))
				.andExpect(model().attributeExists("race"));
	}

	@Test
	void givenMatchday_whenGetNewRaceForm_thenReturnsRaceForm() throws Exception {
		// when
		mockMvc.perform(get("/admin/races/new").param("matchdayId", matchday.getId().toString()))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/race-form"))
				.andExpect(model().attributeExists("raceForm", "matchdays", "teams"));
	}

	@Test
	void givenExistingRace_whenGetEditForm_thenReturnsRaceForm() throws Exception {
		// when
		mockMvc.perform(get("/admin/races/" + race.getId() + "/edit"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/race-form"))
				.andExpect(model().attributeExists("raceForm", "matchdays", "teams", "seasonCars", "seasonTracks"));
	}

	@Test
	void givenExistingRace_whenGetResultsForm_thenReturnsResultsView() throws Exception {
		// when
		mockMvc.perform(get("/admin/races/" + race.getId() + "/results"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/race-results"))
				.andExpect(model().attributeExists("raceForm", "race", "raceScoring"));
	}

	// --- POST /admin/races/save ---

	@Test
	void givenMatchdayAndTeams_whenSaveNewRace_thenRedirectsWithSuccess() throws Exception {
		// when
		mockMvc.perform(post("/admin/races/save")
						.param("matchdayId", matchday.getId().toString())
						.param("homeTeamId", home.getId().toString())
						.param("awayTeamId", away.getId().toString())
				)
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races?matchdayId=" + matchday.getId()))
				.andExpect(flash().attributeExists("successMessage"));
	}

	// --- POST /admin/races/{id}/results ---

	@Test
	void givenTwoDrivers_whenSaveResults_thenRedirectsAndPersistsAndAllowsRepopulate() throws Exception {
		// given
		var driver1 = driverRepository.save(new Driver("psn_home1", "HomeDriver1"));
		var driver2 = driverRepository.save(new Driver("psn_away1", "AwayDriver1"));

		// when
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

		// then
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
	void givenValidReturnUrl_whenQuickScore_thenRedirectsToReturnUrlAndPersistsScore() throws Exception {
		// when
		mockMvc.perform(post("/admin/races/" + race.getId() + "/quick-score")
						.param("homeScore", "10")
						.param("awayScore", "8")
						.param("returnUrl", "/admin/matchdays/" + matchday.getId()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/matchdays/" + matchday.getId()))
				.andExpect(flash().attributeExists("successMessage"));

		// then
		var saved = raceRepository.findById(race.getId()).orElseThrow();
		assertEquals(10, saved.getHomeScore());
		assertEquals(8, saved.getAwayScore());
	}

	@Test
	void givenAbsoluteReturnUrl_whenQuickScore_thenFallsBackToRacesList() throws Exception {
		// when
		mockMvc.perform(post("/admin/races/" + race.getId() + "/quick-score")
						.param("homeScore", "5")
						.param("awayScore", "3")
						.param("returnUrl", "https://evil.com"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races"));
	}

	@Test
	void givenProtocolRelativeReturnUrl_whenQuickScore_thenFallsBackToRacesList() throws Exception {
		// when
		mockMvc.perform(post("/admin/races/" + race.getId() + "/quick-score")
						.param("homeScore", "5")
						.param("awayScore", "3")
						.param("returnUrl", "//evil.com"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races"));
	}

	@Test
	void givenMissingReturnUrl_whenQuickScore_thenFallsBackToRacesList() throws Exception {
		// when
		mockMvc.perform(post("/admin/races/" + race.getId() + "/quick-score")
						.param("homeScore", "7")
						.param("awayScore", "4"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races"));
	}

	// --- POST /admin/races/{id}/attachments/link ---

	@Test
	void givenValidHttpLink_whenAddLink_thenRedirectsWithSuccess() throws Exception {
		// when
		mockMvc.perform(post("/admin/races/" + race.getId() + "/attachments/link")
						.param("name", "Race Replay")
						.param("url", "https://youtube.com/watch?v=abc123"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + race.getId()))
				.andExpect(flash().attribute("successMessage", "Link added: Race Replay"));
	}

	@Test
	void givenJavascriptUrl_whenAddLink_thenRedirectsWithError() throws Exception {
		// when
		mockMvc.perform(post("/admin/races/" + race.getId() + "/attachments/link")
						.param("name", "XSS Attempt")
						.param("url", "javascript:alert(1)"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + race.getId()))
				.andExpect(flash().attribute("errorMessage", "Link must start with http:// or https://"));
	}

	@Test
	void givenDataUriUrl_whenAddLink_thenRedirectsWithError() throws Exception {
		// when
		mockMvc.perform(post("/admin/races/" + race.getId() + "/attachments/link")
						.param("name", "Data URI")
						.param("url", "data:text/html,<script>alert(1)</script>"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + race.getId()))
				.andExpect(flash().attribute("errorMessage", "Link must start with http:// or https://"));
	}

	// --- POST /admin/races/{id}/delete ---

	@Test
	void givenExistingRace_whenDeleteRace_thenRedirectsAndRemoves() throws Exception {
		// when
		mockMvc.perform(post("/admin/races/" + race.getId() + "/delete"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races?matchdayId=" + matchday.getId()))
				.andExpect(flash().attributeExists("successMessage"));

		// then
		assertFalse(raceRepository.findById(race.getId()).isPresent());
	}

	// --- GET /admin/races/used-selections ---

	@Test
	void givenRaceWithCar_whenGetUsedSelections_thenReturnsUsedCarIds() throws Exception {
		// given
		var car = carRepository.save(new Car("Toyota", "GR Supra"));
		season.getCars().add(car);
		seasonRepository.save(season);
		race.setCar(car);
		raceRepository.save(race);

		// when
		mockMvc.perform(get("/admin/races/used-selections")
						.param("seasonId", season.getId().toString())
						.param("homeTeamId", home.getId().toString()))
				// then
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usedCarIds").isArray())
				.andExpect(jsonPath("$.usedCarIds[0]").value(car.getId().toString()))
				.andExpect(jsonPath("$.usedTrackIds").isArray());
	}

	@Test
	void givenRaceWithCarAndExcludeRaceId_whenGetUsedSelections_thenExcludesCurrentRace() throws Exception {
		// given
		var car = carRepository.save(new Car("Nissan", "GT-R"));
		season.getCars().add(car);
		seasonRepository.save(season);
		race.setCar(car);
		raceRepository.save(race);

		// when
		mockMvc.perform(get("/admin/races/used-selections")
						.param("seasonId", season.getId().toString())
						.param("homeTeamId", home.getId().toString())
						.param("excludeRaceId", race.getId().toString()))
				// then
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.usedCarIds").isEmpty());
	}

	// --- Uniqueness validation ---

	@Test
	void givenHomeTeamAlreadyUsedCar_whenSaveRaceWithSameCar_thenRedirectsWithError() throws Exception {
		// given
		var car = carRepository.save(new Car("Honda", "NSX"));
		season.getCars().add(car);
		seasonRepository.save(season);
		race.setCar(car);
		raceRepository.save(race);

		// Create a second matchday for the second race
		// bind matchday to the season's persisted REGULAR phase.
		var regularPhase2 = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();
		var matchday2 = matchdayRepository.save(new Matchday(regularPhase2, "RT Matchday 2", 2));

		// when
		mockMvc.perform(post("/admin/races/save")
						.param("matchdayId", matchday2.getId().toString())
						.param("homeTeamId", home.getId().toString())
						.param("awayTeamId", away.getId().toString())
						.param("carId", car.getId().toString()))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorMessage",
						home.getShortName() + " has already used " + car.getDisplayName() + " this season"));
	}

	// --- Pool validation ---

	@Test
	void givenCarNotInSeasonPool_whenSaveRace_thenRedirectsWithError() throws Exception {
		// given
		var car = carRepository.save(new Car("Ferrari", "488"));
		// Intentionally NOT adding car to season pool

		// when
		mockMvc.perform(post("/admin/races/save")
						.param("matchdayId", matchday.getId().toString())
						.param("homeTeamId", home.getId().toString())
						.param("awayTeamId", away.getId().toString())
						.param("carId", car.getId().toString()))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorMessage", "Car is not in this season's pool"));
	}

	@Test
	void givenTrackNotInSeasonPool_whenSaveRace_thenRedirectsWithError() throws Exception {
		// given
		var track = trackRepository.save(new Track("Silverstone", "UK"));
		// Intentionally NOT adding track to season pool

		// when
		mockMvc.perform(post("/admin/races/save")
						.param("matchdayId", matchday.getId().toString())
						.param("homeTeamId", home.getId().toString())
						.param("awayTeamId", away.getId().toString())
						.param("trackId", track.getId().toString()))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorMessage", "Track is not in this season's pool"));
	}

	// --- POST /admin/races/{id}/attachments/upload ---

	@Test
	void givenImageFile_whenUploadAttachment_thenRedirectsWithSuccess() throws Exception {
		// given
		var file = new MockMultipartFile("file", "test-image.png", "image/png",
				new byte[]{(byte) 0x89, 'P', 'N', 'G'});

		// when
		mockMvc.perform(multipart("/admin/races/" + race.getId() + "/attachments/upload")
						.file(file))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + race.getId()))
				.andExpect(flash().attribute("successMessage", "File uploaded: test-image.png"));
	}

	// --- POST /admin/races/attachments/{id}/delete ---

	@Test
	void givenLinkAttachment_whenDeleteAttachment_thenRedirectsAndRemoves() throws Exception {
		// given
		var attachment = raceAttachmentRepository.save(
				new RaceAttachment(race, AttachmentType.LINK, "Test Link", "https://example.com"));

		// when
		mockMvc.perform(post("/admin/races/attachments/" + attachment.getId() + "/delete"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + race.getId()))
				.andExpect(flash().attribute("successMessage", "Attachment deleted"));

		// then
		assertFalse(raceAttachmentRepository.findById(attachment.getId()).isPresent());
	}

	@Test
	void givenFileAttachment_whenDeleteAttachment_thenRedirectsAndRemoves() throws Exception {
		// given
		// Create a file attachment with a URL that points to a non-existent file (delete is best-effort)
		var attachment = raceAttachmentRepository.save(
				new RaceAttachment(race, AttachmentType.FILE, "Test File", "/uploads/races/" + race.getId() + "/test.png"));

		// when
		mockMvc.perform(post("/admin/races/attachments/" + attachment.getId() + "/delete"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + race.getId()))
				.andExpect(flash().attribute("successMessage", "Attachment deleted"));

		// then
		assertFalse(raceAttachmentRepository.findById(attachment.getId()).isPresent());
	}

	// --- GET /admin/races/attachments/{id}/download ---

	@Test
	void givenExistingFileAttachment_whenDownloadAttachment_thenReturnsFileWithContentDisposition() throws Exception {
		// given
		Path raceDir = Paths.get(uploadDir).toAbsolutePath().normalize()
				.resolve("races").resolve(race.getId().toString());
		Files.createDirectories(raceDir);
		Path testFile = raceDir.resolve("test-download.png");
		Files.write(testFile, new byte[]{1, 2, 3, 4});

		var attachment = raceAttachmentRepository.save(
				new RaceAttachment(race, AttachmentType.FILE, "Download Test",
						"/uploads/races/" + race.getId() + "/test-download.png"));

		// when
		mockMvc.perform(get("/admin/races/attachments/" + attachment.getId() + "/download"))
				// then
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition",
						org.hamcrest.Matchers.containsString("Download Test")));

		// Cleanup
		Files.deleteIfExists(testFile);
	}

	@Test
	void givenLinkAttachment_whenDownloadAttachment_thenReturnsBadRequest() throws Exception {
		// given
		var attachment = raceAttachmentRepository.save(
				new RaceAttachment(race, AttachmentType.LINK, "Not a file", "https://example.com"));

		// when
		mockMvc.perform(get("/admin/races/attachments/" + attachment.getId() + "/download"))
				// then
				.andExpect(status().isBadRequest());
	}

	@Test
	void givenFileAttachmentWithMissingFile_whenDownloadAttachment_thenReturnsNotFound() throws Exception {
		// given
		var attachment = raceAttachmentRepository.save(
				new RaceAttachment(race, AttachmentType.FILE, "Missing",
						"/uploads/races/" + race.getId() + "/nonexistent.png"));

		// when
		mockMvc.perform(get("/admin/races/attachments/" + attachment.getId() + "/download"))
				// then
				.andExpect(status().isNotFound());
	}

	// --- Duplicate track validation ---

	@Test
	void givenHomeTeamAlreadyUsedTrack_whenSaveRaceWithSameTrack_thenRedirectsWithError() throws Exception {
		// given
		var track = trackRepository.save(new Track("Test Suzuka", "JP"));
		season.getTracks().add(track);
		seasonRepository.save(season);
		race.setTrack(track);
		raceRepository.save(race);

		// bind matchday to the season's persisted REGULAR phase.
		var regularPhaseDt = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();
		var matchday2 = matchdayRepository.save(new Matchday(regularPhaseDt, "RT Matchday DT", 3));

		// when
		mockMvc.perform(post("/admin/races/save")
						.param("matchdayId", matchday2.getId().toString())
						.param("homeTeamId", home.getId().toString())
						.param("awayTeamId", away.getId().toString())
						.param("trackId", track.getId().toString()))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorMessage",
						home.getShortName() + " has already used " + track.getName() + " this season"));
	}

	// --- Race detail with results ---

	@Test
	void givenRaceWithResults_whenGetRaceDetail_thenReturnsDetailWithScores() throws Exception {
		// given
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

		// when
		mockMvc.perform(get("/admin/races/" + race.getId()))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/race-detail"))
				.andExpect(model().attributeExists("race", "homeTotal", "awayTotal", "driverTeamMap"));
	}

	// --- Race detail with results-graphic flags ---

	@Test
	void givenRaceWithoutResults_whenGetRaceDetail_thenReturnsCorrectResultsGraphicFlags() throws Exception {
		// when
		mockMvc.perform(get("/admin/races/" + race.getId()))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/race-detail"))
				.andExpect(model().attributeExists("canGenerateResults", "resultsMissing", "resultsExist"))
				.andExpect(model().attribute("resultsMissing", true))
				.andExpect(model().attribute("canGenerateResults", false));
	}

	// --- POST /admin/races/{id}/generate-results ---

	@Test
	void givenRaceWithoutResults_whenGenerateResults_thenRedirectsWithError() throws Exception {
		// when
		mockMvc.perform(post("/admin/races/" + race.getId() + "/generate-results"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + race.getId()))
				.andExpect(flash().attributeExists("errorMessage"));
	}

	// --- POST /admin/races/{id}/generate-settings ---

	@Test
	void givenRaceWithoutSettings_whenGenerateSettings_thenRedirectsWithError() throws Exception {
		// when
		mockMvc.perform(post("/admin/races/" + race.getId() + "/generate-settings"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + race.getId()))
				.andExpect(flash().attributeExists("errorMessage"));
	}

	// --- POST /admin/races/{id}/generate-overlay ---

	@Test
	void givenRaceWithNoMatch_whenGenerateOverlay_thenRedirectsWithError() throws Exception {
		// given — create a race without a match
		var raceWithoutMatch = new Race();
		raceWithoutMatch.setMatchday(race.getMatchday());
		raceWithoutMatch = raceRepository.save(raceWithoutMatch);

		// when
		mockMvc.perform(post("/admin/races/" + raceWithoutMatch.getId() + "/generate-overlay"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + raceWithoutMatch.getId()))
				.andExpect(flash().attributeExists("errorMessage"));
	}

	// --- GET detail with settings flags ---

	@Test
	void givenRaceWithoutSettings_whenGetRaceDetail_thenReturnsCorrectSettingsFlags() throws Exception {
		// when
		mockMvc.perform(get("/admin/races/" + race.getId()))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/race-detail"))
				.andExpect(model().attributeExists("canGenerateSettings", "settingsMissing", "settingsExist",
						"canGenerateOverlay", "overlayExists"))
				.andExpect(model().attribute("settingsMissing", true))
				.andExpect(model().attribute("canGenerateSettings", false))
				.andExpect(model().attribute("canGenerateOverlay", true))
				.andExpect(model().attribute("overlayExists", false));
	}

	// --- List races with scores ---

	@Test
	void givenMatchWithScores_whenGetRaces_thenReturnsRaceScoresInModel() throws Exception {
		// given
		race.getMatch().setHomeScore(10);
		race.getMatch().setAwayScore(8);
		matchRepository.save(race.getMatch());

		// when
		mockMvc.perform(get("/admin/races"))
				// then
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("raceScores"));
	}
}
