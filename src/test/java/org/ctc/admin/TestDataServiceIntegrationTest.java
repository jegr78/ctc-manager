package org.ctc.admin;

import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.repository.SeasonRepository;
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

	@Autowired
	private PhaseTeamRepository phaseTeamRepository;

	@Autowired
	private SeasonPhaseRepository seasonPhaseRepository;


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


	@Test
	void givenDevSeed_whenStarted_thenS2HasFormatSwiss() {
		// given
		var season = findSeason(2024, "Regular Season");
		var regular = seasonPhaseRepository
				.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();

		// then — format moved to REGULAR phase.
		assertThat(regular.getFormat()).isEqualTo(SeasonFormat.SWISS);
	}

	@Test
	void givenDevSeed_whenStarted_thenS4HasFormatLeague() {
		// given
		var season = findSeason(2026, 4);
		var regular = seasonPhaseRepository
				.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR).orElseThrow();

		// then — format moved to REGULAR phase.
		assertThat(regular.getFormat()).isEqualTo(SeasonFormat.LEAGUE);
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
	void givenDevSeed_whenStarted_thenConsolidated2023ContainsSubTeams() {
		// given
		var season = findSeason(2023, 1);
		// then — at least one sub-team present in the consolidated roster
		var teams = season.getSeasonTeams().stream()
				.map(st -> st.getTeam()).toList();
		assertThat(teams).anyMatch(t -> t.getParentTeam() != null);
	}


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
	void givenDevSeed_whenStarted_thenConsolidated2023HasSixMatchdays() {
		// given
		var season = findSeason(2023, 1);
		// when — 3 matchdays per group × 2 groups (Group A + Group B)
		var matchdays = matchdayRepository.findAll().stream()
				.filter(md -> md.getSeason().getId().equals(season.getId()))
				.filter(md -> md.getLabel().contains("Matchday"))
				.count();
		// then
		assertThat(matchdays).isEqualTo(6);
	}

	@Test
	void givenDevSeed_whenStarted_thenConsolidated2023MatchdaysSplitEvenlyByGroup() {
		// given
		var season = findSeason(2023, 1);
		// when
		var matchdaysByGroup = matchdayRepository.findAll().stream()
				.filter(md -> md.getSeason().getId().equals(season.getId()))
				.filter(md -> md.getLabel().contains("Matchday"))
				.filter(md -> md.getGroup() != null)
				.collect(java.util.stream.Collectors.groupingBy(
						md -> md.getGroup().getName(), java.util.stream.Collectors.counting()));
		// then
		assertThat(matchdaysByGroup).containsEntry("Group A", 3L)
				.containsEntry("Group B", 3L);
	}

	@Test
	void givenDevSeed_whenStarted_thenConsolidated2023MatchdaySortIndicesDoNotCollide() {
		// given — D-13 + W-1: Group A uses sortIndex 1..3, Group B uses 4..6 (no collision)
		var season = findSeason(2023, 1);
		// when
		var sortIndices = matchdayRepository.findAll().stream()
				.filter(md -> md.getSeason().getId().equals(season.getId()))
				.filter(md -> md.getLabel().contains("Matchday"))
				.map(md -> md.getSortIndex())
				.sorted()
				.toList();
		// then — exactly 6 distinct values [1, 2, 3, 4, 5, 6]
		assertThat(sortIndices).containsExactly(1, 2, 3, 4, 5, 6);
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
		// given — filter for seed-created matchdays only (label "Matchday N" or group-prefixed)
		var devSeasonIds = seasonRepository.findAll().stream()
				.filter(s -> s.getNumber() < 90) // exclude test seasons
				.map(s -> s.getId())
				.collect(Collectors.toSet());
		var seedMatchdayIds = matchdayRepository.findAll().stream()
				.filter(md -> devSeasonIds.contains(md.getSeason().getId()))
				.filter(md -> md.getLabel().contains("Matchday"))
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


	@Test
	void givenDevSeed_whenStarted_thenConsolidated2023HasOneRegularGroupsPhase() {
		// given
		var season = findSeason(2023, 1);
		// when
		var regularPhases = season.getPhases().stream()
				.filter(p -> p.getPhaseType() == PhaseType.REGULAR).toList();
		// then
		assertThat(regularPhases).hasSize(1);
		assertThat(regularPhases.get(0).getLayout()).isEqualTo(PhaseLayout.GROUPS);
	}

	@Test
	void givenDevSeed_whenStarted_thenConsolidated2023HasTwoNamedGroupsInOrder() {
		// given
		var season = findSeason(2023, 1);
		var regular = season.getPhases().stream()
				.filter(p -> p.getPhaseType() == PhaseType.REGULAR)
				.findFirst().orElseThrow();
		// when
		var groupNames = regular.getGroups().stream()
				.sorted(java.util.Comparator.comparingInt(SeasonPhaseGroup::getSortIndex))
				.map(SeasonPhaseGroup::getName).toList();
		// then
		assertThat(groupNames).containsExactly("Group A", "Group B");
	}

	@Test
	void givenDevSeed_whenStarted_thenConsolidated2023HasTwelvePhaseTeamsSplitSixSix() {
		// given
		var season = findSeason(2023, 1);
		var regular = season.getPhases().stream()
				.filter(p -> p.getPhaseType() == PhaseType.REGULAR)
				.findFirst().orElseThrow();
		// when
		var phaseTeams = phaseTeamRepository.findByPhaseId(regular.getId());
		var groupCounts = phaseTeams.stream()
				.filter(pt -> pt.getGroup() != null)
				.collect(java.util.stream.Collectors.groupingBy(
						pt -> pt.getGroup().getName(), java.util.stream.Collectors.counting()));
		// then
		assertThat(phaseTeams).hasSize(12);
		assertThat(groupCounts).containsEntry("Group A", 6L).containsEntry("Group B", 6L);
	}

	@Test
	void givenDevSeed_whenStarted_thenLeagueSeasonsHavePhaseTeamsWithNullGroup() {
		// given — 2026 (S4) has LEAGUE layout
		var season = findSeason(2026, 4);
		var regular = season.getPhases().stream()
				.filter(p -> p.getPhaseType() == PhaseType.REGULAR)
				.findFirst().orElseThrow();
		// when
		var phaseTeams = phaseTeamRepository.findByPhaseId(regular.getId());
		// then
		assertThat(regular.getLayout()).isEqualTo(PhaseLayout.LEAGUE);
		assertThat(phaseTeams).isNotEmpty();
		assertThat(phaseTeams).allMatch(pt -> pt.getGroup() == null);
		assertThat(phaseTeams.size()).isEqualTo(season.getSeasonTeams().size());
	}


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
