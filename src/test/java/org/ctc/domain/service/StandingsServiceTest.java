package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StandingsServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private RaceRepository raceRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private SeasonRepository seasonRepository;

    @InjectMocks
    private StandingsService standingsService;

    private RaceScoring raceScoring;
    private MatchScoring matchScoring;
    private Season season;
    private Team tnr;
    private Team p1r;
    private Team clr;

    @BeforeEach
    void setUp() {
        raceScoring = new RaceScoring("CTC Standard", "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2);
        matchScoring = new MatchScoring("Standard 3-1-0", 3, 1, 0);

        season = new Season("2026");
        season.setId(UUID.randomUUID());
        season.setRaceScoring(raceScoring);
        season.setMatchScoring(matchScoring);

        tnr = new Team("The Neutrals Racing", "TNR");
        tnr.setId(UUID.randomUUID());

        p1r = new Team("Project One Racing", "P1R");
        p1r.setId(UUID.randomUUID());

        clr = new Team("Community League Racing", "CLR");
        clr.setId(UUID.randomUUID());
    }

    @Nested
    class MatchBasedStandingsTest {

        @Test
        void givenOneMatch_whenCalculateStandings_thenWinnerGetThreePoints() {
            // given
            // TNR beats P1R 70:46
            var matchday = new Matchday(season, "Matchday 1", 1);
            var match = createMatchWithScore(matchday, tnr, p1r, 70, 46);

            season.addTeam(tnr);
            season.addTeam(p1r);
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then
            assertEquals(2, standings.size());

            var tnrStanding = findStanding(standings, tnr);
            assertEquals(1, tnrStanding.getWins());
            assertEquals(0, tnrStanding.getDraws());
            assertEquals(0, tnrStanding.getLosses());
            assertEquals(3, tnrStanding.getPoints()); // matchScoring: 3-1-0
            assertEquals(70, tnrStanding.getPointsFor());
            assertEquals(46, tnrStanding.getPointsAgainst());

            var p1rStanding = findStanding(standings, p1r);
            assertEquals(0, p1rStanding.getWins());
            assertEquals(1, p1rStanding.getLosses());
            assertEquals(0, p1rStanding.getPoints());
        }

        @Test
        void givenEqualScores_whenCalculateStandings_thenBothTeamsGetDrawPoint() {
            // given
            var matchday = new Matchday(season, "Matchday 1", 1);
            var match = createMatchWithScore(matchday, clr, tnr, 54, 54);

            season.addTeam(clr);
            season.addTeam(tnr);
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then
            assertEquals(2, standings.size());
            standings.forEach(s -> {
                assertEquals(1, s.getDraws());
                assertEquals(1, s.getPoints()); // matchScoring: 3-1-0, draw = 1
            });
        }

        @Test
        void givenCustomMatchScoring_whenCalculateStandings_thenCustomPointsApplied() {
            // given
            // Use 2-1-0 scoring instead of 3-1-0
            var customMatchScoring = new MatchScoring("Classic 2-1-0", 2, 1, 0);
            season.setMatchScoring(customMatchScoring);

            var matchday = new Matchday(season, "Matchday 1", 1);
            var match = createMatchWithScore(matchday, tnr, p1r, 70, 46);

            season.addTeam(tnr);
            season.addTeam(p1r);
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then
            var tnrStanding = findStanding(standings, tnr);
            assertEquals(2, tnrStanding.getPoints()); // 2 for win
        }

        @Test
        void givenByeMatch_whenCalculateStandings_thenTeamGetsWin() {
            // given
            var matchday = new Matchday(season, "Matchday 1", 1);
            var byeMatch = new Match(matchday, tnr, null);
            byeMatch.setId(UUID.randomUUID());
            byeMatch.setBye(true);

            season.addTeam(tnr);
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(byeMatch));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then
            assertEquals(1, standings.size());
            var tnrStanding = findStanding(standings, tnr);
            assertEquals(1, tnrStanding.getWins());
            assertEquals(3, tnrStanding.getPoints());
        }

        @Test
        void givenMultipleMatches_whenCalculateStandings_thenSortedByPointsThenPointDifference() {
            // given
            var md1 = new Matchday(season, "Matchday 1", 1);
            var md2 = new Matchday(season, "Matchday 2", 2);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);
            var match2 = createMatchWithScore(md2, clr, p1r, 80, 40);

            season.addTeam(tnr);
            season.addTeam(p1r);
            season.addTeam(clr);
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match1, match2));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then
            // CLR first (+40), TNR second (+24), P1R last
            assertEquals(clr.getId(), standings.get(0).getTeam().getId());
            assertEquals(tnr.getId(), standings.get(1).getTeam().getId());
            assertEquals(p1r.getId(), standings.get(2).getTeam().getId());
        }

        @Test
        void givenTeamWithNoGames_whenCalculateStandings_thenTeamExcluded() {
            // given
            var matchday = new Matchday(season, "Matchday 1", 1);
            var match = createMatchWithScore(matchday, tnr, p1r, 70, 46);

            season.addTeam(tnr);
            season.addTeam(p1r);
            season.addTeam(clr);
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then
            assertEquals(2, standings.size());
            assertTrue(standings.stream().noneMatch(s -> s.getTeam().getId().equals(clr.getId())));
        }

        @Test
        void givenMatchWithNoScores_whenCalculateStandings_thenMatchSkipped() {
            // given
            var matchday = new Matchday(season, "Matchday 1", 1);
            var match = new Match(matchday, tnr, p1r);
            match.setId(UUID.randomUUID());
            // No scores set

            season.addTeam(tnr);
            season.addTeam(p1r);
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then
            assertTrue(standings.isEmpty());
        }
    }

    @Nested
    class TeamSuccessionTest {

        @Test
        void givenReplacedTeam_whenCalculateStandings_thenSuccessorInheritsResults() {
            // given
            // Team A (TNR) wins match 1, then gets replaced by Team C (CLR)
            // CLR should inherit TNR's win
            var md1 = new Matchday(season, "Matchday 1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            season.addTeam(tnr);
            season.addTeam(p1r);
            season.addTeam(clr);

            // TNR replaced by CLR
            var stTnr = season.findSeasonTeam(tnr).orElseThrow();
            var stClr = season.findSeasonTeam(clr).orElseThrow();
            stTnr.setSuccessor(stClr);

            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match1));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then — CLR inherits TNR's win
            var clrStanding = findStanding(standings, clr);
            assertEquals(1, clrStanding.getWins());
            assertEquals(3, clrStanding.getPoints());
            assertEquals(70, clrStanding.getPointsFor());
            assertEquals(46, clrStanding.getPointsAgainst());
        }

        @Test
        void givenReplacedTeam_whenCalculateStandings_thenPredecessorNotInStandings() {
            // given
            var md1 = new Matchday(season, "Matchday 1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            season.addTeam(tnr);
            season.addTeam(p1r);
            season.addTeam(clr);

            var stTnr = season.findSeasonTeam(tnr).orElseThrow();
            var stClr = season.findSeasonTeam(clr).orElseThrow();
            stTnr.setSuccessor(stClr);

            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match1));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then — TNR should not appear
            assertTrue(standings.stream().noneMatch(s -> s.getTeam().getId().equals(tnr.getId())));
        }

        @Test
        void givenReplacedTeamAndNewMatches_whenCalculateStandings_thenBothResultsMerged() {
            // given
            // TNR wins match 1, gets replaced by CLR, CLR wins match 2
            var md1 = new Matchday(season, "Matchday 1", 1);
            var md2 = new Matchday(season, "Matchday 2", 2);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);
            var match2 = createMatchWithScore(md2, clr, p1r, 60, 50);

            season.addTeam(tnr);
            season.addTeam(p1r);
            season.addTeam(clr);

            var stTnr = season.findSeasonTeam(tnr).orElseThrow();
            var stClr = season.findSeasonTeam(clr).orElseThrow();
            stTnr.setSuccessor(stClr);

            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match1, match2));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then — CLR has 2 wins (inherited + own)
            var clrStanding = findStanding(standings, clr);
            assertEquals(2, clrStanding.getWins());
            assertEquals(6, clrStanding.getPoints());
            assertEquals(130, clrStanding.getPointsFor());
            assertEquals(96, clrStanding.getPointsAgainst());
        }

        @Test
        void givenSuccessionChain_whenCalculateStandings_thenFinalSuccessorInheritsAll() {
            // given
            // TNR wins, replaced by P1R, P1R replaced by CLR
            var newTeam = new Team("New Team", "NEW");
            newTeam.setId(UUID.randomUUID());

            var md1 = new Matchday(season, "Matchday 1", 1);
            var match1 = createMatchWithScore(md1, tnr, newTeam, 70, 46);

            season.addTeam(tnr);
            season.addTeam(p1r);
            season.addTeam(clr);
            season.addTeam(newTeam);

            // TNR → P1R → CLR
            var stTnr = season.findSeasonTeam(tnr).orElseThrow();
            var stP1r = season.findSeasonTeam(p1r).orElseThrow();
            var stClr = season.findSeasonTeam(clr).orElseThrow();
            stTnr.setSuccessor(stP1r);
            stP1r.setSuccessor(stClr);

            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match1));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then — CLR inherits TNR's win through chain
            var clrStanding = findStanding(standings, clr);
            assertEquals(1, clrStanding.getWins());
            assertEquals(3, clrStanding.getPoints());

            // TNR and P1R should not appear
            assertTrue(standings.stream().noneMatch(s -> s.getTeam().getId().equals(tnr.getId())));
            assertTrue(standings.stream().noneMatch(s -> s.getTeam().getId().equals(p1r.getId())));
        }

        @Test
        void givenReplacedTeamWithBye_whenCalculateStandings_thenSuccessorInheritsByeWin() {
            // given
            var matchday = new Matchday(season, "Matchday 1", 1);
            var byeMatch = new Match(matchday, tnr, null);
            byeMatch.setId(UUID.randomUUID());
            byeMatch.setBye(true);

            season.addTeam(tnr);
            season.addTeam(clr);

            // TNR replaced by CLR
            var stTnr = season.findSeasonTeam(tnr).orElseThrow();
            var stClr = season.findSeasonTeam(clr).orElseThrow();
            stTnr.setSuccessor(stClr);

            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(byeMatch));
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var standings = standingsService.calculateStandings(season.getId());

            // then — CLR inherits TNR's bye win
            assertEquals(1, standings.size());
            var clrStanding = findStanding(standings, clr);
            assertEquals(1, clrStanding.getWins());
            assertEquals(3, clrStanding.getPoints());
        }
    }

    @Nested
    class CalculateStandingsWithBuchholzTest {

        @Test
        void givenSwissSeason_whenCalculateStandingsWithBuchholz_thenStandingsSortedByPointsThenBuchholzThenPointDiffThenPointsFor() {
            // given
            season.setFormat(SeasonFormat.SWISS);
            season.addTeam(tnr);
            season.addTeam(p1r);
            season.addTeam(clr);

            // Matchday 1: TNR beats P1R (70:46), CLR has bye
            var md1 = new Matchday(season, "Round 1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            // Matchday 2: TNR beats CLR (60:50), P1R has bye
            var md2 = new Matchday(season, "Round 2", 2);
            var match2 = createMatchWithScore(md2, tnr, clr, 60, 50);

            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match1, match2));

            // Races for Buchholz opponent tracking
            var race1 = new Race();
            race1.setId(UUID.randomUUID());
            race1.setMatch(match1);
            race1.setMatchday(md1);
            var race2 = new Race();
            race2.setId(UUID.randomUUID());
            race2.setMatch(match2);
            race2.setMatchday(md2);
            when(raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(season.getId()))
                    .thenReturn(List.of(race1, race2));

            // when
            var standings = standingsService.calculateStandingsWithBuchholz(season.getId());

            // then
            assertFalse(standings.isEmpty());
            // TNR: 6pts, played P1R(0pts) + CLR(0pts), Buchholz=0
            // P1R: 0pts, played TNR(6pts), Buchholz=6
            // CLR: 0pts, played TNR(6pts), Buchholz=6
            assertEquals(tnr.getId(), standings.get(0).getTeam().getId());
            // P1R and CLR both have 0 points and Buchholz 6, sorted by pointDiff then pointsFor
            // P1R: pointsFor=46, pointsAgainst=70, diff=-24
            // CLR: pointsFor=50, pointsAgainst=60, diff=-10
            // CLR should be ahead of P1R (better point difference)
            assertEquals(clr.getId(), standings.get(1).getTeam().getId());
            assertEquals(p1r.getId(), standings.get(2).getTeam().getId());
        }

        @Test
        void givenNonSwissSeason_whenCalculateStandingsWithBuchholz_thenBuchholzIsZeroAndStandingsSortedNormally() {
            // given
            season.setFormat(SeasonFormat.LEAGUE);
            season.addTeam(tnr);
            season.addTeam(p1r);

            var md1 = new Matchday(season, "Matchday 1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of(match1));
            when(raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(season.getId()))
                    .thenReturn(List.of());

            // when
            var standings = standingsService.calculateStandingsWithBuchholz(season.getId());

            // then
            assertEquals(2, standings.size());
            assertEquals(tnr.getId(), standings.get(0).getTeam().getId());
            assertEquals(0, standings.get(0).getBuchholz());
            assertEquals(0, standings.get(1).getBuchholz());
        }

        @Test
        void givenNoMatches_whenCalculateStandingsWithBuchholz_thenEmptyList() {
            // given
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
            when(matchRepository.findByMatchdaySeasonId(season.getId())).thenReturn(List.of());

            // when
            var standings = standingsService.calculateStandingsWithBuchholz(season.getId());

            // then
            assertTrue(standings.isEmpty());
        }
    }

    @Nested
    class AlltimeStandingsTest {

        @Test
        void givenTwoSeasonsWithMatches_whenCalculateAlltimeStandings_thenAggregatesAcrossSeasons() {
            // given
            var season1 = new Season("Season 1");
            season1.setId(UUID.randomUUID());
            season1.setMatchScoring(new MatchScoring("Standard 3-1-0", 3, 1, 0));
            season1.setRaceScoring(raceScoring);
            season1.addTeam(tnr);
            season1.addTeam(p1r);

            var season2 = new Season("Season 2");
            season2.setId(UUID.randomUUID());
            season2.setMatchScoring(new MatchScoring("Standard 3-1-0", 3, 1, 0));
            season2.setRaceScoring(raceScoring);
            season2.addTeam(tnr);
            season2.addTeam(clr);

            var md1 = new Matchday(season1, "Matchday 1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            var md2 = new Matchday(season2, "Matchday 1", 1);
            var match2 = createMatchWithScore(md2, tnr, clr, 60, 50);

            when(seasonRepository.findAll()).thenReturn(List.of(season1, season2));
            when(seasonRepository.findById(season1.getId())).thenReturn(Optional.of(season1));
            when(seasonRepository.findById(season2.getId())).thenReturn(Optional.of(season2));
            when(matchRepository.findByMatchdaySeasonId(season1.getId())).thenReturn(List.of(match1));
            when(matchRepository.findByMatchdaySeasonId(season2.getId())).thenReturn(List.of(match2));

            // when
            var standings = standingsService.calculateAlltimeStandings();

            // then
            var tnrStanding = findStanding(standings, tnr);
            assertEquals(2, tnrStanding.getWins());
            assertEquals(6, tnrStanding.getPoints());
            assertEquals(130, tnrStanding.getPointsFor());
            assertEquals(96, tnrStanding.getPointsAgainst());
        }

        @Test
        void givenDifferentScoringPerSeason_whenCalculateAlltimeStandings_thenRespectsScoringRules() {
            // given
            var season1 = new Season("Season 1");
            season1.setId(UUID.randomUUID());
            season1.setMatchScoring(new MatchScoring("Standard 3-1-0", 3, 1, 0));
            season1.setRaceScoring(raceScoring);
            season1.addTeam(tnr);
            season1.addTeam(p1r);

            var season2 = new Season("Season 2");
            season2.setId(UUID.randomUUID());
            season2.setMatchScoring(new MatchScoring("Classic 2-1-0", 2, 1, 0));
            season2.setRaceScoring(raceScoring);
            season2.addTeam(tnr);
            season2.addTeam(clr);

            var md1 = new Matchday(season1, "Matchday 1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            var md2 = new Matchday(season2, "Matchday 1", 1);
            var match2 = createMatchWithScore(md2, tnr, clr, 60, 50);

            when(seasonRepository.findAll()).thenReturn(List.of(season1, season2));
            when(seasonRepository.findById(season1.getId())).thenReturn(Optional.of(season1));
            when(seasonRepository.findById(season2.getId())).thenReturn(Optional.of(season2));
            when(matchRepository.findByMatchdaySeasonId(season1.getId())).thenReturn(List.of(match1));
            when(matchRepository.findByMatchdaySeasonId(season2.getId())).thenReturn(List.of(match2));

            // when
            var standings = standingsService.calculateAlltimeStandings();

            // then
            var tnrStanding = findStanding(standings, tnr);
            assertEquals(5, tnrStanding.getPoints()); // 3 + 2
        }

        @Test
        void givenSubTeam_whenCalculateAlltimeStandings_thenAggregesToParent() {
            // given
            var subTeam = new Team("TNR Sub", "TSB");
            subTeam.setId(UUID.randomUUID());
            subTeam.setParentTeam(tnr);

            var season1 = new Season("Season 1");
            season1.setId(UUID.randomUUID());
            season1.setMatchScoring(new MatchScoring("Standard 3-1-0", 3, 1, 0));
            season1.setRaceScoring(raceScoring);
            season1.addTeam(subTeam);
            season1.addTeam(p1r);

            var md1 = new Matchday(season1, "Matchday 1", 1);
            var match1 = createMatchWithScore(md1, subTeam, p1r, 70, 46);

            when(seasonRepository.findAll()).thenReturn(List.of(season1));
            when(seasonRepository.findById(season1.getId())).thenReturn(Optional.of(season1));
            when(matchRepository.findByMatchdaySeasonId(season1.getId())).thenReturn(List.of(match1));

            // when
            var standings = standingsService.calculateAlltimeStandings();

            // then - results should aggregate to parent team TNR
            var tnrStanding = findStanding(standings, tnr);
            assertEquals(1, tnrStanding.getWins());
            assertEquals(3, tnrStanding.getPoints());
            assertEquals(70, tnrStanding.getPointsFor());
        }

        @Test
        void givenSeasonWithNoMatches_whenCalculateAlltimeStandings_thenSeasonSkipped() {
            // given
            var season1 = new Season("Season 1");
            season1.setId(UUID.randomUUID());
            season1.setMatchScoring(new MatchScoring("Standard 3-1-0", 3, 1, 0));
            season1.setRaceScoring(raceScoring);
            season1.addTeam(tnr);
            season1.addTeam(p1r);

            var season2 = new Season("Season 2");
            season2.setId(UUID.randomUUID());
            season2.setMatchScoring(new MatchScoring("Standard 3-1-0", 3, 1, 0));
            season2.setRaceScoring(raceScoring);
            season2.addTeam(clr);

            var md1 = new Matchday(season1, "Matchday 1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            when(seasonRepository.findAll()).thenReturn(List.of(season1, season2));
            when(seasonRepository.findById(season1.getId())).thenReturn(Optional.of(season1));
            when(seasonRepository.findById(season2.getId())).thenReturn(Optional.of(season2));
            when(matchRepository.findByMatchdaySeasonId(season1.getId())).thenReturn(List.of(match1));
            when(matchRepository.findByMatchdaySeasonId(season2.getId())).thenReturn(List.of());

            // when
            var standings = standingsService.calculateAlltimeStandings();

            // then - only teams from season1 appear
            assertEquals(2, standings.size());
            assertTrue(standings.stream().noneMatch(s -> s.getTeam().getId().equals(clr.getId())));
        }

        @Test
        void givenNoSeasons_whenCalculateAlltimeStandings_thenReturnsEmptyList() {
            // given
            when(seasonRepository.findAll()).thenReturn(List.of());

            // when
            var standings = standingsService.calculateAlltimeStandings();

            // then
            assertTrue(standings.isEmpty());
        }

        @Test
        void givenMultipleTeams_whenCalculateAlltimeStandings_thenSortedByPointsThenPointDiffThenPointsFor() {
            // given
            var season1 = new Season("Season 1");
            season1.setId(UUID.randomUUID());
            season1.setMatchScoring(new MatchScoring("Standard 3-1-0", 3, 1, 0));
            season1.setRaceScoring(raceScoring);
            season1.addTeam(tnr);
            season1.addTeam(p1r);
            season1.addTeam(clr);

            // TNR beats P1R 70:46 (+24), CLR beats P1R 80:40 (+40)
            // Both TNR and CLR have 3 points, but CLR has better point diff
            var md1 = new Matchday(season1, "Matchday 1", 1);
            var md2 = new Matchday(season1, "Matchday 2", 2);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);
            var match2 = createMatchWithScore(md2, clr, p1r, 80, 40);

            when(seasonRepository.findAll()).thenReturn(List.of(season1));
            when(seasonRepository.findById(season1.getId())).thenReturn(Optional.of(season1));
            when(matchRepository.findByMatchdaySeasonId(season1.getId())).thenReturn(List.of(match1, match2));

            // when
            var standings = standingsService.calculateAlltimeStandings();

            // then - CLR first (better point diff), TNR second, P1R last
            assertEquals(clr.getId(), standings.get(0).getTeam().getId());
            assertEquals(tnr.getId(), standings.get(1).getTeam().getId());
            assertEquals(p1r.getId(), standings.get(2).getTeam().getId());
        }

        @Test
        void givenAlltimeStandings_whenCalculated_thenBuchholzIsAlwaysZero() {
            // given
            var season1 = new Season("Season 1");
            season1.setId(UUID.randomUUID());
            season1.setMatchScoring(new MatchScoring("Standard 3-1-0", 3, 1, 0));
            season1.setRaceScoring(raceScoring);
            season1.addTeam(tnr);
            season1.addTeam(p1r);

            var md1 = new Matchday(season1, "Matchday 1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            when(seasonRepository.findAll()).thenReturn(List.of(season1));
            when(seasonRepository.findById(season1.getId())).thenReturn(Optional.of(season1));
            when(matchRepository.findByMatchdaySeasonId(season1.getId())).thenReturn(List.of(match1));

            // when
            var standings = standingsService.calculateAlltimeStandings();

            // then
            assertFalse(standings.isEmpty());
            standings.forEach(s -> assertEquals(0, s.getBuchholz()));
        }
    }

    private Match createMatchWithScore(Matchday matchday, Team home, Team away, int homeScore, int awayScore) {
        var match = new Match(matchday, home, away);
        match.setId(UUID.randomUUID());
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        return match;
    }

    private StandingsService.TeamStanding findStanding(List<StandingsService.TeamStanding> standings, Team team) {
        return standings.stream()
                .filter(s -> s.getTeam().getId().equals(team.getId()))
                .findFirst().orElseThrow();
    }
}
