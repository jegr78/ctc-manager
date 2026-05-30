package org.ctc.admin.controller;

import org.ctc.TestHelper;
import org.ctc.TestHelper.SeasonFixture;
import org.ctc.domain.model.Match;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class MatchControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestHelper testHelper;

	@Autowired
	private MatchRepository matchRepository;

	@Autowired
	private RaceRepository raceRepository;

	private SeasonFixture fixture;

	@BeforeEach
	void setUp() {
		fixture = testHelper.createFullSeasonFixture("Test_Match");
	}

	@Test
	void givenMatchday_whenGetNewMatchForm_thenReturnsMatchForm() throws Exception {
		// given
		// fixture provides matchday

		// when
		mockMvc.perform(get("/admin/matches/new")
						.param("matchdayId", fixture.matchday().getId().toString()))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/match-form"))
				.andExpect(model().attributeExists("matchday", "teams"));
	}

	@Test
	void givenTwoTeams_whenSaveMatch_thenRedirectsAndCreatesMatchWithRace() throws Exception {
		// given
		var newHome = testHelper.createTeam("Test_Match Save Home", "Test_MSH");
		var newAway = testHelper.createTeam("Test_Match Save Away", "Test_MSA");
		fixture.season().addTeam(newHome);
		fixture.season().addTeam(newAway);

		// when
		mockMvc.perform(post("/admin/matches/save")
						.param("matchdayId", fixture.matchday().getId().toString())
						.param("homeTeamId", newHome.getId().toString())
						.param("awayTeamId", newAway.getId().toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/matchdays/" + fixture.matchday().getId()))
				.andExpect(flash().attributeExists("successMessage"));

		// then
		var matches = matchRepository.findByMatchdayId(fixture.matchday().getId());
		var created = matches.stream()
				.filter(m -> m.getHomeTeam().getId().equals(newHome.getId()))
				.findFirst();
		assertTrue(created.isPresent());

		// Auto-created race should exist
		var races = raceRepository.findAll().stream()
				.filter(r -> r.getMatch() != null && r.getMatch().getId().equals(created.get().getId()))
				.toList();
		assertEquals(1, races.size());
	}

	@Test
	void givenByeTeam_whenSaveByeMatch_thenRedirectsAndCreatesByeMatch() throws Exception {
		// given
		var byeTeam = testHelper.createTeam("Test_Match Bye Team", "Test_MBT");
		fixture.season().addTeam(byeTeam);

		// when
		mockMvc.perform(post("/admin/matches/save")
						.param("matchdayId", fixture.matchday().getId().toString())
						.param("homeTeamId", byeTeam.getId().toString())
						.param("bye", "true"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("successMessage"));

		// then
		var matches = matchRepository.findByMatchdayId(fixture.matchday().getId());
		var byeMatch = matches.stream()
				.filter(m -> m.getHomeTeam().getId().equals(byeTeam.getId()) && m.isBye())
				.findFirst();
		assertTrue(byeMatch.isPresent());
	}

	@Test
	void givenExistingMatch_whenDeleteMatch_thenRedirectsAndRemoves() throws Exception {
		// given
		var matchId = fixture.match().getId();
		var matchdayId = fixture.matchday().getId();

		// when
		mockMvc.perform(post("/admin/matches/" + matchId + "/delete"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/matchdays/" + matchdayId))
				.andExpect(flash().attributeExists("successMessage"));

		// then
		assertFalse(matchRepository.findById(matchId).isPresent());
	}

	@Test
	void givenExistingMatch_whenSaveDuplicateMatch_thenRedirectsWithError() throws Exception {
		// given
		// Fixture already has a match homeTeam vs awayTeam — creating the same should fail

		// when
		mockMvc.perform(post("/admin/matches/save")
						.param("matchdayId", fixture.matchday().getId().toString())
						.param("homeTeamId", fixture.homeTeam().getId().toString())
						.param("awayTeamId", fixture.awayTeam().getId().toString()))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("errorMessage"));
	}

	@Test
	void givenMatchEditForm_whenSaveEditWithWalkoverTeam_thenWalkoverPersisted() throws Exception {
		// given
		var matchId = fixture.match().getId();
		var awayId = fixture.awayTeam().getId();

		// when
		mockMvc.perform(post("/admin/matches/" + matchId + "/save-edit")
						.param("id", matchId.toString())
						.param("walkoverTeamId", awayId.toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/matches/" + matchId))
				.andExpect(flash().attributeExists("successMessage"));

		// then
		var reloaded = matchRepository.findById(matchId).orElseThrow();
		assertNotNull(reloaded.getWalkoverTeam());
		assertEquals(awayId, reloaded.getWalkoverTeam().getId());
	}

	@Test
	void givenByeMatch_whenSaveEditWithWalkoverTeam_thenErrorFlash() throws Exception {
		// given — a bye match (no away team) cannot become a walkover
		var byeMatch = new Match(fixture.matchday(), fixture.homeTeam(), null);
		byeMatch.setBye(true);
		var byeId = matchRepository.save(byeMatch).getId();

		// when
		mockMvc.perform(post("/admin/matches/" + byeId + "/save-edit")
						.param("id", byeId.toString())
						.param("walkoverTeamId", fixture.homeTeam().getId().toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("errorMessage"));

		// then
		var reloaded = matchRepository.findById(byeId).orElseThrow();
		assertNull(reloaded.getWalkoverTeam());
	}

	@Test
	void givenWalkoverMatch_whenSaveEditWithNullWalkoverTeam_thenWalkoverCleared() throws Exception {
		// given — a match already marked as a walkover
		var matchId = fixture.match().getId();
		mockMvc.perform(post("/admin/matches/" + matchId + "/save-edit")
						.param("id", matchId.toString())
						.param("walkoverTeamId", fixture.awayTeam().getId().toString()))
				.andExpect(flash().attributeExists("successMessage"));
		assertNotNull(matchRepository.findById(matchId).orElseThrow().getWalkoverTeam());

		// when — clear it
		mockMvc.perform(post("/admin/matches/" + matchId + "/save-edit")
						.param("id", matchId.toString())
						.param("walkoverTeamId", ""))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("successMessage"));

		// then
		assertNull(matchRepository.findById(matchId).orElseThrow().getWalkoverTeam());
	}

	@Test
	void givenMatchEditFormWithUnrelatedTeam_whenSaveEditWithWalkoverTeam_thenErrorFlash() throws Exception {
		// given — a third team that is neither home nor away of this match
		var matchId = fixture.match().getId();
		var unrelated = testHelper.createTeam("Test_Match Unrelated", "Test_MUR");
		fixture.season().addTeam(unrelated);

		// when
		mockMvc.perform(post("/admin/matches/" + matchId + "/save-edit")
						.param("id", matchId.toString())
						.param("walkoverTeamId", unrelated.getId().toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("errorMessage"));

		// then
		var reloaded = matchRepository.findById(matchId).orElseThrow();
		assertNull(reloaded.getWalkoverTeam());
	}
}
