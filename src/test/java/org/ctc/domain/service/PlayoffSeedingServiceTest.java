package org.ctc.domain.service;

import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlayoffSeedingServiceTest {

    @Mock private PlayoffRepository playoffRepository;
    @Mock private PlayoffMatchupRepository playoffMatchupRepository;
    @Mock private PlayoffSeedRepository playoffSeedRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private PlayoffBracketViewService playoffBracketViewService;
    @Mock private EntityManager entityManager;

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

        playoff = new Playoff(season, "Test Playoff");
        playoff.setId(playoffId);

        team1 = createTeam("T1");
        team2 = createTeam("T2");
        team3 = createTeam("T3");
        team4 = createTeam("T4");
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

    // --- Helper methods ---

    private Team createTeam(String name) {
        var team = new Team(name, name);
        team.setId(UUID.randomUUID());
        return team;
    }
}
