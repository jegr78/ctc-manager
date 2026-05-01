package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

	@Mock
	private MatchRepository matchRepository;
	@Mock
	private MatchdayRepository matchdayRepository;
	@Mock
	private TeamRepository teamRepository;
	@Mock
	private RaceRepository raceRepository;

	@InjectMocks
	private MatchService service;

	// --- createMatch ---

	@Test
	void givenValidTeamsAndMatchday_whenCreateMatch_thenMatchAndFirstRaceCreated() {
		// given
		var matchdayId = UUID.randomUUID();
		var homeTeamId = UUID.randomUUID();
		var awayTeamId = UUID.randomUUID();

		var season = new Season("Test Season");
		var matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		matchday.setId(matchdayId);
		// legs lives on the phase; explicit single-leg setup for this test.
		matchday.getPhase().setLegs(1);

		var homeTeam = new Team();
		homeTeam.setId(homeTeamId);
		homeTeam.setShortName("HOM");

		var awayTeam = new Team();
		awayTeam.setId(awayTeamId);
		awayTeam.setShortName("AWY");

		when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
		when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
		when(teamRepository.findById(awayTeamId)).thenReturn(Optional.of(awayTeam));
		when(matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(matchdayId, homeTeamId, awayTeamId))
				.thenReturn(false);
		when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
			Match m = inv.getArgument(0);
			m.setId(UUID.randomUUID());
			return m;
		});
		when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		var result = service.createMatch(matchdayId, homeTeamId, awayTeamId, false);

		// then
		assertThat(result.getHomeTeam()).isEqualTo(homeTeam);
		assertThat(result.getAwayTeam()).isEqualTo(awayTeam);
		assertThat(result.getMatchday()).isEqualTo(matchday);
		assertThat(result.isBye()).isFalse();
		verify(matchRepository).save(any(Match.class));
		verify(raceRepository).save(any(Race.class));
	}

	@Test
	void givenByeFlag_whenCreateMatch_thenAwayTeamIsNull() {
		// given
		var matchdayId = UUID.randomUUID();
		var homeTeamId = UUID.randomUUID();

		var season = new Season("Test Season");
		var matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		matchday.setId(matchdayId);

		var homeTeam = new Team();
		homeTeam.setId(homeTeamId);
		homeTeam.setShortName("HOM");

		when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
		when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
		when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
			Match m = inv.getArgument(0);
			m.setId(UUID.randomUUID());
			return m;
		});
		when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		var result = service.createMatch(matchdayId, homeTeamId, null, true);

		// then
		assertThat(result.getHomeTeam()).isEqualTo(homeTeam);
		assertThat(result.getAwayTeam()).isNull();
		assertThat(result.isBye()).isTrue();
		verify(matchRepository).save(any(Match.class));
		verify(raceRepository).save(any(Race.class));
	}

	@Test
	void givenSeasonWithTwoLegs_whenCreateMatch_thenTwoRacesCreatedWithSwappedSecondLeg() {
		// given
		var matchdayId = UUID.randomUUID();
		var homeTeamId = UUID.randomUUID();
		var awayTeamId = UUID.randomUUID();

		var season = new Season("Test Season");
		var matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		matchday.setId(matchdayId);
		// legs lives on the phase. Configure two legs for this scenario.
		matchday.getPhase().setLegs(2);

		var homeTeam = new Team();
		homeTeam.setId(homeTeamId);
		homeTeam.setShortName("HOM");

		var awayTeam = new Team();
		awayTeam.setId(awayTeamId);
		awayTeam.setShortName("AWY");

		when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
		when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
		when(teamRepository.findById(awayTeamId)).thenReturn(Optional.of(awayTeam));
		when(matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(matchdayId, homeTeamId, awayTeamId))
				.thenReturn(false);
		when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
			Match m = inv.getArgument(0);
			m.setId(UUID.randomUUID());
			return m;
		});
		when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		var result = service.createMatch(matchdayId, homeTeamId, awayTeamId, false);

		// then
		var raceCaptor = ArgumentCaptor.forClass(Race.class);
		verify(raceRepository, times(2)).save(raceCaptor.capture());
		List<Race> saved = raceCaptor.getAllValues();

		// Match itself keeps the originally supplied home/away — never swapped regardless of legs
		assertThat(result.getHomeTeam()).isEqualTo(homeTeam);
		assertThat(result.getAwayTeam()).isEqualTo(awayTeam);

		// Leg 1: no override, resolves to match home/away
		assertThat(saved.get(0).getHomeTeam()).isEqualTo(homeTeam);
		assertThat(saved.get(0).getAwayTeam()).isEqualTo(awayTeam);

		// Leg 2: swapped via overrides
		assertThat(saved.get(1).getHomeTeam()).isEqualTo(awayTeam);
		assertThat(saved.get(1).getAwayTeam()).isEqualTo(homeTeam);
	}

	@Test
	void givenSeasonWithTwoLegsAndBye_whenCreateMatch_thenTwoRacesWithoutSwap() {
		// given
		var matchdayId = UUID.randomUUID();
		var homeTeamId = UUID.randomUUID();

		var season = new Season("Test Season");
		var matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		matchday.setId(matchdayId);
		// legs lives on the phase. Configure two legs for this scenario.
		matchday.getPhase().setLegs(2);

		var homeTeam = new Team();
		homeTeam.setId(homeTeamId);
		homeTeam.setShortName("HOM");

		when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
		when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
		when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
			Match m = inv.getArgument(0);
			m.setId(UUID.randomUUID());
			return m;
		});
		when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		service.createMatch(matchdayId, homeTeamId, null, true);

		// then — bye: both legs have home team, no swap
		var raceCaptor = ArgumentCaptor.forClass(Race.class);
		verify(raceRepository, times(2)).save(raceCaptor.capture());
		List<Race> saved = raceCaptor.getAllValues();

		assertThat(saved.get(0).getHomeTeam()).isEqualTo(homeTeam);
		assertThat(saved.get(0).getAwayTeam()).isNull();
		assertThat(saved.get(1).getHomeTeam()).isEqualTo(homeTeam);
		assertThat(saved.get(1).getAwayTeam()).isNull();
	}

	@Test
	void givenDuplicateMatch_whenCreateMatch_thenThrowsException() {
		// given
		var matchdayId = UUID.randomUUID();
		var homeTeamId = UUID.randomUUID();
		var awayTeamId = UUID.randomUUID();

		var season = new Season("Test Season");
		var matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		matchday.setId(matchdayId);

		var homeTeam = new Team();
		homeTeam.setId(homeTeamId);
		homeTeam.setShortName("HOM");

		var awayTeam = new Team();
		awayTeam.setId(awayTeamId);
		awayTeam.setShortName("AWY");

		when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
		when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
		when(teamRepository.findById(awayTeamId)).thenReturn(Optional.of(awayTeam));
		when(matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(matchdayId, homeTeamId, awayTeamId))
				.thenReturn(true);

		// when / then
		assertThatThrownBy(() -> service.createMatch(matchdayId, homeTeamId, awayTeamId, false))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Match already exists");
	}

	// --- addLeg ---

	@Test
	void givenExistingMatch_whenAddLeg_thenAdditionalRaceCreated() {
		// given
		var matchId = UUID.randomUUID();
		var matchdayId = UUID.randomUUID();

		var season = new Season("Test Season");
		var matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		matchday.setId(matchdayId);
		// legs lives on the phase. Allow more than 1 leg for addLeg tests.
		matchday.getPhase().setLegs(99);

		var homeTeam = new Team();
		homeTeam.setShortName("HOM");
		var awayTeam = new Team();
		awayTeam.setShortName("AWY");

		var match = new Match(matchday, homeTeam, awayTeam);
		match.setId(matchId);
		// One existing race (first leg)
		var existingRace = new Race();
		existingRace.setMatchday(matchday);
		existingRace.setMatch(match);
		match.setRaces(new ArrayList<>());
		match.getRaces().add(existingRace);

		when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
		when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		var result = service.addLeg(matchId);

		// then
		assertThat(result.getRaces()).hasSize(2);
		verify(raceRepository).save(any(Race.class));
	}

	@Test
	void givenMatchWith1Leg_whenAddLeg_thenSecondLegHasSwappedHomeAway() {
		// given
		var matchId = UUID.randomUUID();
		var season = new Season("Test Season");
		var matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		matchday.setId(UUID.randomUUID());
		// legs lives on the phase. Allow more legs for addLeg tests.
		matchday.getPhase().setLegs(99);

		var homeTeam = new Team();
		homeTeam.setId(UUID.randomUUID());
		homeTeam.setShortName("HOM");
		var awayTeam = new Team();
		awayTeam.setId(UUID.randomUUID());
		awayTeam.setShortName("AWY");

		var match = new Match(matchday, homeTeam, awayTeam);
		match.setId(matchId);
		var leg1 = new Race();
		leg1.setMatch(match);
		match.setRaces(new ArrayList<>());
		match.getRaces().add(leg1);

		when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
		when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		service.addLeg(matchId);

		// then
		var savedRace = match.getRaces().get(1);
		assertThat(savedRace.getHomeTeam()).isEqualTo(awayTeam);
		assertThat(savedRace.getAwayTeam()).isEqualTo(homeTeam);
	}

	@Test
	void givenMatchWith2Legs_whenAddThirdLeg_thenNoSwap() {
		// given
		var matchId = UUID.randomUUID();
		var season = new Season("Test Season");
		var matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		matchday.setId(UUID.randomUUID());
		// legs lives on the phase. Allow more legs for addLeg tests.
		matchday.getPhase().setLegs(99);

		var homeTeam = new Team();
		homeTeam.setId(UUID.randomUUID());
		homeTeam.setShortName("HOM");
		var awayTeam = new Team();
		awayTeam.setId(UUID.randomUUID());
		awayTeam.setShortName("AWY");

		var match = new Match(matchday, homeTeam, awayTeam);
		match.setId(matchId);
		match.setRaces(new ArrayList<>());
		match.getRaces().add(new Race());
		match.getRaces().add(new Race());

		when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
		when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

		// when
		service.addLeg(matchId);

		// then — leg 3 (odd, 1-based) has no swap
		var savedRace = match.getRaces().get(2);
		assertThat(savedRace.getHomeTeam()).isEqualTo(homeTeam);
		assertThat(savedRace.getAwayTeam()).isEqualTo(awayTeam);
	}

	// --- deleteMatch ---

	@Test
	void givenExistingMatch_whenDeleteMatch_thenDeletesAndReturnsMatchdayId() {
		// given
		var matchId = UUID.randomUUID();
		var matchdayId = UUID.randomUUID();

		var season = new Season("Test Season");
		var matchday = org.ctc.domain.service.PhaseTestFixtures.matchdayInRegularPhase(season, "MD1", 1);
		matchday.setId(matchdayId);

		var homeTeam = new Team();
		homeTeam.setShortName("HOM");

		var match = new Match(matchday, homeTeam, null);
		match.setId(matchId);

		when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

		// when
		var result = service.deleteMatch(matchId);

		// then
		assertThat(result).isEqualTo(matchdayId);
		verify(matchRepository).delete(match);
	}
}
