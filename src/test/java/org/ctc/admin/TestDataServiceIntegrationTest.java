package org.ctc.admin;

import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class TestDataServiceIntegrationTest {

	@Autowired
	private SeasonRepository seasonRepository;

	@Autowired
	private MatchdayRepository matchdayRepository;

	@Autowired
	private RaceResultRepository raceResultRepository;

	@Autowired
	private MatchRepository matchRepository;

	@Autowired
	private RaceRepository raceRepository;

	// --- Helper methods ---

	private Season findSeason(int year, String name) {
		return seasonRepository.findAll().stream()
				.filter(s -> s.getYear() == year && s.getName().equals(name))
				.findFirst().orElseThrow(() -> new AssertionError(
						"Season not found: year=" + year + " name=" + name));
	}

	private Season findSeason(int year, int number) {
		return seasonRepository.findAll().stream()
				.filter(s -> s.getYear() == year && s.getNumber() == number)
				.findFirst().orElseThrow(() -> new AssertionError(
						"Season not found: year=" + year + " number=" + number));
	}

	// --- Task 1 Phase 23: Season structure tests ---

	@Test
	void givenDevSeed_whenStarted_thenS1GroupAHasFormatRoundRobin() {
		// given
		var season = findSeason(2023, "Group A");

		// then
		assertThat(season.getFormat()).isEqualTo(SeasonFormat.ROUND_ROBIN);
	}

	@Test
	void givenDevSeed_whenStarted_thenS1GroupBHasFormatRoundRobin() {
		// given
		var season = findSeason(2023, "Group B");

		// then
		assertThat(season.getFormat()).isEqualTo(SeasonFormat.ROUND_ROBIN);
	}

	@Test
	void givenDevSeed_whenStarted_thenS2HasFormatSwiss() {
		// given
		var season = findSeason(2024, "Regular Season");

		// then
		assertThat(season.getFormat()).isEqualTo(SeasonFormat.SWISS);
	}

	@Test
	void givenDevSeed_whenStarted_thenS4HasFormatLeague() {
		// given
		var season = findSeason(2026, 4);

		// then
		assertThat(season.getFormat()).isEqualTo(SeasonFormat.LEAGUE);
	}

	@Test
	void givenDevSeed_whenStarted_thenS1GroupAHasSixTeams() {
		// given
		var season = findSeason(2023, "Group A");

		// then
		assertThat(season.getSeasonTeams()).hasSize(6);
	}

	@Test
	void givenDevSeed_whenStarted_thenS1GroupBHasSixTeams() {
		// given
		var season = findSeason(2023, "Group B");

		// then
		assertThat(season.getSeasonTeams()).hasSize(6);
	}

	@Test
	void givenDevSeed_whenStarted_thenS4HasFourteenMatchTeams() {
		// given
		var season = findSeason(2026, 4);

		// when
		var teams = season.getSeasonTeams();

		// then
		assertThat(teams).hasSize(14);
		// VRX, SGM, TBR parents should NOT be present (only their sub-teams)
		var teamShortNames = teams.stream()
				.map(st -> st.getTeam().getShortName())
				.toList();
		assertThat(teamShortNames).doesNotContain("VRX", "SGM", "TBR");
	}

	@Test
	void givenDevSeed_whenStarted_thenS1GroupsContainSubTeams() {
		// given
		var groupA = findSeason(2023, "Group A");
		var groupB = findSeason(2023, "Group B");

		// then - at least one sub-team in each group
		var groupATeams = groupA.getSeasonTeams().stream()
				.map(st -> st.getTeam())
				.toList();
		assertThat(groupATeams).anyMatch(t -> t.getParentTeam() != null);

		var groupBTeams = groupB.getSeasonTeams().stream()
				.map(st -> st.getTeam())
				.toList();
		assertThat(groupBTeams).anyMatch(t -> t.getParentTeam() != null);
	}

	// --- Phase 23 Plan 02: Matchday and result tests ---

	@Test
	void givenDevSeed_whenStarted_thenLeagueSeasonHasFiveMatchdays() {
		// given
		var season = findSeason(2026, 4);

		// when — filter for seed-created matchdays (label pattern "Matchday N")
		var matchdays = matchdayRepository.findAll().stream()
				.filter(md -> md.getSeason().getId().equals(season.getId()))
				.filter(md -> md.getLabel().matches("Matchday \\d+"))
				.count();

		// then
		assertThat(matchdays).isEqualTo(5);
	}

	@Test
	void givenDevSeed_whenStarted_thenSwissSeasonHasFiveMatchdays() {
		// given
		var season = findSeason(2024, "Regular Season");

		// when
		var matchdays = matchdayRepository.findAll().stream()
				.filter(md -> md.getSeason().getId().equals(season.getId()))
				.filter(md -> md.getLabel().matches("Matchday \\d+"))
				.count();

		// then
		assertThat(matchdays).isEqualTo(5);
	}

	@Test
	void givenDevSeed_whenStarted_thenRoundRobinGroupAHasThreeMatchdays() {
		// given
		var season = findSeason(2023, "Group A");

		// when
		var matchdays = matchdayRepository.findAll().stream()
				.filter(md -> md.getSeason().getId().equals(season.getId()))
				.filter(md -> md.getLabel().matches("Matchday \\d+"))
				.count();

		// then
		assertThat(matchdays).isEqualTo(3);
	}

	@Test
	void givenDevSeed_whenStarted_thenRoundRobinGroupBHasThreeMatchdays() {
		// given
		var season = findSeason(2023, "Group B");

		// when
		var matchdays = matchdayRepository.findAll().stream()
				.filter(md -> md.getSeason().getId().equals(season.getId()))
				.filter(md -> md.getLabel().matches("Matchday \\d+"))
				.count();

		// then
		assertThat(matchdays).isEqualTo(3);
	}

	@Test
	void givenDevSeed_whenStarted_thenLeagueRacesHaveResults() {
		// given
		var season = findSeason(2026, 4);
		var seedMatchdayIds = matchdayRepository.findAll().stream()
				.filter(md -> md.getSeason().getId().equals(season.getId()))
				.filter(md -> md.getLabel().matches("Matchday \\d+"))
				.map(md -> md.getId())
				.collect(Collectors.toSet());
		var seasonRaces = raceRepository.findAll().stream()
				.filter(r -> seedMatchdayIds.contains(r.getMatchday().getId()))
				.toList();

		// then
		assertThat(seasonRaces).isNotEmpty();
		for (var race : seasonRaces) {
			var resultCount = raceResultRepository.findAll().stream()
					.filter(r -> r.getRace().getId().equals(race.getId()))
					.count();
			assertThat(resultCount).as("Race %s should have 12 results", race.getId()).isEqualTo(12);
		}
	}

	@Test
	void givenDevSeed_whenStarted_thenAllRaceResultsHaveNonZeroPoints() {
		// given
		var devSeasonIds = seasonRepository.findAll().stream()
				.filter(s -> s.getNumber() < 90) // exclude test seasons
				.map(s -> s.getId())
				.collect(Collectors.toSet());

		// when
		var devRaceResults = raceResultRepository.findAll().stream()
				.filter(r -> devSeasonIds.contains(r.getRace().getMatchday().getSeason().getId()))
				.toList();

		// then
		assertThat(devRaceResults).isNotEmpty();
		assertThat(devRaceResults).allMatch(r -> r.getPointsTotal() > 0);
	}

	@Test
	void givenDevSeed_whenStarted_thenAllMatchesHaveNonNullScores() {
		// given — filter for seed-created matchdays only (label "Matchday N")
		var devSeasonIds = seasonRepository.findAll().stream()
				.filter(s -> s.getNumber() < 90) // exclude test seasons
				.map(s -> s.getId())
				.collect(Collectors.toSet());
		var seedMatchdayIds = matchdayRepository.findAll().stream()
				.filter(md -> devSeasonIds.contains(md.getSeason().getId()))
				.filter(md -> md.getLabel().matches("Matchday \\d+"))
				.map(md -> md.getId())
				.collect(Collectors.toSet());

		// when
		var devMatches = matchRepository.findAll().stream()
				.filter(m -> seedMatchdayIds.contains(m.getMatchday().getId()))
				.toList();

		// then
		assertThat(devMatches).isNotEmpty();
		assertThat(devMatches).allMatch(m -> m.getHomeScore() != null && m.getAwayScore() != null);
	}

	// --- Phase 26: Demo logo classpath resource tests ---

	@Test
	void givenFictiveTeamShortNames_whenLoadingClasspathResource_thenAllTenLogosExist() {
		// given
		var fictiveNames = List.of("VRX", "SGM", "ADR", "TBR", "ICL", "SVT", "NFR", "EGP", "HMS", "PWR");

		// when / then
		for (String shortName : fictiveNames) {
			var resource = new ClassPathResource("demo/team-logos/" + shortName + ".png");
			assertThat(resource.exists())
					.as("Logo for fictive team %s must exist on classpath", shortName)
					.isTrue();
		}
	}

	@Test
	void givenRealCtcTeamShortNames_whenLoadingClasspathResource_thenNoLogosExist() {
		// given
		var realNames = List.of("AHR", "ART", "CLR", "DTR", "GXR", "MRL", "P1R", "TCR", "TNR", "VEZ");

		// when / then
		for (String shortName : realNames) {
			var resource = new ClassPathResource("demo/team-logos/" + shortName + ".png");
			assertThat(resource.exists())
					.as("Logo for real CTC team %s must NOT exist on classpath", shortName)
					.isFalse();
		}
	}
}
