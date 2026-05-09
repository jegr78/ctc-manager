package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SwissPairingServiceTest {

	@Autowired
	private SwissPairingService swissPairingService;
	@Autowired
	private SeasonRepository seasonRepository;
	@Autowired
	private RaceRepository raceRepository;
	@Autowired
	private MatchRepository matchRepository;
	@Autowired
	private TeamRepository teamRepository;
	@Autowired
	private DriverRepository driverRepository;
	@Autowired
	private SeasonDriverRepository seasonDriverRepository;
	@Autowired
	private RaceScoringRepository raceScoringRepository;
	@Autowired
	private MatchScoringRepository matchScoringRepository;
	@Autowired
	private SeasonPhaseRepository seasonPhaseRepository;
	@Autowired
	private SeasonPhaseGroupRepository seasonPhaseGroupRepository;
	@Autowired
	private PhaseTeamRepository phaseTeamRepository;
	@Autowired
	private MatchdayRepository matchdayRepository;

	private Season season;
	private RaceScoring raceScoring;
	private MatchScoring matchScoring;
	private SeasonPhase regularPhase; // REGULAR phase backing the season for @Deprecated bridge

	@BeforeEach
	void setUp() {
		var uniqueSuffix = UUID.randomUUID().toString().substring(0, 4);
		raceScoring = raceScoringRepository.save(
				new RaceScoring("Swiss RS " + uniqueSuffix, "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
		matchScoring = matchScoringRepository.save(
				new MatchScoring("Swiss MS " + uniqueSuffix, 3, 1, 0));

		season = new Season("Swiss Test " + uniqueSuffix, 2026, 1);
		season = seasonRepository.save(season);

		// Create a REGULAR phase wired for SWISS format so SwissPairingService accepts it.
		// format/scoring live on the phase.
		var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		phase.setFormat(SeasonFormat.SWISS);
		phase.setRaceScoring(raceScoring);
		phase.setMatchScoring(matchScoring);
		phase.setTotalRounds(99);
		regularPhase = seasonPhaseRepository.save(phase);
	}

	@Test
	void givenSwissSeasonWith6Teams_whenGenerateNextRound_thenCreatesThreePairings() {
		// given
		addTeams(6);

		// when
		var matchday = swissPairingService.generateNextRound(regularPhase.getId(), null);

		// then
		assertEquals("Round 1", matchday.getLabel());
		assertEquals(1, matchday.getSortIndex());

		var races = raceRepository.findByMatchdayId(matchday.getId());
		assertEquals(3, races.size());
		races.forEach(r -> assertFalse(r.isBye()));
	}

	@Test
	void givenOddNumberOfTeams_whenGenerateNextRound_thenOneByeCreated() {
		// given
		addTeams(5);

		// when
		var matchday = swissPairingService.generateNextRound(regularPhase.getId(), null);

		// then
		var races = raceRepository.findByMatchdayId(matchday.getId());
		assertEquals(3, races.size()); // 2 regular + 1 bye

		long byeCount = races.stream().filter(Race::isBye).count();
		assertEquals(1, byeCount);

		var byeRace = races.stream().filter(Race::isBye).findFirst().orElseThrow();
		assertNotNull(byeRace.getHomeTeam());
		assertNull(byeRace.getAwayTeam());
	}

	@Test
	void givenTotalRoundsReached_whenGenerateNextRound_thenThrowsException() {
		// given — set totalRounds=1 on both season and the REGULAR phase
		// totalRounds lives on the phase only.
		regularPhase.setTotalRounds(1);
		addTeams(4);
		seasonRepository.save(season);
		regularPhase = seasonPhaseRepository.save(regularPhase);

		var md = swissPairingService.generateNextRound(regularPhase.getId(), null);
		addDummyResults(md.getId());

		// when / then
		assertThrows(IllegalStateException.class,
				() -> swissPairingService.generateNextRound(regularPhase.getId(), null));
	}

	@Test
	void givenLeagueSeason_whenGenerateNextRound_thenThrowsException() {
		// given — a LEAGUE-format REGULAR LEAGUE-layout phase (format=LEAGUE → not Swiss → should throw)
		var s = buildTestSeason();
		var leaguePhase = new SeasonPhase(s, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		leaguePhase.setFormat(SeasonFormat.LEAGUE); // LEAGUE format → not Swiss → should throw
		var saved = seasonPhaseRepository.save(leaguePhase);
		addTeamsToPhase(saved, null, 4);

		UUID phaseId = saved.getId();

		// when / then — LEAGUE format phase cannot generate Swiss rounds
		assertThrows(IllegalArgumentException.class,
				() -> swissPairingService.generateNextRound(phaseId, null));
	}

	@Test
	void givenTwoCompletedRounds_whenGenerateNextRound_thenNoRematches() {
		// given
		addTeams(8);

		var md1 = swissPairingService.generateNextRound(regularPhase.getId(), null);
		addDummyResults(md1.getId());

		var races1 = raceRepository.findByMatchdayId(md1.getId());
		assertEquals(4, races1.size());
		Set<String> firstRoundPairs = new HashSet<>();
		for (var race : races1) {
			firstRoundPairs.add(pairKey(race.getHomeTeam().getId(), race.getAwayTeam().getId()));
		}

		// when
		var md2 = swissPairingService.generateNextRound(regularPhase.getId(), null);
		var races2 = raceRepository.findByMatchdayId(md2.getId());

		// then
		assertEquals(4, races2.size());

		for (var race : races2) {
			String pair = pairKey(race.getHomeTeam().getId(), race.getAwayTeam().getId());
			assertFalse(firstRoundPairs.contains(pair),
					"Rematch: " + race.getHomeTeam().getShortName() + " vs " + race.getAwayTeam().getShortName());
		}
	}

	@Test
	void givenIncompleteCurrentRound_whenGenerateNextRound_thenThrowsException() {
		// given
		addTeams(4);

		swissPairingService.generateNextRound(regularPhase.getId(), null);

		// when / then
		var ex = assertThrows(IllegalStateException.class,
				() -> swissPairingService.generateNextRound(regularPhase.getId(), null));
		assertTrue(ex.getMessage().toLowerCase().contains("incomplete"),
				"Expected message to contain 'incomplete', got: " + ex.getMessage());
	}

	@Test
	void givenReplacedTeam_whenGenerateNextRound_thenReplacedTeamExcluded() {
		// given — register teams[1..3] and replacementTeam in the REGULAR phase roster;
		// teams[0] (the replaced team) is deliberately NOT added to the phase roster,
		// so it won't appear in pairings (PhaseTeam is the source of truth in the
		// canonical path; succession is no longer needed for team-pool filtering).
		var teams = new ArrayList<Team>();
		for (int i = 0; i < 4; i++) {
			var team = teamRepository.save(new Team("SwissT " + i + "_" + UUID.randomUUID().toString().substring(0, 4),
					"SW" + i + UUID.randomUUID().toString().substring(0, 2)));
			season.addTeam(team);
			teams.add(team);
		}
		seasonRepository.save(season);

		// Team 0 replaced by a new team
		var replacementTeam = teamRepository.save(new Team("Replacement " + UUID.randomUUID().toString().substring(0, 4),
				"REP" + UUID.randomUUID().toString().substring(0, 2)));
		season.addTeam(replacementTeam);
		season = seasonRepository.saveAndFlush(season);

		var stOld = season.findSeasonTeam(teams.get(0)).orElseThrow();
		var stNew = season.findSeasonTeam(replacementTeam).orElseThrow();
		stOld.setSuccessor(stNew);
		season = seasonRepository.saveAndFlush(season);

		// Phase roster: teams[1], teams[2], teams[3] + replacementTeam (teams[0] excluded)
		phaseTeamRepository.save(new PhaseTeam(regularPhase, teams.get(1)));
		phaseTeamRepository.save(new PhaseTeam(regularPhase, teams.get(2)));
		phaseTeamRepository.save(new PhaseTeam(regularPhase, teams.get(3)));
		phaseTeamRepository.save(new PhaseTeam(regularPhase, replacementTeam));

		// when
		var matchday = swissPairingService.generateNextRound(regularPhase.getId(), null);

		// then — 4 active teams (teams[1..3] + replacement), replaced team excluded
		var races = raceRepository.findByMatchdayId(matchday.getId());
		assertEquals(2, races.size()); // 4 teams = 2 pairings

		// Replaced team should not appear in any race
		for (var race : races) {
			assertNotEquals(teams.get(0).getId(), race.getHomeTeam().getId());
			if (race.getAwayTeam() != null) {
				assertNotEquals(teams.get(0).getId(), race.getAwayTeam().getId());
			}
		}
	}

	// Phase/group-aware tests (D-17, D-21, SVC-04) — TDD-RED: new canonical
	// signatures (UUID phaseId, UUID groupId) do not exist yet

	@Test
	void givenSwissLeaguePhase_whenGenerateNextRound_thenPairingsCreated() {
		// given — LEAGUE-layout SWISS phase, groupId=null (valid)
		var phase = buildSwissLeaguePhase();
		addTeamsToPhase(phase, null, 4);

		// when
		var matchday = swissPairingService.generateNextRound(phase.getId(), null);

		// then
		assertThat(matchday).isNotNull();
		assertThat(matchday.getPhase()).isNotNull();
		assertThat(matchday.getPhase().getId()).isEqualTo(phase.getId());
		var races = raceRepository.findByMatchdayId(matchday.getId());
		assertThat(races).hasSize(2);
	}

	@Test
	void givenSwissGroupsPhase_whenGenerateNextRoundForGroupA_thenOnlyGroupAAdvances() {
		// given — GROUPS-layout SWISS phase with two groups (A: 4 teams, B: 4 teams)
		var phase = buildSwissGroupsPhase();
		var groupA = phase.getGroups().get(0);
		var groupB = phase.getGroups().get(1);
		addTeamsToPhase(phase, groupA, 4);
		addTeamsToPhase(phase, groupB, 4);

		// round 1 for both groups
		var r1A = swissPairingService.generateNextRound(phase.getId(), groupA.getId());
		swissPairingService.generateNextRound(phase.getId(), groupB.getId());
		// complete round 1 for group A so round 2 can be generated
		addDummyResults(r1A.getId());

		// round 2 for group A only
		swissPairingService.generateNextRound(phase.getId(), groupA.getId());

		// then — group A is at round 2, group B is at round 1
		assertThat(swissPairingService.getCurrentRound(phase.getId(), groupA.getId())).isEqualTo(2);
		assertThat(swissPairingService.getCurrentRound(phase.getId(), groupB.getId())).isEqualTo(1);
	}

	@Test
	void givenGroupsPhaseWithOddTeams_whenGetByeTeamsForGroupA_thenSingleByeTeamForGroupA() {
		// given — GROUPS-layout SWISS phase; group A has 3 teams (odd), group B has 4 teams
		var phase = buildSwissGroupsPhase();
		var groupA = phase.getGroups().get(0);
		var groupB = phase.getGroups().get(1);
		addTeamsToPhase(phase, groupA, 3); // odd → generates a bye
		addTeamsToPhase(phase, groupB, 4);

		// when — generate round 1 for group A (produces a bye)
		swissPairingService.generateNextRound(phase.getId(), groupA.getId());

		// then — group A has exactly 1 bye team; group B has no byes
		var byeTeamsA = swissPairingService.getByeTeams(phase.getId(), groupA.getId());
		var byeTeamsB = swissPairingService.getByeTeams(phase.getId(), groupB.getId());
		assertThat(byeTeamsA).hasSize(1);
		assertThat(byeTeamsB).isEmpty();
	}

	@Test
	void givenGroupsPhase_whenGroupAAtRound2GroupBAtRound1_thenIsCurrentRoundCompleteIsPerGroup() {
		// given — GROUPS-layout SWISS phase; 4 teams per group
		var phase = buildSwissGroupsPhase();
		var groupA = phase.getGroups().get(0);
		var groupB = phase.getGroups().get(1);
		addTeamsToPhase(phase, groupA, 4);
		addTeamsToPhase(phase, groupB, 4);

		// round 1 for both groups
		var r1A = swissPairingService.generateNextRound(phase.getId(), groupA.getId());
		var r1B = swissPairingService.generateNextRound(phase.getId(), groupB.getId());
		// complete round 1 for both groups; then generate round 2 only for group A
		addDummyResults(r1A.getId());
		addDummyResults(r1B.getId());

		// round 2 only group A
		swissPairingService.generateNextRound(phase.getId(), groupA.getId());

		// then — group A is at round 2 (no results yet → incomplete),
		//         group B is at round 1 (results complete → isComplete=true, no round 2 pending)
		assertThat(swissPairingService.getCurrentRound(phase.getId(), groupA.getId())).isEqualTo(2);
		assertThat(swissPairingService.getCurrentRound(phase.getId(), groupB.getId())).isEqualTo(1);
		assertThat(swissPairingService.isCurrentRoundComplete(phase.getId(), groupA.getId())).isFalse();
		assertThat(swissPairingService.isCurrentRoundComplete(phase.getId(), groupB.getId())).isTrue();
	}

	@Test
	void givenLeaguePhaseAndGroupId_whenGenerateNextRound_thenThrowsIllegalArgument() {
		// given — LEAGUE-layout phase + non-null groupId (invalid)
		var phase = buildSwissLeaguePhase();
		addTeamsToPhase(phase, null, 4);

		// when / then
		assertThatThrownBy(() -> swissPairingService.generateNextRound(phase.getId(), UUID.randomUUID()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("LEAGUE");
	}

	@Test
	void givenGroupsLayoutAndNullGroupId_whenGenerateNextRound_thenThrowsIllegalArgument() {
		// given — GROUPS-layout phase + null groupId (invalid)
		var phase = buildSwissGroupsPhase();

		// when / then
		assertThatThrownBy(() -> swissPairingService.generateNextRound(phase.getId(), null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("GROUPS");
	}

	// Helper factories for phase-aware tests

	/**
	 * Creates a fresh test season (separate from the setUp season to avoid
	 * UNIQUE(season_id, phase_type) conflicts with the regularPhase created in setUp).
	 */
	private Season buildTestSeason() {
		var s = new Season("Phase58-Test-Swiss-" + UUID.randomUUID().toString().substring(0, 4), 9999, 99);
		return seasonRepository.save(s);
	}

	/** Creates and persists a LEAGUE-layout REGULAR phase with SWISS format on a fresh test season. */
	private SeasonPhase buildSwissLeaguePhase() {
		var s = buildTestSeason();
		var phase = new SeasonPhase(s, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		// format/scoring/totalRounds live on the phase.
		phase.setFormat(SeasonFormat.SWISS);
		phase.setRaceScoring(raceScoring);
		phase.setMatchScoring(matchScoring);
		phase.setTotalRounds(99);
		return seasonPhaseRepository.save(phase);
	}

	/** Creates and persists a GROUPS-layout REGULAR phase with SWISS format and two groups on a fresh test season. */
	private SeasonPhase buildSwissGroupsPhase() {
		var s = buildTestSeason();
		var phase = new SeasonPhase(s, PhaseType.REGULAR, PhaseLayout.GROUPS, 0);
		// format/scoring live on the phase.
		phase.setFormat(SeasonFormat.SWISS);
		phase.setRaceScoring(raceScoring);
		phase.setMatchScoring(matchScoring);
		phase.setTotalRounds(99);
		phase = seasonPhaseRepository.save(phase);
		var groupA = seasonPhaseGroupRepository.save(new SeasonPhaseGroup(phase, "Phase58-Test-Swiss-A", 0));
		var groupB = seasonPhaseGroupRepository.save(new SeasonPhaseGroup(phase, "Phase58-Test-Swiss-B", 1));
		phase.getGroups().add(groupA);
		phase.getGroups().add(groupB);
		return phase;
	}

	/** Creates teams and assigns them as PhaseTeam entries for the given phase/group. */
	private List<Team> addTeamsToPhase(SeasonPhase phase, SeasonPhaseGroup group, int count) {
		var teams = new ArrayList<Team>();
		var suffix = UUID.randomUUID().toString().substring(0, 4);
		for (int i = 0; i < count; i++) {
			var team = teamRepository.save(new Team("Phase58-Test-SwT " + i + "_" + suffix,
					"P8S" + i + suffix.substring(0, 2)));
			var pt = new PhaseTeam(phase, team);
			pt.setGroup(group);
			phaseTeamRepository.save(pt);
			teams.add(team);
		}
		return teams;
	}

	// Existing legacy helpers (updated to also populate PhaseTeam for the
	// regularPhase so the canonical generateNextRound(phaseId, null) finds teams)

	private void addTeams(int count) {
		for (int i = 0; i < count; i++) {
			var team = teamRepository.save(new Team("SwissT " + i + "_" + UUID.randomUUID().toString().substring(0, 4),
					"SW" + i + UUID.randomUUID().toString().substring(0, 2)));
			season.addTeam(team);
			// Also register as PhaseTeam on the REGULAR phase so the canonical
			// generateNextRound(phaseId, null) (called via @Deprecated bridge) finds them
			phaseTeamRepository.save(new PhaseTeam(regularPhase, team));
		}
		seasonRepository.save(season);
	}

	private void addDummyResults(UUID matchdayId) {
		var races = raceRepository.findByMatchdayId(matchdayId);
		for (var race : races) {
			if (race.isBye()) continue;

			var homeDriver = driverRepository.save(new Driver(
					"sh_" + UUID.randomUUID().toString().substring(0, 8), "Home Driver"));
			seasonDriverRepository.save(new SeasonDriver(season, homeDriver, race.getHomeTeam()));

			var awayDriver = driverRepository.save(new Driver(
					"sa_" + UUID.randomUUID().toString().substring(0, 8), "Away Driver"));
			seasonDriverRepository.save(new SeasonDriver(season, awayDriver, race.getAwayTeam()));

			var hr = new RaceResult();
			hr.setRace(race);
			hr.setDriver(homeDriver);
			hr.setPosition(1);
			hr.setQualiPosition(1);
			hr.setPointsTotal(20);
			race.getResults().add(hr);

			var ar = new RaceResult();
			ar.setRace(race);
			ar.setDriver(awayDriver);
			ar.setPosition(2);
			ar.setQualiPosition(2);
			ar.setPointsTotal(10);
			race.getResults().add(ar);

			// Set scores on the Match
			if (race.getMatch() != null) {
				race.getMatch().setHomeScore(20);
				race.getMatch().setAwayScore(10);
			}

			raceRepository.save(race);
		}
	}

	private String pairKey(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
	}
}
