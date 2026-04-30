package org.ctc.admin.controller;

import org.ctc.TestHelper;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class StandingsControllerTest {

	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private SeasonRepository seasonRepository;
	@Autowired
	private TeamRepository teamRepository;
	@Autowired
	private TestHelper testHelper;
	@Autowired
	private MatchdayRepository matchdayRepository;
	@Autowired
	private MatchRepository matchRepository;
	@Autowired
	private SeasonPhaseRepository seasonPhaseRepository;
	@Autowired
	private SeasonPhaseService seasonPhaseService;

	private Season activeSeason;
	private Season inactiveSeason;

	@BeforeEach
	void setUp() {
		seasonRepository.findByActiveTrue().ifPresent(s -> {
			s.setActive(false);
			seasonRepository.save(s);
		});

		activeSeason = testHelper.createSeason("Standings Active " + UUID.randomUUID().toString().substring(0, 8));
		activeSeason.setActive(true);
		activeSeason = seasonRepository.save(activeSeason);

		inactiveSeason = testHelper.createSeason("Standings Inactive " + UUID.randomUUID().toString().substring(0, 8));
		inactiveSeason.setActive(false);
		inactiveSeason = seasonRepository.save(inactiveSeason);

		var teamA = teamRepository.save(new Team("Alpha Racing", "ALP"));
		var teamB = teamRepository.save(new Team("Bravo Racing", "BRV"));
		activeSeason.addTeam(teamA);
		activeSeason.addTeam(teamB);
		seasonRepository.save(activeSeason);
	}

	@Test
	void givenActiveSeason_whenGetStandings_thenReturnsActiveSeasonStandings() throws Exception {
		// when
		mockMvc.perform(get("/admin/standings"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/standings"))
				.andExpect(model().attributeExists("seasons", "standings", "driverRanking", "selectedSeason"))
				.andExpect(model().attribute("selectedSeason", hasProperty("id", is(activeSeason.getId()))));
	}

	@Test
	void givenSpecificSeasonId_whenGetStandings_thenReturnsSpecificSeasonStandings() throws Exception {
		// when
		mockMvc.perform(get("/admin/standings").param("seasonId", inactiveSeason.getId().toString()))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/standings"))
				.andExpect(model().attributeExists("seasons", "standings", "driverRanking"))
				.andExpect(model().attribute("selectedSeason", hasProperty("id", is(inactiveSeason.getId()))))
				.andExpect(model().attribute("selectedSeasonId", inactiveSeason.getId().toString()));
	}

	@Test
	void whenGetAlltimeStandings_thenReturnsAlltimeView() throws Exception {
		// given - create a matchday and match with scores for activeSeason
		var matchday = new Matchday(activeSeason, "Spieltag 1", 1);
		matchday = matchdayRepository.save(matchday);
		var teamA = activeSeason.getSeasonTeams().stream()
				.map(SeasonTeam::getTeam).findFirst().orElseThrow();
		var teamB = activeSeason.getSeasonTeams().stream()
				.map(SeasonTeam::getTeam).skip(1).findFirst().orElseThrow();
		var match = new Match(matchday, teamA, teamB);
		match.setHomeScore(70);
		match.setAwayScore(46);
		matchRepository.save(match);

		// when
		mockMvc.perform(get("/admin/standings").param("seasonId", "alltime"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/standings"))
				.andExpect(model().attributeExists("seasons", "standings", "driverRanking"))
				.andExpect(model().attribute("isAlltime", true))
				.andExpect(model().attribute("selectedSeasonId", "alltime"))
				.andExpect(model().attributeDoesNotExist("selectedSeason"))
				.andExpect(model().attribute("standings", hasSize(greaterThan(0))));
	}

	@Test
	void givenNoActiveSeasonAndNoParam_whenGetStandings_thenReturnsSelectSeasonState() throws Exception {
		// given
		activeSeason.setActive(false);
		seasonRepository.save(activeSeason);

		// when
		mockMvc.perform(get("/admin/standings"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/standings"))
				.andExpect(model().attributeExists("seasons"))
				.andExpect(model().attribute("isAlltime", false))
				.andExpect(model().attributeDoesNotExist("standings", "driverRanking", "selectedSeason"));
	}

	@Test
	void givenSwissSeason_whenGetStandingsForSeason_thenReturnsSwissSeasonStandings() throws Exception {
		// given
		var swissSeason = testHelper.createSeason("Swiss Season " + UUID.randomUUID().toString().substring(0, 8));
		swissSeason.setFormat(SeasonFormat.SWISS);
		swissSeason = seasonRepository.save(swissSeason);

		// when
		mockMvc.perform(get("/admin/standings").param("seasonId", swissSeason.getId().toString()))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/standings"))
				.andExpect(model().attributeExists("seasons", "standings", "driverRanking"))
				.andExpect(model().attribute("selectedSeason", hasProperty("format", is(SeasonFormat.SWISS))));
	}

	@Test
	void givenMultipleSeasons_whenGetStandings_thenAlwaysPopulatesSeasonsListWithAllSeasons() throws Exception {
		// when
		mockMvc.perform(get("/admin/standings"))
				// then
				.andExpect(status().isOk())
				.andExpect(model().attribute("seasons", hasSize(greaterThanOrEqualTo(2))));
	}

	// --- Phase 60 UI-05: phase-scoped standings (D-12, D-30, D-31, D-32, D-33) ---

	@Test
	void givenLegacySeasonParam_whenGetStandings_thenResolvesToRegularPhase() throws Exception {
		// given
		var season = testHelper.createSeason("T-Phase60-LegacyURL");

		// when: legacy ?seasonId= param
		mockMvc.perform(get("/admin/standings").param("seasonId", season.getId().toString()))
				// then: Phase 60 implementation must add "phase" to model (D-12 bridge)
				.andExpect(status().isOk())
				.andExpect(view().name("admin/standings"))
				.andExpect(model().attributeExists("phase"));
	}

	@Test
	void givenGroupsPhase_whenGetStandingsWithoutGroup_thenCombinedViewWithGroupColumn() throws Exception {
		// given: GROUPS-layout REGULAR phase with 2 groups
		var season = testHelper.createSeason("T-Phase60-CombinedView");
		var regular = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();
		regular.setLayout(PhaseLayout.GROUPS);
		var groupA = seasonPhaseService.createGroup(regular.getId(), "T-Phase60-Group-A", 0);
		var groupB = seasonPhaseService.createGroup(regular.getId(), "T-Phase60-Group-B", 1);

		// when: ?phase=X without &group= → combined view
		mockMvc.perform(get("/admin/standings").param("phase", regular.getId().toString()))
				// then: Phase 60 implementation must add these model attrs (D-30, D-31)
				.andExpect(status().isOk())
				.andExpect(model().attribute("combinedView", true))
				.andExpect(model().attribute("showGroupColumn", true));
	}

	@Test
	void givenSwissPerGroup_whenGetStandings_thenShowBuchholzTrue() throws Exception {
		// given: GROUPS+SWISS phase with a specific group
		var season = testHelper.createSeason("T-Phase60-SwissGroup");
		var regular = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();
		regular.setLayout(PhaseLayout.GROUPS);
		regular.setFormat(SeasonFormat.SWISS);
		seasonPhaseRepository.save(regular);
		var group = seasonPhaseService.createGroup(regular.getId(), "T-Phase60-Swiss-Group-A", 0);

		// when: ?phase=X&group=Y → Buchholz mode
		mockMvc.perform(get("/admin/standings")
						.param("phase", regular.getId().toString())
						.param("group", group.getId().toString()))
				// then: Phase 60 implementation must set showBuchholz=true for SWISS (D-33)
				.andExpect(status().isOk())
				.andExpect(model().attribute("showBuchholz", true));
	}
}
