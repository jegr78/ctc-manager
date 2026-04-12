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

	private Season season;

	@BeforeEach
	void setUp() {
		var uniqueSuffix = UUID.randomUUID().toString().substring(0, 4);
		var raceScoring = raceScoringRepository.save(
				new RaceScoring("Swiss RS " + uniqueSuffix, "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
		var matchScoring = matchScoringRepository.save(
				new MatchScoring("Swiss MS " + uniqueSuffix, 3, 1, 0));

		season = new Season("Swiss Test " + uniqueSuffix, 2026, 1);
		season.setFormat(SeasonFormat.SWISS);
		season.setTotalRounds(5);
		season.setRaceScoring(raceScoring);
		season.setMatchScoring(matchScoring);
		season = seasonRepository.save(season);
	}

	@Test
	void givenSwissSeasonWith6Teams_whenGenerateNextRound_thenCreatesThreePairings() {
		// given
		addTeams(6);

		// when
		var matchday = swissPairingService.generateNextRound(season.getId());

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
		var matchday = swissPairingService.generateNextRound(season.getId());

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
		// given
		addTeams(4);
		season.setTotalRounds(1);
		seasonRepository.save(season);

		var md = swissPairingService.generateNextRound(season.getId());
		addDummyResults(md.getId());

		// when / then
		assertThrows(IllegalStateException.class,
				() -> swissPairingService.generateNextRound(season.getId()));
	}

	@Test
	void givenLeagueSeason_whenGenerateNextRound_thenThrowsException() {
		// given
		var uniqueSuffix = UUID.randomUUID().toString().substring(0, 4);
		var raceScoring = raceScoringRepository.save(
				new RaceScoring("League RS " + uniqueSuffix, "20,17,14", null, 0));
		var matchScoring = matchScoringRepository.save(
				new MatchScoring("League MS " + uniqueSuffix, 3, 1, 0));

		var leagueSeason = new Season("League Test " + uniqueSuffix, 2026, 2);
		leagueSeason.setFormat(SeasonFormat.LEAGUE);
		leagueSeason.setRaceScoring(raceScoring);
		leagueSeason.setMatchScoring(matchScoring);
		leagueSeason = seasonRepository.save(leagueSeason);

		UUID id = leagueSeason.getId();

		// when / then
		assertThrows(IllegalArgumentException.class,
				() -> swissPairingService.generateNextRound(id));
	}

	@Test
	void givenTwoCompletedRounds_whenGenerateNextRound_thenNoRematches() {
		// given
		addTeams(8);

		var md1 = swissPairingService.generateNextRound(season.getId());
		addDummyResults(md1.getId());

		var races1 = raceRepository.findByMatchdayId(md1.getId());
		assertEquals(4, races1.size());
		Set<String> firstRoundPairs = new HashSet<>();
		for (var race : races1) {
			firstRoundPairs.add(pairKey(race.getHomeTeam().getId(), race.getAwayTeam().getId()));
		}

		// when
		var md2 = swissPairingService.generateNextRound(season.getId());
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

		swissPairingService.generateNextRound(season.getId());

		// when / then
		var ex = assertThrows(IllegalStateException.class,
				() -> swissPairingService.generateNextRound(season.getId()));
		assertTrue(ex.getMessage().toLowerCase().contains("incomplete"),
				"Expected message to contain 'incomplete', got: " + ex.getMessage());
	}

	@Test
	void givenOneCompletedRound_whenCalculateBuchholz_thenReturnsOpponentScoreSums() {
		// given
		addTeams(4);

		var md = swissPairingService.generateNextRound(season.getId());
		addDummyResults(md.getId());

		// when
		var buchholz = swissPairingService.calculateBuchholz(season.getId());

		// then
		assertEquals(4, buchholz.size());

		var values = new ArrayList<>(buchholz.values());
		Collections.sort(values);
		assertEquals(List.of(0, 0, 3, 3), values);
	}

	@Test
	void givenReplacedTeam_whenGenerateNextRound_thenReplacedTeamExcluded() {
		// given
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

		// when
		var matchday = swissPairingService.generateNextRound(season.getId());

		// then — 4 active teams (3 original + 1 replacement), replaced team excluded
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

	private void addTeams(int count) {
		for (int i = 0; i < count; i++) {
			var team = teamRepository.save(new Team("SwissT " + i + "_" + UUID.randomUUID().toString().substring(0, 4),
					"SW" + i + UUID.randomUUID().toString().substring(0, 2)));
			season.addTeam(team);
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
