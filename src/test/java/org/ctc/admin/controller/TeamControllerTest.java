package org.ctc.admin.controller;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class TeamControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private SeasonRepository seasonRepository;

	@Autowired
	private DriverRepository driverRepository;

	@Autowired
	private MatchdayRepository matchdayRepository;

	@Autowired
	private MatchRepository matchRepository;

	@Autowired
	private RaceRepository raceRepository;

	@Autowired
	private RaceLineupRepository raceLineupRepository;

	@Test
	void whenGetTeams_thenReturnsTeamsView() throws Exception {
		// when
		mockMvc.perform(get("/admin/teams"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/teams"))
				.andExpect(model().attributeExists("parentTeams"));
	}

	@Test
	void givenValidTeamForm_whenSaveTeam_thenRedirectsAndPersists() throws Exception {
		// when
		mockMvc.perform(post("/admin/teams/save")
						.param("name", "MockMvc Racing")
						.param("shortName", "MVR"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/teams"));

		// then
		var saved = teamRepository.findByShortName("MVR");
		assertTrue(saved.isPresent());
		assertEquals("MockMvc Racing", saved.get().getName());
	}

	@Test
	void givenExistingTeam_whenGetTeamDetail_thenReturnsDetailView() throws Exception {
		// given
		var team = teamRepository.save(new Team("Detail Racing", "DTL"));

		// when
		mockMvc.perform(get("/admin/teams/" + team.getId()))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/team-detail"))
				.andExpect(model().attributeExists("team", "seasons", "seasonDriverGroups", "seasonsWithoutDrivers"));
	}

	@Test
	void givenTeamWithSubTeamAndRaceLineup_whenGetTeamDetail_thenReturnsGroupedDrivers() throws Exception {
		// given
		var parent = teamRepository.save(new Team("Grouped Racing", "GRP"));
		var sub = teamRepository.save(new Team("Grouped Racing A", "GRP A", parent));

		var season = seasonRepository.findByActiveTrue().orElseThrow();
		var matchday = matchdayRepository.save(org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "GRP MD", 99));
		var match = new Match();
		match.setMatchday(matchday);
		match.setHomeTeam(parent);
		match.setAwayTeam(sub);
		matchRepository.save(match);
		var race = new Race();
		race.setMatchday(matchday);
		race.setMatch(match);
		raceRepository.save(race);

		var d1 = driverRepository.save(new Driver("grp_driver1", "GRP Driver 1"));
		var d2 = driverRepository.save(new Driver("grp_driver2", "GRP Driver 2"));
		raceLineupRepository.save(new RaceLineup(race, d1, parent));
		raceLineupRepository.save(new RaceLineup(race, d2, sub));

		// when
		mockMvc.perform(get("/admin/teams/" + parent.getId()))
				// then
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("seasonDriverGroups"))
				.andExpect(model().attribute("seasonDriverGroups",
						org.hamcrest.Matchers.hasSize(1)));
	}

	@Test
	void givenTeamWithNoRaceLineup_whenGetTeamDetail_thenReturnsEmptyDriverGroups() throws Exception {
		// given
		var team = teamRepository.save(new Team("Empty Racing", "EMP"));

		// when
		mockMvc.perform(get("/admin/teams/" + team.getId()))
				// then
				.andExpect(status().isOk())
				.andExpect(model().attribute("seasonDriverGroups",
						org.hamcrest.Matchers.hasSize(0)));
	}

	@Test
	void givenParentAndSubTeamWithLineups_whenGetParentDetail_thenIncludesSubTeamDrivers() throws Exception {
		// given
		var parent = teamRepository.save(new Team("Parent Inc", "PIN"));
		var sub = teamRepository.save(new Team("Parent Inc A", "PIN A", parent));

		var season = seasonRepository.findByActiveTrue().orElseThrow();
		var matchday = matchdayRepository.save(org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "PIN MD", 98));
		var match = new Match();
		match.setMatchday(matchday);
		match.setHomeTeam(parent);
		match.setAwayTeam(sub);
		matchRepository.save(match);
		var race = new Race();
		race.setMatchday(matchday);
		race.setMatch(match);
		raceRepository.save(race);

		var d1 = driverRepository.save(new Driver("pin_parent1", "Parent Driver"));
		var d2 = driverRepository.save(new Driver("pin_sub1", "Sub Driver"));
		raceLineupRepository.save(new RaceLineup(race, d1, parent));
		raceLineupRepository.save(new RaceLineup(race, d2, sub));

		// when
		mockMvc.perform(get("/admin/teams/" + parent.getId()))
				// then
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("pin_parent1")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("pin_sub1")));
	}

	@Test
	void givenSubTeamWithOwnLineup_whenGetSubTeamDetail_thenShowsOnlyOwnDrivers() throws Exception {
		// given
		var parent = teamRepository.save(new Team("Own Inc", "OWN"));
		var sub = teamRepository.save(new Team("Own Inc A", "OWN A", parent));

		var season = seasonRepository.findByActiveTrue().orElseThrow();
		var matchday = matchdayRepository.save(org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "OWN MD", 97));
		var match = new Match();
		match.setMatchday(matchday);
		match.setHomeTeam(parent);
		match.setAwayTeam(sub);
		matchRepository.save(match);
		var race = new Race();
		race.setMatchday(matchday);
		race.setMatch(match);
		raceRepository.save(race);

		var d1 = driverRepository.save(new Driver("own_parent1", "Parent Only"));
		var d2 = driverRepository.save(new Driver("own_sub1", "Sub Only"));
		raceLineupRepository.save(new RaceLineup(race, d1, parent));
		raceLineupRepository.save(new RaceLineup(race, d2, sub));

		// when
		mockMvc.perform(get("/admin/teams/" + sub.getId()))
				// then
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("own_sub1")))
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("own_parent1"))));
	}

	@Test
	void whenGetNewTeamForm_thenReturnsTeamForm() throws Exception {
		// when
		mockMvc.perform(get("/admin/teams/new"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/team-form"))
				.andExpect(model().attributeExists("teamForm"));
	}

	@Test
	void givenExistingTeam_whenGetEditForm_thenReturnsTeamForm() throws Exception {
		// given
		var team = teamRepository.save(new Team("Edit Racing", "EDT"));

		// when
		mockMvc.perform(get("/admin/teams/" + team.getId() + "/edit"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/team-form"))
				.andExpect(model().attributeExists("teamForm", "team"));
	}

	@Test
	void givenExistingTeam_whenSaveUpdatedTeam_thenRedirectsAndUpdates() throws Exception {
		// given
		var team = teamRepository.save(new Team("Original Racing", "ORI"));

		// when
		mockMvc.perform(post("/admin/teams/save")
						.param("id", team.getId().toString())
						.param("name", "Updated Racing")
						.param("shortName", "UPD"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/teams"));

		// then
		var updated = teamRepository.findById(team.getId()).orElseThrow();
		assertEquals("Updated Racing", updated.getName());
		assertEquals("UPD", updated.getShortName());
	}

	@Test
	void givenParentTeam_whenAddSubTeam_thenRedirectsWithSuccess() throws Exception {
		// given
		var parent = teamRepository.save(new Team("Parent Sub Racing", "PSR"));

		// when
		mockMvc.perform(post("/admin/teams/" + parent.getId() + "/add-sub-team")
						.param("subName", "Sub Racing A")
						.param("subShortName", "PSR A"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/teams/" + parent.getId() + "/edit"))
				.andExpect(flash().attributeExists("successMessage"));
	}

	@Test
	void givenParentTeam_whenAddSubTeamWithBlankName_thenRedirectsWithError() throws Exception {
		// given
		var parent = teamRepository.save(new Team("Blank Sub Parent", "BSP"));

		// when
		mockMvc.perform(post("/admin/teams/" + parent.getId() + "/add-sub-team")
						.param("subName", "")
						.param("subShortName", ""))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("errorMessage"));
	}

	@Test
	void givenParentWithSubTeam_whenRemoveSubTeam_thenRedirectsAndDeletes() throws Exception {
		// given
		var parent = teamRepository.save(new Team("Remove Sub Parent", "RSP"));
		var sub = teamRepository.save(new Team("Remove Sub A", "RSP A", parent));

		// when
		mockMvc.perform(post("/admin/teams/" + parent.getId() + "/remove-sub-team")
						.param("subTeamId", sub.getId().toString()))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/teams/" + parent.getId() + "/edit"))
				.andExpect(flash().attributeExists("successMessage"));

		// then
		assertFalse(teamRepository.findById(sub.getId()).isPresent());
	}

	@Test
	void givenExistingTeam_whenDeleteTeam_thenRedirectsAndRemoves() throws Exception {
		// given
		var team = teamRepository.save(new Team("Delete Racing", "DLR"));

		// when
		mockMvc.perform(post("/admin/teams/" + team.getId() + "/delete"))
				.andExpect(status().is3xxRedirection());

		// then
		assertFalse(teamRepository.findById(team.getId()).isPresent());
	}
}
