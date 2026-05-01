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

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class MatchdayGeneratorServiceTest {

	@Autowired
	private MatchdayGeneratorService matchdayGeneratorService;
	@Autowired
	private SeasonRepository seasonRepository;
	@Autowired
	private TeamRepository teamRepository;
	@Autowired
	private MatchRepository matchRepository;
	@Autowired
	private MatchdayRepository matchdayRepository;
	@Autowired
	private RaceRepository raceRepository;
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

	private Season season;
	private RaceScoring raceScoring;
	private MatchScoring matchScoring;
	private SeasonPhase regularPhase; // REGULAR phase backing the season for @Deprecated bridge

	@BeforeEach
	void setUp() {
		var suffix = UUID.randomUUID().toString().substring(0, 4);
		raceScoring = raceScoringRepository.save(
				new RaceScoring("Gen RS " + suffix, "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
		matchScoring = matchScoringRepository.save(
				new MatchScoring("Gen MS " + suffix, 3, 1, 0));

		season = new Season("Generator Test " + suffix, 2026, 1);
		season = seasonRepository.save(season);

		// Create a REGULAR LEAGUE phase so the @Deprecated bridge (generate(seasonId,...))
		// can resolve via seasonPhaseService.findRegularPhase(seasonId).
		var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		regularPhase = seasonPhaseRepository.save(phase);
	}

	@Test
	void givenLeagueWith6Teams_whenGenerate_thenCreates5MatchdaysAllTeamsPlayOncePerRound() {
		// given
		addTeams(6);

		// when
		matchdayGeneratorService.generate(regularPhase.getId(), null, 5, false);

		// then
		var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());
		assertThat(matchdays).hasSize(5);
		assertThat(matchdays.get(0).getLabel()).isEqualTo("MD 1");
		assertThat(matchdays.get(4).getLabel()).isEqualTo("MD 5");

		// Each matchday has 3 matches (6 teams / 2)
		for (var md : matchdays) {
			var matches = matchRepository.findByMatchdayId(md.getId());
			assertThat(matches).hasSize(3);
		}

		// Each team plays exactly once per matchday
		for (var md : matchdays) {
			var matches = matchRepository.findByMatchdayId(md.getId());
			var teamIds = new HashSet<UUID>();
			for (var match : matches) {
				assertThat(teamIds.add(match.getHomeTeam().getId()))
						.as("Team %s appears twice in %s", match.getHomeTeam().getShortName(), md.getLabel())
						.isTrue();
				assertThat(teamIds.add(match.getAwayTeam().getId()))
						.as("Team %s appears twice in %s", match.getAwayTeam().getShortName(), md.getLabel())
						.isTrue();
			}
		}
	}

	@Test
	void givenRoundRobinWith4Teams_whenGenerate3Rounds_thenEachPairPlaysOnce() {
		// given
		seasonRepository.save(season);
		var teams = addTeams(4);

		// when
		matchdayGeneratorService.generate(regularPhase.getId(), null, 3, false);

		// then
		var allMatches = matchRepository.findByMatchdaySeasonId(season.getId());
		assertThat(allMatches).hasSize(6); // C(4,2) = 6 pairings

		// Every pair plays exactly once
		var pairings = new HashSet<String>();
		for (var match : allMatches) {
			var pair = pairKey(match.getHomeTeam().getId(), match.getAwayTeam().getId());
			assertThat(pairings.add(pair)).as("Duplicate pairing found").isTrue();
		}
	}

	@Test
	void givenHomeAndAway_whenGenerate_thenDoubleMatchdaysReversedPairings() {
		// given
		addTeams(4);

		// when
		matchdayGeneratorService.generate(regularPhase.getId(), null, 3, true);

		// then
		var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());
		assertThat(matchdays).hasSize(6); // 3 rounds * 2

		assertThat(matchdays.get(0).getLabel()).isEqualTo("MD 1");
		assertThat(matchdays.get(5).getLabel()).isEqualTo("MD 6");

		// Collect first-half and second-half pairings
		var firstHalf = new ArrayList<String>();
		var secondHalf = new ArrayList<String>();
		for (int i = 0; i < 3; i++) {
			for (var match : matchRepository.findByMatchdayId(matchdays.get(i).getId())) {
				firstHalf.add(match.getHomeTeam().getId() + "→" + match.getAwayTeam().getId());
			}
		}
		for (int i = 3; i < 6; i++) {
			for (var match : matchRepository.findByMatchdayId(matchdays.get(i).getId())) {
				secondHalf.add(match.getHomeTeam().getId() + "→" + match.getAwayTeam().getId());
			}
		}

		// For each first-half match H→A, second-half should have A→H
		for (var pair : firstHalf) {
			var parts = pair.split("→");
			var reversed = parts[1] + "→" + parts[0];
			assertThat(secondHalf).contains(reversed);
		}
	}

	@Test
	void givenOddTeamCount_whenGenerate_thenByesCreated() {
		// given
		addTeams(5);

		// when
		matchdayGeneratorService.generate(regularPhase.getId(), null, 5, false);

		// then
		var matchdays = matchdayRepository.findBySeasonIdOrderBySortIndexAsc(season.getId());
		assertThat(matchdays).hasSize(5);

		// Each matchday has 3 matches: 2 regular + 1 bye
		for (var md : matchdays) {
			var matches = matchRepository.findByMatchdayId(md.getId());
			assertThat(matches).hasSize(3);

			long byeCount = matches.stream().filter(Match::isBye).count();
			assertThat(byeCount).isEqualTo(1);

			var byeMatch = matches.stream().filter(Match::isBye).findFirst().orElseThrow();
			assertThat(byeMatch.getHomeTeam()).isNotNull();
			assertThat(byeMatch.getAwayTeam()).isNull();
		}
	}

	@Test
	void givenExistingMatchdays_whenGenerate_thenThrowsException() {
		// given
		addTeams(4);
		matchdayGeneratorService.generate(regularPhase.getId(), null, 3, false);

		// when / then
		assertThatThrownBy(() -> matchdayGeneratorService.generate(regularPhase.getId(), null, 3, false))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void givenFewerThan2Teams_whenGenerate_thenThrowsException() {
		// given
		addTeams(1);

		// when / then
		assertThatThrownBy(() -> matchdayGeneratorService.generate(regularPhase.getId(), null, 1, false))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void givenSwissSeason_whenGenerate_thenThrowsException() {
		// given — format lives only on SeasonPhase.
		regularPhase.setFormat(SeasonFormat.SWISS);
		regularPhase = seasonPhaseRepository.save(regularPhase);
		addTeams(4);

		// when / then
		assertThatThrownBy(() -> matchdayGeneratorService.generate(regularPhase.getId(), null, 3, false))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void givenParentWithSubTeams_whenGenerate_thenOnlySubTeamsUsed() {
		// given
		var suffix = UUID.randomUUID().toString().substring(0, 4);
		var parent = teamRepository.save(new Team("Parent " + suffix, "PAR" + suffix));
		var sub1 = teamRepository.save(new Team("Sub1 " + suffix, "SB1" + suffix, parent));
		var sub2 = teamRepository.save(new Team("Sub2 " + suffix, "SB2" + suffix, parent));
		var other = teamRepository.save(new Team("Other " + suffix, "OTH" + suffix));
		season.addTeam(parent);
		season.addTeam(sub1);
		season.addTeam(sub2);
		season.addTeam(other);
		seasonRepository.save(season);
		// Register only sub-teams and other in the REGULAR phase (parent is excluded from roster —
		// the canonical path uses PhaseTeam, not season.getEligibleTeams())
		phaseTeamRepository.save(new PhaseTeam(regularPhase, sub1));
		phaseTeamRepository.save(new PhaseTeam(regularPhase, sub2));
		phaseTeamRepository.save(new PhaseTeam(regularPhase, other));

		// when
		matchdayGeneratorService.generate(regularPhase.getId(), null, 2, false);

		// then — 3 eligible teams (sub1, sub2, other), parent excluded
		var allMatches = matchRepository.findByMatchdaySeasonId(season.getId());
		for (var match : allMatches) {
			assertThat(match.getHomeTeam().getId()).isNotEqualTo(parent.getId());
			if (match.getAwayTeam() != null) {
				assertThat(match.getAwayTeam().getId()).isNotEqualTo(parent.getId());
			}
		}
	}

	@Test
	void givenGenerate_thenHomeAwayDistributionBalanced() {
		// given
		addTeams(6);

		// when
		matchdayGeneratorService.generate(regularPhase.getId(), null, 5, false);

		// then — each team should have 2-3 home and 2-3 away games (balanced for 5 rounds)
		var allMatches = matchRepository.findByMatchdaySeasonId(season.getId());
		var homeCounts = new HashMap<UUID, Integer>();
		var awayCounts = new HashMap<UUID, Integer>();
		for (var match : allMatches) {
			if (match.isBye()) continue;
			homeCounts.merge(match.getHomeTeam().getId(), 1, Integer::sum);
			awayCounts.merge(match.getAwayTeam().getId(), 1, Integer::sum);
		}

		// With 6 teams and 5 rounds, each team plays 5 matches
		// Balanced means max difference of 1 between home and away
		for (var teamId : homeCounts.keySet()) {
			int home = homeCounts.getOrDefault(teamId, 0);
			int away = awayCounts.getOrDefault(teamId, 0);
			assertThat(Math.abs(home - away))
					.as("Team %s has %d home and %d away — not balanced", teamId, home, away)
					.isLessThanOrEqualTo(1);
		}
	}

	@Test
	void givenGenerate_thenEachMatchHasOneRace() {
		// given
		addTeams(4);

		// when
		matchdayGeneratorService.generate(regularPhase.getId(), null, 3, false);

		// then
		var allRaces = raceRepository.findByMatchdaySeasonId(season.getId());
		var allMatches = matchRepository.findByMatchdaySeasonId(season.getId());
		assertThat(allRaces).hasSameSizeAs(allMatches);

		// Each race is linked to a match
		for (var race : allRaces) {
			assertThat(race.getMatch()).isNotNull();
		}
	}

	@Test
	void givenSeasonWith2Legs_whenGenerate_thenEachMatchHas2Races() {
		// given
		// legs lives on the phase. Configure two legs.
		regularPhase.setLegs(2);
		regularPhase = seasonPhaseRepository.save(regularPhase);
		seasonRepository.save(season);
		addTeams(4);

		// when
		matchdayGeneratorService.generate(regularPhase.getId(), null, 3, false);

		// then
		var allRaces = raceRepository.findByMatchdaySeasonId(season.getId());
		var allMatches = matchRepository.findByMatchdaySeasonId(season.getId());
		// 4 teams → 2 matches per round → 6 matches total, each with 2 legs → 12 races
		assertThat(allMatches).hasSize(6);
		assertThat(allRaces).hasSize(12);
	}

	@Test
	void givenSeasonWith2Legs_whenGenerate_thenLeg2HasSwappedHomeAway() {
		// given
		// legs lives on the phase. Configure two legs.
		regularPhase.setLegs(2);
		regularPhase = seasonPhaseRepository.save(regularPhase);
		seasonRepository.save(season);
		addTeams(4);

		// when
		matchdayGeneratorService.generate(regularPhase.getId(), null, 3, false);

		// then — verify via races grouped by match
		var allRaces = raceRepository.findByMatchdaySeasonId(season.getId());
		var racesByMatch = allRaces.stream()
				.filter(r -> !r.isBye())
				.collect(java.util.stream.Collectors.groupingBy(r -> r.getMatch().getId()));

		for (var entry : racesByMatch.entrySet()) {
			var races = entry.getValue();
			assertThat(races).hasSize(2);

			var match = races.get(0).getMatch();
			var leg1 = races.get(0);
			var leg2 = races.get(1);

			// Leg 1: same as match home/away (no override)
			assertThat(leg1.getHomeTeam().getId()).isEqualTo(match.getHomeTeam().getId());
			assertThat(leg1.getAwayTeam().getId()).isEqualTo(match.getAwayTeam().getId());

			// Leg 2: swapped home/away
			assertThat(leg2.getHomeTeam().getId()).isEqualTo(match.getAwayTeam().getId());
			assertThat(leg2.getAwayTeam().getId()).isEqualTo(match.getHomeTeam().getId());
		}
	}

	// =========================================================================
	// Phase/group-aware tests (D-16, SVC-04) — TDD-RED: new canonical
	// generate(UUID phaseId, UUID groupId, ...) signature does not exist yet
	// =========================================================================

	@Test
	void givenLeaguePhaseAndGroupId_whenGenerate_thenThrowsIllegalArgument() {
		// given — LEAGUE layout phase + non-null groupId (invalid combination)
		var phase = buildLeaguePhase();
		addTeamsToPhase(phase, null, 4);

		// when / then
		assertThatThrownBy(() -> matchdayGeneratorService.generate(
				phase.getId(), UUID.randomUUID(), 3, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("LEAGUE");
	}

	@Test
	void givenGroupsLayoutAndNullGroupId_whenGenerate_thenThrowsIllegalArgument() {
		// given — GROUPS layout phase + null groupId (invalid combination)
		var phase = buildGroupsPhase();

		// when / then
		assertThatThrownBy(() -> matchdayGeneratorService.generate(
				phase.getId(), null, 3, false))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("GROUPS");
	}

	@Test
	void givenLeaguePhase_whenGenerate_thenMatchdaysHavePhaseIdAndGroupNull() {
		// given — LEAGUE layout, groupId=null (valid)
		var phase = buildLeaguePhase();
		addTeamsToPhase(phase, null, 4);

		// when
		matchdayGeneratorService.generate(phase.getId(), null, 3, false);

		// then — each generated matchday links to the phase and has no group
		var matchdays = matchdayRepository.findByPhaseIdOrderBySortIndexAsc(phase.getId());
		assertThat(matchdays).hasSize(3);
		for (var md : matchdays) {
			assertThat(md.getPhase()).isNotNull();
			assertThat(md.getPhase().getId()).isEqualTo(phase.getId());
			assertThat(md.getGroup()).isNull();
		}
	}

	@Test
	void givenGroupsPhaseGroupA_whenGenerate_thenMatchdaysHavePhaseIdAndGroupId() {
		// given — GROUPS layout, groupId non-null (valid)
		var phase = buildGroupsPhase();
		var groupA = phase.getGroups().get(0);
		addTeamsToPhase(phase, groupA, 4);

		// when
		matchdayGeneratorService.generate(phase.getId(), groupA.getId(), 3, false);

		// then — each generated matchday links to phase AND to groupA
		var matchdays = matchdayRepository.findByPhaseIdAndGroupIdOrderBySortIndexAsc(phase.getId(), groupA.getId());
		assertThat(matchdays).hasSize(3);
		for (var md : matchdays) {
			assertThat(md.getPhase()).isNotNull();
			assertThat(md.getPhase().getId()).isEqualTo(phase.getId());
			assertThat(md.getGroup()).isNotNull();
			assertThat(md.getGroup().getId()).isEqualTo(groupA.getId());
		}
	}

	@Test
	void givenSeasonId_whenGenerate_thenDelegatesToRegularPhase() {
		// given — use the regularPhase created in setUp() (backs the shared season);
		// add teams directly to that phase
		addTeamsToPhase(regularPhase, null, 4);

		// when — call the @Deprecated seasonId-based overload
		matchdayGeneratorService.generate(regularPhase.getId(), null, 3, false);

		// then — matchdays are linked to the REGULAR phase of that season
		var matchdays = matchdayRepository.findByPhaseIdOrderBySortIndexAsc(regularPhase.getId());
		assertThat(matchdays).hasSize(3);
		for (var md : matchdays) {
			assertThat(md.getPhase()).isNotNull();
			assertThat(md.getPhase().getId()).isEqualTo(regularPhase.getId());
		}
	}

	// =========================================================================
	// Helper factories for phase-aware tests
	// =========================================================================

	/**
	 * Creates a fresh test season (separate from the setUp season to avoid
	 * UNIQUE(season_id, phase_type) conflicts with the regularPhase created in setUp).
	 */
	private Season buildTestSeason() {
		var s = new Season("Phase58-Test-Gen-" + UUID.randomUUID().toString().substring(0, 4), 9999, 99);
		return seasonRepository.save(s);
	}

	/** Creates and persists a LEAGUE-layout REGULAR phase on a fresh test season. */
	private SeasonPhase buildLeaguePhase() {
		var s = buildTestSeason();
		var phase = new SeasonPhase(s, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		return seasonPhaseRepository.save(phase);
	}

	/** Creates and persists a GROUPS-layout REGULAR phase on a fresh test season, with two groups (A, B). */
	private SeasonPhase buildGroupsPhase() {
		var s = buildTestSeason();
		var phase = new SeasonPhase(s, PhaseType.REGULAR, PhaseLayout.GROUPS, 0);
		phase = seasonPhaseRepository.save(phase);
		var groupA = seasonPhaseGroupRepository.save(new SeasonPhaseGroup(phase, "Phase58-Test-Group-A", 0));
		var groupB = seasonPhaseGroupRepository.save(new SeasonPhaseGroup(phase, "Phase58-Test-Group-B", 1));
		phase.getGroups().add(groupA);
		phase.getGroups().add(groupB);
		return phase;
	}

	/** Creates teams and assigns them as PhaseTeam entries for the given phase/group. */
	private List<Team> addTeamsToPhase(SeasonPhase phase, SeasonPhaseGroup group, int count) {
		var teams = new ArrayList<Team>();
		var suffix = UUID.randomUUID().toString().substring(0, 4);
		for (int i = 0; i < count; i++) {
			var team = teamRepository.save(new Team("Phase58-Test-GT " + i + "_" + suffix,
					"P8" + i + suffix));
			var pt = new PhaseTeam(phase, team);
			pt.setGroup(group);
			phaseTeamRepository.save(pt);
			teams.add(team);
		}
		return teams;
	}

	// =========================================================================
	// Existing legacy helpers (updated to also populate PhaseTeam for the
	// regularPhase so the canonical generate(phaseId, null, ...) finds teams)
	// =========================================================================

	private List<Team> addTeams(int count) {
		var teams = new ArrayList<Team>();
		var suffix = UUID.randomUUID().toString().substring(0, 4);
		for (int i = 0; i < count; i++) {
			var team = teamRepository.save(new Team("GenT " + i + "_" + suffix,
					"GT" + i + suffix));
			season.addTeam(team);
			// Also register as PhaseTeam on the REGULAR phase so the canonical
			// generate(phaseId, null, ...) (called via @Deprecated bridge) finds them
			phaseTeamRepository.save(new PhaseTeam(regularPhase, team));
			teams.add(team);
		}
		seasonRepository.save(season);
		return teams;
	}

	private String pairKey(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
	}
}
