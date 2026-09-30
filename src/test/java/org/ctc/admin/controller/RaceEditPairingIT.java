package org.ctc.admin.controller;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.service.MatchService;
import org.ctc.domain.service.PlayoffService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Edits races through {@code POST /admin/races/save} with real two-leg overrides and a real playoff
 * matchup, without a test transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class RaceEditPairingIT {

	private static final LocalDateTime NEW_TIME = LocalDateTime.of(2026, 6, 4, 20, 0);

	@Autowired MockMvc mockMvc;
	@Autowired TestHelper testHelper;
	@Autowired MatchService matchService;
	@Autowired PlayoffService playoffService;
	@Autowired RaceRepository raceRepository;
	@Autowired MatchRepository matchRepository;
	@Autowired SeasonRepository seasonRepository;
	@Autowired SeasonPhaseRepository seasonPhaseRepository;
	@Autowired TeamRepository teamRepository;
	@Autowired PlayoffMatchupRepository playoffMatchupRepository;
	@Autowired PlayoffRepository playoffRepository;
	@Autowired MatchdayRepository matchdayRepository;
	@Autowired TransactionTemplate transactionTemplate;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private Season season;
	private Team alpha;
	private Team bravo;
	private Team charlie;
	private Matchday matchday;
	private Match match;
	private Race leg1;
	private Race leg2;

	@BeforeEach
	void createTwoLegMatch() {
		season = testHelper.createSeason("Test_Pairing_" + id);
		alpha = testHelper.createTeam("Test Pairing Alpha " + id, "Test_PA_" + id);
		bravo = testHelper.createTeam("Test Pairing Bravo " + id, "Test_PB_" + id);
		charlie = testHelper.createTeam("Test Pairing Charlie " + id, "Test_PC_" + id);
		transactionTemplate.executeWithoutResult(status -> {
			var s = seasonRepository.findById(season.getId()).orElseThrow();
			s.addTeam(alpha);
			s.addTeam(bravo);
			s.addTeam(charlie);
			var regular = seasonPhaseRepository.findById(s.getPhases().getFirst().getId()).orElseThrow();
			regular.setLegs(2);
		});
		matchday = testHelper.createMatchdayInRegularPhase(season, "Test_Pairing MD " + id, 1);
		match = matchService.createMatchWithLegs(matchday, alpha, bravo, false);
		List<Race> legs = transactionTemplate.execute(status -> raceRepository.findByMatchId(match.getId()).stream()
				.sorted(Comparator.comparing(r -> r.getHomeTeam().getId().equals(alpha.getId()) ? 0 : 1))
				.toList());
		leg1 = legs.get(0);
		leg2 = legs.get(1);
	}

	@AfterEach
	void removeFixture() {
		raceRepository.deleteAll(raceRepository.findByMatchdaySeasonId(season.getId()));
		transactionTemplate.executeWithoutResult(status -> playoffRepository.findBySeasonId(season.getId())
				.ifPresent(playoff -> {
					playoff.getRounds().forEach(round -> round.getMatchups().forEach(m -> m.setNextMatchup(null)));
					playoffRepository.flush();
					playoffRepository.delete(playoff);
				}));
		testHelper.deleteSeasonCascade(season);
		teamRepository.deleteAll(List.of(alpha, bravo, charlie));
	}

	@Test
	void givenLeg2DateChange_whenSaved_thenPairingAndBothLegOrientationsStay() throws Exception {
		// when
		save(leg2.getId(), matchday, bravo, alpha).andExpect(flash().attributeExists("successMessage"));

		// then
		assertPairing(alpha, bravo);
		assertLegs(alpha, bravo, bravo, alpha);
		transactionTemplate.executeWithoutResult(status ->
				assertThat(raceRepository.findById(leg2.getId()).orElseThrow().getDateTime()).isEqualTo(NEW_TIME));
	}

	@Test
	void givenUnscoredMatch_whenLeg2PairingChanges_thenMatchAndEveryLegFollowTheLegOrientation() throws Exception {
		// when
		save(leg2.getId(), matchday, charlie, alpha).andExpect(flash().attributeExists("successMessage"));

		// then
		assertPairing(alpha, charlie);
		assertLegs(alpha, charlie, charlie, alpha);
	}

	@Test
	void givenScoredLeg1_whenLeg2PairingChanges_thenRejectedAndNothingChanges() throws Exception {
		// given
		addResult(leg1);

		// when
		save(leg2.getId(), matchday, charlie, alpha)
				.andExpect(flash().attribute("errorMessage", "Teams, matchday and phase cannot change after results were entered"));

		// then
		assertPairing(alpha, bravo);
		assertLegs(alpha, bravo, bravo, alpha);
	}

	@Test
	void givenScoredLeg1_whenLeg1MovesToAnotherPhase_thenRejected() throws Exception {
		// given
		addResult(leg1);
		var playoff = playoffService.createPlayoff(season.getId(), "Test Pairing Playoff " + id, 2);
		var otherPhaseMatchday = matchdayRepository.save(new Matchday(playoff.getPhase(), "Test_Pairing PO " + id, 150));

		// when
		save(leg1.getId(), otherPhaseMatchday, alpha, bravo)
				.andExpect(flash().attribute("errorMessage", "Teams, matchday and phase cannot change after results were entered"));

		// then
		transactionTemplate.executeWithoutResult(status ->
				assertThat(raceRepository.findById(leg1.getId()).orElseThrow().getMatchday().getId())
						.isEqualTo(matchday.getId()));
	}

	@Test
	void givenScoredLeg1_whenLeg1MovesToAnotherMatchdayOfThePhase_thenRejected() throws Exception {
		// given
		addResult(leg1);
		var laterMatchday = testHelper.createMatchdayInRegularPhase(season, "Test_Pairing MD2 " + id, 2);

		// when
		save(leg1.getId(), laterMatchday, alpha, bravo)
				.andExpect(flash().attribute("errorMessage", "Teams, matchday and phase cannot change after results were entered"));

		// then
		transactionTemplate.executeWithoutResult(status ->
				assertThat(raceRepository.findById(leg1.getId()).orElseThrow().getMatchday().getId())
						.isEqualTo(matchday.getId()));
	}

	@Test
	void givenPlayoffRace_whenDateEdited_thenNoRegularMatchIsCreated() throws Exception {
		// given
		var playoff = playoffService.createPlayoff(season.getId(), "Test Pairing Playoff " + id, 2);
		var matchupId = transactionTemplate.execute(status -> {
			var matchup = playoffMatchupRepository.findAll().stream()
					.filter(m -> m.getRound().getPlayoff().getId().equals(playoff.getId()))
					.findFirst().orElseThrow();
			matchup.setTeam1(alpha);
			matchup.setTeam2(charlie);
			return matchup.getId();
		});
		var playoffRace = playoffService.addRaceToMatchup(matchupId, null, null, null);
		long matchesBefore = matchRepository.count();

		// when
		save(playoffRace.getId(), playoffRace.getMatchday(), alpha, charlie)
				.andExpect(flash().attributeExists("successMessage"));

		// then
		assertThat(matchRepository.count()).as("a playoff edit must not create a regular Match").isEqualTo(matchesBefore);
		transactionTemplate.executeWithoutResult(status -> {
			var race = raceRepository.findById(playoffRace.getId()).orElseThrow();
			assertThat(race.getMatch()).isNull();
			assertThat(race.getPlayoffMatchup().getId()).isEqualTo(matchupId);
		});
	}

	@Test
	void givenPlayoffRace_whenTeamsChanged_thenRejected() throws Exception {
		// given
		var playoff = playoffService.createPlayoff(season.getId(), "Test Pairing Playoff " + id, 2);
		var matchupId = transactionTemplate.execute(status -> {
			var matchup = playoffMatchupRepository.findAll().stream()
					.filter(m -> m.getRound().getPlayoff().getId().equals(playoff.getId()))
					.findFirst().orElseThrow();
			matchup.setTeam1(alpha);
			matchup.setTeam2(charlie);
			return matchup.getId();
		});
		var playoffRace = playoffService.addRaceToMatchup(matchupId, null, null, null);

		// when / then
		save(playoffRace.getId(), playoffRace.getMatchday(), alpha, bravo)
				.andExpect(flash().attribute("errorMessage",
						"The teams and phase of a playoff race come from its playoff matchup"));
	}

	private ResultActions save(UUID raceId, Matchday targetMatchday, Team home, Team away) throws Exception {
		return mockMvc.perform(post("/admin/races/save")
						.param("id", raceId.toString())
						.param("matchdayId", targetMatchday.getId().toString())
						.param("homeTeamId", home.getId().toString())
						.param("awayTeamId", away.getId().toString())
						.param("dateTime", NEW_TIME.toString()))
				.andExpect(status().is3xxRedirection());
	}

	private void addResult(Race race) {
		var driver = testHelper.createDriver("Test_Pairing_" + id + "_Drv", "Test Pairing Driver");
		transactionTemplate.executeWithoutResult(status -> {
			var managed = raceRepository.findById(race.getId()).orElseThrow();
			managed.getResults().add(new RaceResult(managed, driver, 1, 1, false));
		});
	}

	private void assertPairing(Team home, Team away) {
		transactionTemplate.executeWithoutResult(status -> {
			var stored = matchRepository.findById(match.getId()).orElseThrow();
			assertThat(stored.getHomeTeam().getId()).as("match home").isEqualTo(home.getId());
			assertThat(stored.getAwayTeam().getId()).as("match away").isEqualTo(away.getId());
		});
	}

	private void assertLegs(Team leg1Home, Team leg1Away, Team leg2Home, Team leg2Away) {
		transactionTemplate.executeWithoutResult(status -> {
			var first = raceRepository.findById(leg1.getId()).orElseThrow();
			var second = raceRepository.findById(leg2.getId()).orElseThrow();
			assertThat(first.getHomeTeam().getId()).as("leg 1 home").isEqualTo(leg1Home.getId());
			assertThat(first.getAwayTeam().getId()).as("leg 1 away").isEqualTo(leg1Away.getId());
			assertThat(second.getHomeTeam().getId()).as("leg 2 home").isEqualTo(leg2Home.getId());
			assertThat(second.getAwayTeam().getId()).as("leg 2 away").isEqualTo(leg2Away.getId());
		});
	}
}
