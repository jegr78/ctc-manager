package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.repository.PlayoffSeedRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayoffSeedingServiceTest {

	@Mock
	private PlayoffRepository playoffRepository;
	@Mock
	private PlayoffMatchupRepository playoffMatchupRepository;
	@Mock
	private PlayoffSeedRepository playoffSeedRepository;
	@Mock
	private TeamRepository teamRepository;
	@Mock
	private PlayoffBracketViewService playoffBracketViewService;
	@Mock
	private EntityManager entityManager;
	@Mock
	private SeasonPhaseService seasonPhaseService;
	@Mock
	private StandingsService standingsService;
	@Mock
	private PhaseTeamRepository phaseTeamRepository;

	@InjectMocks
	private PlayoffSeedingService playoffSeedingService;

	private Playoff playoff;
	private UUID playoffId;
	private Team team1;
	private Team team2;
	private Team team3;
	private Team team4;

	@BeforeEach
	void setUp() {
		playoffId = UUID.randomUUID();
		var season = new Season("Test Season");
		season.setId(UUID.randomUUID());

		// Playoff is bound to a SeasonPhase, not the Season directly.
		playoff = PhaseTestFixtures.playoffForSeason(season, "Test Playoff");
		playoff.setId(playoffId);

		team1 = createTeam("T1");
		team2 = createTeam("T2");
		team3 = createTeam("T3");
		team4 = createTeam("T4");
	}

	private Team createTeam(String name) {
		var team = new Team(name, name);
		team.setId(UUID.randomUUID());
		return team;
	}

	@Nested
	class SeedTeam {

		@Test
		void givenMatchup_whenSeedTeamSlot1_thenTeam1Set() {
			// given
			var matchupId = UUID.randomUUID();
			var round = new PlayoffRound(playoff, "Final", 0);
			var matchup = new PlayoffMatchup(round, 0);
			matchup.setId(matchupId);

			when(playoffMatchupRepository.findById(matchupId)).thenReturn(Optional.of(matchup));
			when(teamRepository.findById(team1.getId())).thenReturn(Optional.of(team1));
			when(playoffMatchupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			// when
			playoffSeedingService.seedTeam(matchupId, team1.getId(), 1);

			// then
			assertEquals(team1, matchup.getTeam1());
			verify(playoffMatchupRepository).save(matchup);
		}

		@Test
		void givenMatchup_whenSeedTeamSlot2_thenTeam2Set() {
			// given
			var matchupId = UUID.randomUUID();
			var round = new PlayoffRound(playoff, "Final", 0);
			var matchup = new PlayoffMatchup(round, 0);
			matchup.setId(matchupId);

			when(playoffMatchupRepository.findById(matchupId)).thenReturn(Optional.of(matchup));
			when(teamRepository.findById(team2.getId())).thenReturn(Optional.of(team2));
			when(playoffMatchupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			// when
			playoffSeedingService.seedTeam(matchupId, team2.getId(), 2);

			// then
			assertEquals(team2, matchup.getTeam2());
		}

		@Test
		void givenMatchup_whenSeedTeamWithInvalidSlot_thenThrowsIllegalArgument() {
			// given
			var matchupId = UUID.randomUUID();
			var round = new PlayoffRound(playoff, "Final", 0);
			var matchup = new PlayoffMatchup(round, 0);
			matchup.setId(matchupId);

			when(playoffMatchupRepository.findById(matchupId)).thenReturn(Optional.of(matchup));

			// when / then
			assertThrows(IllegalArgumentException.class, () ->
					playoffSeedingService.seedTeam(matchupId, team1.getId(), 3));
		}
	}

	@Nested
	class AutoSeedBracket {

		@Test
		void givenNoSeedNumbers_whenAutoSeedBracket_thenThrowsIllegalState() {
			// given
			when(playoffSeedRepository.findByPlayoffId(playoffId)).thenReturn(List.of());

			// when / then
			assertThrows(IllegalStateException.class, () ->
					playoffSeedingService.autoSeedBracket(playoffId));
		}

		@Test
		void givenFourSeeds_whenAutoSeedBracket_thenMatchupsPopulatedWithCorrectPairings() {
			// given
			var round = new PlayoffRound(playoff, "Semifinal", 0);
			round.setId(UUID.randomUUID());

			var matchup0 = new PlayoffMatchup(round, 0);
			matchup0.setId(UUID.randomUUID());
			var matchup1 = new PlayoffMatchup(round, 1);
			matchup1.setId(UUID.randomUUID());
			round.getMatchups().add(matchup0);
			round.getMatchups().add(matchup1);
			playoff.getRounds().add(round);

			var seed1 = new PlayoffSeed(playoff, team1, 1);
			var seed2 = new PlayoffSeed(playoff, team2, 2);
			var seed3 = new PlayoffSeed(playoff, team3, 3);
			var seed4 = new PlayoffSeed(playoff, team4, 4);

			when(playoffSeedRepository.findByPlayoffId(playoffId))
					.thenReturn(List.of(seed1, seed2, seed3, seed4));
			when(playoffRepository.findById(playoffId)).thenReturn(Optional.of(playoff));
			when(playoffMatchupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			// when
			playoffSeedingService.autoSeedBracket(playoffId);

			// then — Matchup 0: Seed 1 vs Seed 4, Matchup 1: Seed 2 vs Seed 3
			assertEquals(team1, matchup0.getTeam1()); // seed 1
			assertEquals(team4, matchup0.getTeam2()); // seed 4
			assertEquals(team2, matchup1.getTeam1()); // seed 2
			assertEquals(team3, matchup1.getTeam2()); // seed 3
		}
	}

	@Nested
	class GetSeedingData {

		@Test
		void givenPlayoffWithNoSeeds_whenGetSeedingData_thenReturnsEmptySeededIds() {
			// given
			var round = new PlayoffRound(playoff, "Semifinal", 0);
			round.setId(UUID.randomUUID());
			var matchup = new PlayoffMatchup(round, 0);
			matchup.setId(UUID.randomUUID());
			round.getMatchups().add(matchup);
			playoff.getRounds().add(round);
			playoff.getSeason().addTeam(team1);
			playoff.getSeason().addTeam(team2);

			var bracketView = new PlayoffBracketViewService.PlayoffBracketView(
					playoffId, "Test", List.of());

			when(playoffRepository.findById(playoffId)).thenReturn(Optional.of(playoff));
			when(playoffBracketViewService.getBracketView(playoffId)).thenReturn(bracketView);
			when(playoffSeedRepository.findByPlayoffId(playoffId)).thenReturn(List.of());

			// when
			var data = playoffSeedingService.getSeedingData(playoffId);

			// then
			assertNotNull(data.playoff());
			assertNotNull(data.bracketView());
			assertNotNull(data.firstRound());
			assertTrue(data.seededTeamIds().isEmpty());
			assertFalse(data.teams().isEmpty());
		}

		@Test
		void givenPlayoffWithOneSeededTeam_whenGetSeedingData_thenSeededIdTracked() {
			// given
			var round = new PlayoffRound(playoff, "Semifinal", 0);
			round.setId(UUID.randomUUID());
			var matchup = new PlayoffMatchup(round, 0);
			matchup.setId(UUID.randomUUID());
			matchup.setTeam1(team1);
			round.getMatchups().add(matchup);
			playoff.getRounds().add(round);
			playoff.getSeason().addTeam(team1);
			playoff.getSeason().addTeam(team2);

			var bracketView = new PlayoffBracketViewService.PlayoffBracketView(
					playoffId, "Test", List.of());

			when(playoffRepository.findById(playoffId)).thenReturn(Optional.of(playoff));
			when(playoffBracketViewService.getBracketView(playoffId)).thenReturn(bracketView);
			when(playoffSeedRepository.findByPlayoffId(playoffId)).thenReturn(List.of());

			// when
			var data = playoffSeedingService.getSeedingData(playoffId);

			// then
			assertTrue(data.seededTeamIds().contains(team1.getId()));
			assertEquals(1, data.seededTeamIds().size());
		}
	}


	@Nested
	class SaveSeed {

		@Test
		void givenSeedEntries_whenSaveSeed_thenTeamsSeededAndSaved() {
			// given
			var matchupId = UUID.randomUUID();
			var round = new PlayoffRound(playoff, "Final", 0);
			var matchup = new PlayoffMatchup(round, 0);
			matchup.setId(matchupId);

			var seeds = List.of(
					new PlayoffSeedingService.SeedEntry(matchupId, 1, team1.getId(), null)
			);

			when(playoffMatchupRepository.findById(matchupId)).thenReturn(Optional.of(matchup));
			when(teamRepository.findById(team1.getId())).thenReturn(Optional.of(team1));
			when(playoffMatchupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			// when
			playoffSeedingService.saveSeed(playoffId, seeds);

			// then
			assertEquals(team1, matchup.getTeam1());
		}

		@Test
		void givenSeedEntryWithNullTeamId_whenSaveSeed_thenEntrySkipped() {
			// given
			var seeds = List.of(
					new PlayoffSeedingService.SeedEntry(UUID.randomUUID(), 1, null, null)
			);

			// when
			playoffSeedingService.saveSeed(playoffId, seeds);

			// then — no matchup lookup should occur
			verify(playoffMatchupRepository, never()).findById(any());
		}
	}

	// Top-N from REGULAR phase standings + PhaseTeam side-effect

	@Nested
	class AutoSeedFromRegularStandings {

		@Test
		void givenRegularLeagueStandings_whenAutoSeedBracket_thenSeedsTopNFromStandings() {
			// given — playoff with 4 matchup-slots (8-team bracket: QF), no manual seeds yet,
			// REGULAR-LEAGUE phase has standings for 8+ teams ordered by points
			var rs = new RaceScoring("RS", "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2);
			var ms = new MatchScoring("MS", 3, 1, 0);
			rs.setId(UUID.randomUUID());
			ms.setId(UUID.randomUUID());

			var season = playoff.getSeason();

			var regularPhase = PhaseTestFixtures.regularPhase(season, rs, ms);
			var playoffPhase = PhaseTestFixtures.playoffPhase(season, "Cup", rs, ms);
			playoff.setPhase(playoffPhase);

			// 4-team bracket → 2 matchups → totalTeams=4
			var round = new PlayoffRound(playoff, "Semifinal", 0);
			round.setId(UUID.randomUUID());
			var matchup0 = new PlayoffMatchup(round, 0);
			matchup0.setId(UUID.randomUUID());
			var matchup1 = new PlayoffMatchup(round, 1);
			matchup1.setId(UUID.randomUUID());
			round.getMatchups().add(matchup0);
			round.getMatchups().add(matchup1);
			playoff.getRounds().add(round);

			// Standings: team1 > team2 > team3 > team4 (so Top-4 are team1..team4)
			var s1 = new StandingsService.TeamStanding(team1);
			s1.addWin();
			s1.addMatchPoints(9);
			var s2 = new StandingsService.TeamStanding(team2);
			s2.addWin();
			s2.addMatchPoints(7);
			var s3 = new StandingsService.TeamStanding(team3);
			s3.addWin();
			s3.addMatchPoints(5);
			var s4 = new StandingsService.TeamStanding(team4);
			s4.addWin();
			s4.addMatchPoints(3);
			List<StandingsService.TeamStanding> standings = List.of(s1, s2, s3, s4);

			when(playoffRepository.findById(playoffId)).thenReturn(Optional.of(playoff));
			when(seasonPhaseService.findByType(season.getId(), PhaseType.REGULAR))
					.thenReturn(Optional.of(regularPhase));
			when(standingsService.calculateStandings(regularPhase.getId(), null))
					.thenReturn(standings);
			when(playoffSeedRepository.findByPlayoffId(playoffId)).thenReturn(List.of());
			when(phaseTeamRepository.findByPhaseId(playoffPhase.getId())).thenReturn(List.of());
			when(playoffMatchupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
			when(playoffSeedRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
			when(phaseTeamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			// when
			playoffSeedingService.autoSeedBracket(playoffId);

			// then — D-15: Top-4 from standings populates the bracket
			// Bracket order for 4 teams: matchup 0 → seed1 vs seed4, matchup 1 → seed2 vs seed3
			verify(standingsService).calculateStandings(regularPhase.getId(), null);
			assertEquals(team1, matchup0.getTeam1(), "Seed 1 in matchup 0 slot 1");
			assertEquals(team4, matchup0.getTeam2(), "Seed 4 in matchup 0 slot 2");
			assertEquals(team2, matchup1.getTeam1(), "Seed 2 in matchup 1 slot 1");
			assertEquals(team3, matchup1.getTeam2(), "Seed 3 in matchup 1 slot 2");
		}

		@Test
		void givenRegularGroupsStandings_whenAutoSeedBracket_thenSeedsTopNFromCombinedView() {
			// given — REGULAR phase has GROUPS layout; combined-view (groupId=null) flattens
			// all groups into one ranking; autoSeedBracket must use that combined view
			var rs = new RaceScoring("RS", "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2);
			var ms = new MatchScoring("MS", 3, 1, 0);
			rs.setId(UUID.randomUUID());
			ms.setId(UUID.randomUUID());

			var season = playoff.getSeason();

			var regularPhase = PhaseTestFixtures.groupsRegularPhase(season, rs, ms, "A", "B");
			var playoffPhase = PhaseTestFixtures.playoffPhase(season, "Cup", rs, ms);
			playoff.setPhase(playoffPhase);

			var round = new PlayoffRound(playoff, "Semifinal", 0);
			round.setId(UUID.randomUUID());
			var matchup0 = new PlayoffMatchup(round, 0);
			matchup0.setId(UUID.randomUUID());
			var matchup1 = new PlayoffMatchup(round, 1);
			matchup1.setId(UUID.randomUUID());
			round.getMatchups().add(matchup0);
			round.getMatchups().add(matchup1);
			playoff.getRounds().add(round);

			// Combined-view standings spanning groups A and B
			var s1 = new StandingsService.TeamStanding(team1);
			s1.addWin();
			s1.addMatchPoints(9);
			var s2 = new StandingsService.TeamStanding(team2);
			s2.addWin();
			s2.addMatchPoints(7);
			var s3 = new StandingsService.TeamStanding(team3);
			s3.addWin();
			s3.addMatchPoints(5);
			var s4 = new StandingsService.TeamStanding(team4);
			s4.addWin();
			s4.addMatchPoints(3);
			List<StandingsService.TeamStanding> combined = List.of(s1, s2, s3, s4);

			when(playoffRepository.findById(playoffId)).thenReturn(Optional.of(playoff));
			when(seasonPhaseService.findByType(season.getId(), PhaseType.REGULAR))
					.thenReturn(Optional.of(regularPhase));
			when(standingsService.calculateStandings(regularPhase.getId(), null))
					.thenReturn(combined);
			when(playoffSeedRepository.findByPlayoffId(playoffId)).thenReturn(List.of());
			when(phaseTeamRepository.findByPhaseId(playoffPhase.getId())).thenReturn(List.of());
			when(playoffMatchupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
			when(playoffSeedRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
			when(phaseTeamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			// when
			playoffSeedingService.autoSeedBracket(playoffId);

			// then — D-15: combined-view drives the seed pool, not per-group calls
			verify(standingsService).calculateStandings(regularPhase.getId(), null);
			verify(standingsService, never()).calculateStandings(eq(regularPhase.getId()),
					argThat(id -> id != null));
			assertEquals(team1, matchup0.getTeam1());
			assertEquals(team4, matchup0.getTeam2());
			assertEquals(team2, matchup1.getTeam1());
			assertEquals(team3, matchup1.getTeam2());
		}

		@Test
		void givenAutoSeedBracket_whenSeedsCreated_thenPhaseTeamRowsExistOnPlayoffPhase() {
			// given — D-20 side-effect: each seeded team becomes a PhaseTeam row on the PLAYOFF phase
			var rs = new RaceScoring("RS", "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2);
			var ms = new MatchScoring("MS", 3, 1, 0);
			rs.setId(UUID.randomUUID());
			ms.setId(UUID.randomUUID());

			var season = playoff.getSeason();

			var regularPhase = PhaseTestFixtures.regularPhase(season, rs, ms);
			var playoffPhase = PhaseTestFixtures.playoffPhase(season, "Cup", rs, ms);
			playoff.setPhase(playoffPhase);

			var round = new PlayoffRound(playoff, "Semifinal", 0);
			round.setId(UUID.randomUUID());
			var matchup0 = new PlayoffMatchup(round, 0);
			matchup0.setId(UUID.randomUUID());
			var matchup1 = new PlayoffMatchup(round, 1);
			matchup1.setId(UUID.randomUUID());
			round.getMatchups().add(matchup0);
			round.getMatchups().add(matchup1);
			playoff.getRounds().add(round);

			var s1 = new StandingsService.TeamStanding(team1);
			s1.addWin();
			var s2 = new StandingsService.TeamStanding(team2);
			s2.addWin();
			var s3 = new StandingsService.TeamStanding(team3);
			s3.addWin();
			var s4 = new StandingsService.TeamStanding(team4);
			s4.addWin();

			when(playoffRepository.findById(playoffId)).thenReturn(Optional.of(playoff));
			when(seasonPhaseService.findByType(season.getId(), PhaseType.REGULAR))
					.thenReturn(Optional.of(regularPhase));
			when(standingsService.calculateStandings(regularPhase.getId(), null))
					.thenReturn(List.of(s1, s2, s3, s4));
			when(playoffSeedRepository.findByPlayoffId(playoffId)).thenReturn(List.of());
			when(phaseTeamRepository.findByPhaseId(playoffPhase.getId())).thenReturn(List.of());
			when(playoffMatchupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
			when(playoffSeedRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
			when(phaseTeamRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

			// when
			playoffSeedingService.autoSeedBracket(playoffId);

			// then — D-20: 4 PhaseTeam rows saved on the PLAYOFF phase, one per seeded team
			ArgumentCaptor<PhaseTeam> captor = ArgumentCaptor.forClass(PhaseTeam.class);
			verify(phaseTeamRepository, times(4)).save(captor.capture());
			var savedTeams = captor.getAllValues().stream()
					.map(PhaseTeam::getTeam)
					.toList();
			assertThat(savedTeams).containsExactlyInAnyOrder(team1, team2, team3, team4);
			// All saved on the PLAYOFF phase
			assertThat(captor.getAllValues()).allMatch(pt -> pt.getPhase().getId().equals(playoffPhase.getId()));
		}
	}
}
