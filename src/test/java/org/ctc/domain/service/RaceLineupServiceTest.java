package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceLineupServiceTest {

	@Mock
	private RaceRepository raceRepository;
	@Mock
	private RaceLineupRepository raceLineupRepository;
	@Mock
	private SeasonDriverRepository seasonDriverRepository;
	@Mock
	private TeamRepository teamRepository;
	@Mock
	private DriverRepository driverRepository;

	@InjectMocks
	private RaceLineupService service;

	// --- saveLineup ---

	@Test
	void givenDriverTeamMapping_whenSaveLineup_thenCreatesEntries() {
		// given
		var raceId = UUID.randomUUID();
		var driverId = UUID.randomUUID();
		var teamId = UUID.randomUUID();

		var race = new Race();
		race.setId(raceId);
		var driver = new Driver();
		driver.setId(driverId);
		var team = new Team();
		team.setId(teamId);

		when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
		when(raceLineupRepository.findByRaceId(raceId)).thenReturn(List.of());
		when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
		when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
		when(raceLineupRepository.save(any(RaceLineup.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		int count = service.saveLineup(raceId, Map.of(driverId, teamId));

		// then
		assertThat(count).isEqualTo(1);
		verify(raceLineupRepository).save(any(RaceLineup.class));
	}

	@Test
	void givenExistingLineup_whenSaveLineup_thenClearsBeforeSaving() {
		// given
		var raceId = UUID.randomUUID();
		var driverId = UUID.randomUUID();
		var teamId = UUID.randomUUID();

		var race = new Race();
		race.setId(raceId);
		var driver = new Driver();
		driver.setId(driverId);
		var team = new Team();
		team.setId(teamId);

		var existingLineup = new RaceLineup(race, driver, team);
		when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
		when(raceLineupRepository.findByRaceId(raceId)).thenReturn(List.of(existingLineup));
		when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
		when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
		when(raceLineupRepository.save(any(RaceLineup.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		int count = service.saveLineup(raceId, Map.of(driverId, teamId));

		// then
		assertThat(count).isEqualTo(1);
		verify(raceLineupRepository).deleteAll(List.of(existingLineup));
		verify(raceLineupRepository).save(any(RaceLineup.class));
	}

	// --- getLineupData ---

	@Test
	void givenRaceWithRegularTeams_whenGetLineupData_thenReturnsCorrectEntries() {
		// given
		var homeTeam = new Team("Home Team", "HOM");
		homeTeam.setId(UUID.randomUUID());
		var awayTeam = new Team("Away Team", "AWY");
		awayTeam.setId(UUID.randomUUID());

		var season = new Season("Test Season");
		season.setId(UUID.randomUUID());
		season.addTeam(homeTeam);
		season.addTeam(awayTeam);

		var matchday = new Matchday(season, "MD 1", 1);
		matchday.setId(UUID.randomUUID());

		var match = new Match(matchday, homeTeam, awayTeam);
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(matchday);
		race.setMatch(match);

		when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
		when(seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), homeTeam.getId())).thenReturn(List.of());
		when(seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), awayTeam.getId())).thenReturn(List.of());

		// when
		var data = service.getLineupData(race.getId());

		// then
		assertThat(data.race()).isEqualTo(race);
		assertThat(data.homeEntry()).isNotNull();
		assertThat(data.homeEntry().team()).isEqualTo(homeTeam);
		assertThat(data.homeEntry().hasSubTeams()).isFalse();
		assertThat(data.awayEntry()).isNotNull();
		assertThat(data.awayEntry().team()).isEqualTo(awayTeam);
		assertThat(data.awayEntry().hasSubTeams()).isFalse();
	}

	@Test
	void givenRaceWithSubTeams_whenGetLineupData_thenReturnsParentWithSubTeams() {
		// given
		var parentHome = new Team("Parent Home", "PAR");
		parentHome.setId(UUID.randomUUID());
		var sub1 = new Team("Sub Home 1", "PAR_1");
		sub1.setId(UUID.randomUUID());
		sub1.setParentTeam(parentHome);
		var sub2 = new Team("Sub Home 2", "PAR_2");
		sub2.setId(UUID.randomUUID());
		sub2.setParentTeam(parentHome);
		parentHome.setSubTeams(List.of(sub1, sub2));

		var awayTeam = new Team("Away Team", "AWY");
		awayTeam.setId(UUID.randomUUID());

		var season = new Season("Test Season");
		season.setId(UUID.randomUUID());
		season.addTeam(parentHome);
		season.addTeam(sub1);
		season.addTeam(sub2);
		season.addTeam(awayTeam);

		var matchday = new Matchday(season, "MD 1", 1);
		matchday.setId(UUID.randomUUID());

		var match = new Match(matchday, sub1, awayTeam);
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setMatchday(matchday);
		race.setMatch(match);

		when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
		when(seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), sub1.getId())).thenReturn(List.of());
		when(seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), sub2.getId())).thenReturn(List.of());
		when(seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), awayTeam.getId())).thenReturn(List.of());

		// when
		var data = service.getLineupData(race.getId());

		// then
		assertThat(data.homeEntry()).isNotNull();
		assertThat(data.homeEntry().team()).isEqualTo(parentHome);
		assertThat(data.homeEntry().hasSubTeams()).isTrue();
		assertThat(data.homeEntry().subTeams()).containsExactly(sub1, sub2);
		assertThat(data.awayEntry()).isNotNull();
		assertThat(data.awayEntry().team()).isEqualTo(awayTeam);
		assertThat(data.awayEntry().hasSubTeams()).isFalse();
	}

	// --- getDriverAssignments ---

	@Test
	void givenLineupWithTwoDrivers_whenGetDriverAssignments_thenReturnsDriverTeamMap() {
		// given
		var raceId = UUID.randomUUID();
		var driver1 = new Driver();
		driver1.setId(UUID.randomUUID());
		var driver2 = new Driver();
		driver2.setId(UUID.randomUUID());
		var team1 = new Team();
		team1.setId(UUID.randomUUID());
		var team2 = new Team();
		team2.setId(UUID.randomUUID());

		var race = new Race();
		race.setId(raceId);
		var lu1 = new RaceLineup(race, driver1, team1);
		var lu2 = new RaceLineup(race, driver2, team2);

		when(raceLineupRepository.findByRaceId(raceId)).thenReturn(List.of(lu1, lu2));

		// when
		var assignments = service.getDriverAssignments(raceId);

		// then
		assertThat(assignments).hasSize(2);
		assertThat(assignments.get(driver1.getId())).isEqualTo(team1.getId());
		assertThat(assignments.get(driver2.getId())).isEqualTo(team2.getId());
	}

	@Test
	void givenEmptyLineup_whenGetDriverAssignments_thenReturnsEmptyMap() {
		// given
		var raceId = UUID.randomUUID();
		when(raceLineupRepository.findByRaceId(raceId)).thenReturn(List.of());

		// when
		var assignments = service.getDriverAssignments(raceId);

		// then
		assertThat(assignments).isEmpty();
	}
}
