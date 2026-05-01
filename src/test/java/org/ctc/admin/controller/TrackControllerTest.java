package org.ctc.admin.controller;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class TrackControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TrackRepository trackRepository;

	@Autowired
	private RaceRepository raceRepository;

	@Autowired
	private SeasonRepository seasonRepository;

	@Autowired
	private MatchdayRepository matchdayRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private MatchRepository matchRepository;

	@Autowired
	private RaceScoringRepository raceScoringRepository;

	@Autowired
	private MatchScoringRepository matchScoringRepository;

	@Autowired
	private SeasonPhaseRepository seasonPhaseRepository;

	private Track track;

	@BeforeEach
	void setUp() {
		track = trackRepository.save(new Track("Tsukuba Circuit", "Japan"));
	}

	// --- GET /admin/tracks ---

	@Test
	void whenGetTracks_thenReturnsTracksView() throws Exception {
		// when
		mockMvc.perform(get("/admin/tracks"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/tracks"))
				.andExpect(model().attributeExists("tracks"));
	}

	// --- GET /admin/tracks/new ---

	@Test
	void whenGetNewTrackForm_thenReturnsTrackForm() throws Exception {
		// when
		mockMvc.perform(get("/admin/tracks/new"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/track-form"))
				.andExpect(model().attributeExists("trackForm"));
	}

	// --- GET /admin/tracks/{id}/edit ---

	@Test
	void givenExistingTrack_whenGetEditForm_thenReturnsTrackForm() throws Exception {
		// when
		mockMvc.perform(get("/admin/tracks/" + track.getId() + "/edit"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/track-form"))
				.andExpect(model().attributeExists("trackForm"));
	}

	// --- POST /admin/tracks/save ---

	@Test
	void givenValidTrackForm_whenSaveNewTrack_thenRedirectsAndPersists() throws Exception {
		// when
		mockMvc.perform(post("/admin/tracks/save")
						.param("name", "Suzuka Circuit")
						.param("country", "Japan"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/tracks"))
				.andExpect(flash().attributeExists("successMessage"));

		// then
		assertTrue(trackRepository.existsByName("Suzuka Circuit"));
	}

	@Test
	void givenExistingTrack_whenSaveUpdatedTrack_thenRedirectsAndUpdates() throws Exception {
		// when
		mockMvc.perform(post("/admin/tracks/save")
						.param("id", track.getId().toString())
						.param("name", "Tsukuba Circuit Updated")
						.param("country", "Japan"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/tracks"))
				.andExpect(flash().attributeExists("successMessage"));

		// then
		var updated = trackRepository.findById(track.getId()).orElseThrow();
		assertEquals("Tsukuba Circuit Updated", updated.getName());
	}

	@Test
	void givenBlankName_whenSaveTrack_thenReturnsFormWithErrors() throws Exception {
		// when
		mockMvc.perform(post("/admin/tracks/save")
						.param("name", "")
						.param("country", "Japan"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/track-form"));
	}

	// --- POST /admin/tracks/{id}/delete ---

	@Test
	void givenUnreferencedTrack_whenDeleteTrack_thenRedirectsAndRemoves() throws Exception {
		// when
		mockMvc.perform(post("/admin/tracks/" + track.getId() + "/delete"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/tracks"))
				.andExpect(flash().attributeExists("successMessage"));

		// then
		assertFalse(trackRepository.findById(track.getId()).isPresent());
	}

	@Test
	void givenTrackReferencedByRace_whenDeleteTrack_thenRedirectsWithErrorAndKeepsTrack() throws Exception {
		// given
		var rs = raceScoringRepository.save(new RaceScoring("TT RS " + java.util.UUID.randomUUID().toString().substring(0, 4), "20,17", null, 0));
		var ms = matchScoringRepository.save(new MatchScoring("TT MS " + java.util.UUID.randomUUID().toString().substring(0, 4), 3, 1, 0));
		var s = new Season("Track Test Season", 2026, 1);
		var season = seasonRepository.save(s);
		// Phase 61 MIGR-06: persist a REGULAR phase, then bind the matchday to it.
		var regularPhase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		regularPhase.setRaceScoring(rs);
		regularPhase.setMatchScoring(ms);
		regularPhase = seasonPhaseRepository.save(regularPhase);
		var matchday = matchdayRepository.save(new Matchday(regularPhase, "TT Matchday", 1));
		var home = teamRepository.save(new Team("Home Team", "HOM"));
		var away = teamRepository.save(new Team("Away Team", "AWY"));
		var match = matchRepository.save(new Match(matchday, home, away));
		var race = new Race();
		race.setMatchday(matchday);
		race.setMatch(match);
		race.setTrack(track);
		raceRepository.save(race);

		// when
		mockMvc.perform(post("/admin/tracks/" + track.getId() + "/delete"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/tracks"))
				.andExpect(flash().attributeExists("errorMessage"));

		// then
		assertTrue(trackRepository.findById(track.getId()).isPresent());
	}

	// --- POST /admin/tracks/{id}/image ---

	@Test
	void givenImageFile_whenUploadTrackImage_thenRedirectsAndSetsImageUrl() throws Exception {
		// given
		var imageFile = new org.springframework.mock.web.MockMultipartFile(
				"image", "track.png", "image/png", new byte[]{1, 2, 3});

		// when
		mockMvc.perform(multipart("/admin/tracks/" + track.getId() + "/image").file(imageFile))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/tracks/" + track.getId() + "/edit"))
				.andExpect(flash().attributeExists("successMessage"));

		// then
		var updated = trackRepository.findById(track.getId()).orElseThrow();
		assertNotNull(updated.getImageUrl());
		assertTrue(updated.getImageUrl().contains("track.png"));
	}

	@Test
	void givenTrackAssignedToSeasonPool_whenDeleteTrack_thenRedirectsWithErrorAndKeepsTrack() throws Exception {
		// given
		var rs = raceScoringRepository.save(new RaceScoring("TP RS " + java.util.UUID.randomUUID().toString().substring(0, 4), "20,17", null, 0));
		var ms = matchScoringRepository.save(new MatchScoring("TP MS " + java.util.UUID.randomUUID().toString().substring(0, 4), 3, 1, 0));
		var s = new Season("Pool Test Season", 2026, 1);
		var season = seasonRepository.save(s);
		season.getTracks().add(track);
		seasonRepository.save(season);

		// when
		mockMvc.perform(post("/admin/tracks/" + track.getId() + "/delete"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/tracks"))
				.andExpect(flash().attributeExists("errorMessage"));

		// then
		assertTrue(trackRepository.findById(track.getId()).isPresent());
	}
}
