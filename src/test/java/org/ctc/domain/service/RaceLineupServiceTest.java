package org.ctc.domain.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.ctc.domain.exception.BusinessRuleException;
import org.mockito.ArgumentCaptor;

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
	@Mock
	private RaceResultRepository raceResultRepository;
	@Mock
	private ScoringService scoringService;

	@InjectMocks
	private RaceLineupService service;


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

		var matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "MD 1", 1);
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

		var matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "MD 1", 1);
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

	@Test
	void givenGuestAssignment_whenSaveLineup_thenGuestEntryPersistedWithGuestFlag() {
		// given
		var raceId = UUID.randomUUID();
		var guestDriverId = UUID.randomUUID();
		var teamId = UUID.randomUUID();

		var driver = new Driver();
		driver.setId(guestDriverId);
		var team = new Team();
		team.setId(teamId);
		var awayTeam = new Team();
		awayTeam.setId(UUID.randomUUID());
		var race = raceWithTeams(raceId, team, awayTeam);

		when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
		when(raceLineupRepository.findByRaceId(raceId)).thenReturn(List.of());
		when(driverRepository.findById(guestDriverId)).thenReturn(Optional.of(driver));
		when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
		when(raceLineupRepository.save(any(RaceLineup.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		int count = service.saveLineup(raceId, Map.of(), Map.of(guestDriverId, teamId));

		// then
		assertThat(count).isEqualTo(1);
		ArgumentCaptor<RaceLineup> captor = ArgumentCaptor.forClass(RaceLineup.class);
		verify(raceLineupRepository).save(captor.capture());
		assertThat(captor.getValue().isGuest()).isTrue();
		verifyNoInteractions(scoringService);
	}

	@Test
	void givenRosterAndGuestMaps_whenSaveLineup_thenRosterEntryHasGuestFalse() {
		// given
		var raceId = UUID.randomUUID();
		var rosterDriverId = UUID.randomUUID();
		var teamId = UUID.randomUUID();

		var race = new Race();
		race.setId(raceId);
		var driver = new Driver();
		driver.setId(rosterDriverId);
		var team = new Team();
		team.setId(teamId);

		when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
		when(raceLineupRepository.findByRaceId(raceId)).thenReturn(List.of());
		when(driverRepository.findById(rosterDriverId)).thenReturn(Optional.of(driver));
		when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
		when(raceLineupRepository.save(any(RaceLineup.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		service.saveLineup(raceId, Map.of(rosterDriverId, teamId), Map.of());

		// then
		ArgumentCaptor<RaceLineup> captor = ArgumentCaptor.forClass(RaceLineup.class);
		verify(raceLineupRepository).save(captor.capture());
		assertThat(captor.getValue().isGuest()).isFalse();
		verifyNoInteractions(scoringService);
	}

	@Test
	void givenRemovedGuest_whenSaveLineup_thenResultDeletedAndScoresReaggregated() {
		// given
		var raceId = UUID.randomUUID();
		var droppedGuestId = UUID.randomUUID();

		var race = new Race();
		race.setId(raceId);
		var droppedDriver = new Driver();
		droppedDriver.setId(droppedGuestId);
		var team = new Team();
		team.setId(UUID.randomUUID());
		var existingGuest = new RaceLineup(race, droppedDriver, team, true);
		var orphanResult = new RaceResult();

		when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
		when(raceLineupRepository.findByRaceId(raceId)).thenReturn(List.of(existingGuest));
		when(raceResultRepository.findByRaceIdAndDriverId(raceId, droppedGuestId))
				.thenReturn(Optional.of(orphanResult));

		// when
		service.saveLineup(raceId, Map.of(), Map.of());

		// then
		verify(raceResultRepository).delete(orphanResult);
		verify(scoringService).aggregateMatchScores(race);
	}

	@Test
	void givenGuestStillPresent_whenSaveLineup_thenNoResultDeleteAndNoReaggregation() {
		// given
		var raceId = UUID.randomUUID();
		var guestId = UUID.randomUUID();
		var teamId = UUID.randomUUID();

		var driver = new Driver();
		driver.setId(guestId);
		var team = new Team();
		team.setId(teamId);
		var awayTeam = new Team();
		awayTeam.setId(UUID.randomUUID());
		var race = raceWithTeams(raceId, team, awayTeam);
		var existingGuest = new RaceLineup(race, driver, team, true);

		when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
		when(raceLineupRepository.findByRaceId(raceId)).thenReturn(List.of(existingGuest));
		when(driverRepository.findById(guestId)).thenReturn(Optional.of(driver));
		when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
		when(raceLineupRepository.save(any(RaceLineup.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		service.saveLineup(raceId, Map.of(), Map.of(guestId, teamId));

		// then
		verifyNoInteractions(scoringService);
	}

	@Test
	void givenKeptGuestMovedToDifferentTeam_whenSaveLineup_thenScoresReaggregated() {
		// given — an existing guest whose team_id changes on re-save
		var raceId = UUID.randomUUID();
		var guestId = UUID.randomUUID();
		var oldTeamId = UUID.randomUUID();
		var newTeamId = UUID.randomUUID();

		var driver = new Driver();
		driver.setId(guestId);
		var oldTeam = new Team();
		oldTeam.setId(oldTeamId);
		var newTeam = new Team();
		newTeam.setId(newTeamId);
		var race = raceWithTeams(raceId, oldTeam, newTeam);
		var existingGuest = new RaceLineup(race, driver, oldTeam, true);

		when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
		when(raceLineupRepository.findByRaceId(raceId)).thenReturn(List.of(existingGuest));
		when(driverRepository.findById(guestId)).thenReturn(Optional.of(driver));
		when(teamRepository.findById(newTeamId)).thenReturn(Optional.of(newTeam));
		when(raceLineupRepository.save(any(RaceLineup.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		service.saveLineup(raceId, Map.of(), Map.of(guestId, newTeamId));

		// then
		verify(scoringService).aggregateMatchScores(race);
	}

	@Test
	void givenGuestTeamNotInRace_whenSaveLineup_thenThrowsBusinessRule() {
		// given — a guest assigned to a team that is neither home nor away
		var raceId = UUID.randomUUID();
		var guestId = UUID.randomUUID();
		var homeTeam = new Team();
		homeTeam.setId(UUID.randomUUID());
		var awayTeam = new Team();
		awayTeam.setId(UUID.randomUUID());
		var foreignTeamId = UUID.randomUUID();
		var race = raceWithTeams(raceId, homeTeam, awayTeam);

		when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));

		// when / then
		assertThatThrownBy(() -> service.saveLineup(raceId, Map.of(), Map.of(guestId, foreignTeamId)))
				.isInstanceOf(BusinessRuleException.class);
		verify(raceLineupRepository, org.mockito.Mockito.never()).deleteAll(any());
	}

	private Race raceWithTeams(UUID raceId, Team homeTeam, Team awayTeam) {
		var season = new Season("Test_GuestSvc");
		season.setId(UUID.randomUUID());
		var matchday = PhaseTestFixtures.matchdayInRegularPhase(season, "MD", 1);
		var match = new Match(matchday, homeTeam, awayTeam);
		var race = new Race();
		race.setId(raceId);
		race.setMatchday(matchday);
		race.setMatch(match);
		return race;
	}

	@Test
	void givenDriverInBothRosterAndGuestMaps_whenSaveLineup_thenThrowsBusinessRule() {
		// given
		var raceId = UUID.randomUUID();
		var driverId = UUID.randomUUID();
		var teamId = UUID.randomUUID();

		var race = new Race();
		race.setId(raceId);
		when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));

		// when / then
		assertThatThrownBy(() ->
				service.saveLineup(raceId, Map.of(driverId, teamId), Map.of(driverId, teamId)))
				.isInstanceOf(BusinessRuleException.class);
		verify(raceLineupRepository, org.mockito.Mockito.never()).deleteAll(any());
	}

	@Test
	void givenMixedLineup_whenGetDriverAssignments_thenExcludesGuests() {
		// given
		var raceId = UUID.randomUUID();
		var rosterDriver = new Driver();
		rosterDriver.setId(UUID.randomUUID());
		var guestDriver = new Driver();
		guestDriver.setId(UUID.randomUUID());
		var team = new Team();
		team.setId(UUID.randomUUID());

		var race = new Race();
		race.setId(raceId);
		var rosterLineup = new RaceLineup(race, rosterDriver, team);
		var guestLineup = new RaceLineup(race, guestDriver, team, true);

		when(raceLineupRepository.findByRaceId(raceId)).thenReturn(List.of(rosterLineup, guestLineup));

		// when
		var assignments = service.getDriverAssignments(raceId);

		// then
		assertThat(assignments).hasSize(1);
		assertThat(assignments).containsKey(rosterDriver.getId());
		assertThat(assignments).doesNotContainKey(guestDriver.getId());
	}

	@Test
	void givenMixedLineup_whenGetGuestLineups_thenReturnsOnlyGuests() {
		// given
		var raceId = UUID.randomUUID();
		var rosterDriver = new Driver();
		rosterDriver.setId(UUID.randomUUID());
		var guestDriver = new Driver();
		guestDriver.setId(UUID.randomUUID());
		var team = new Team();
		team.setId(UUID.randomUUID());

		var race = new Race();
		race.setId(raceId);
		var rosterLineup = new RaceLineup(race, rosterDriver, team);
		var guestLineup = new RaceLineup(race, guestDriver, team, true);

		when(raceLineupRepository.findByRaceId(raceId)).thenReturn(List.of(rosterLineup, guestLineup));

		// when
		var guests = service.getGuestLineups(raceId);

		// then
		assertThat(guests).containsExactly(guestLineup);
	}
}
