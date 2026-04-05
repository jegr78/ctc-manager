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
            var matchday = new Matchday(season, "Spieltag 1", 1);
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
            var matchday = new Matchday(season, "Spieltag 1", 1);
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

            var matchday = new Matchday(season, "Spieltag 1", 1);
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
            var matchday = new Matchday(season, "Spieltag 1", 1);
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
            var md1 = new Matchday(season, "Spieltag 1", 1);
            var md2 = new Matchday(season, "Spieltag 2", 2);
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
            var matchday = new Matchday(season, "Spieltag 1", 1);
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
            var matchday = new Matchday(season, "Spieltag 1", 1);
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
            var md1 = new Matchday(season, "Spieltag 1", 1);
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
            var md1 = new Matchday(season, "Spieltag 1", 1);
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
            var md1 = new Matchday(season, "Spieltag 1", 1);
            var md2 = new Matchday(season, "Spieltag 2", 2);
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

            var md1 = new Matchday(season, "Spieltag 1", 1);
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
            var matchday = new Matchday(season, "Spieltag 1", 1);
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
    class AlltimeStandingsTest {

        private Season season1;
        private Season season2;
        private MatchScoring matchScoring310;
        private MatchScoring matchScoring210;
        private Team tnr2;

        @BeforeEach
        void setUp() {
            matchScoring310 = new MatchScoring("Standard 3-1-0", 3, 1, 0);
            matchScoring210 = new MatchScoring("Classic 2-1-0", 2, 1, 0);

            season1 = new Season("2025");
            season1.setId(UUID.randomUUID());
            season1.setRaceScoring(raceScoring);
            season1.setMatchScoring(matchScoring310);

            season2 = new Season("2026");
            season2.setId(UUID.randomUUID());
            season2.setRaceScoring(raceScoring);
            season2.setMatchScoring(matchScoring310);

            tnr2 = new Team("TNR Sub-Team", "TN2");
            tnr2.setId(UUID.randomUUID());
            tnr2.setParentTeam(tnr);
        }

        @Test
        void givenTwoSeasonsWithMatches_whenCalculateAlltimeStandings_thenAggregatesAcrossSeasons() {
            // given
            season1.addTeam(tnr);
            season1.addTeam(p1r);
            var md1 = new Matchday(season1, "Spieltag 1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            season2.addTeam(tnr);
            season2.addTeam(p1r);
            var md2 = new Matchday(season2, "Spieltag 1", 1);
            var match2 = createMatchWithScore(md2, tnr, p1r, 60, 50);

            when(seasonRepository.findAll()).thenReturn(List.of(season1, season2));
            when(seasonRepository.findById(season1.getId())).thenReturn(Optional.of(season1));
            when(seasonRepository.findById(season2.getId())).thenReturn(Optional.of(season2));
            when(matchRepository.findByMatchdaySeasonId(season1.getId())).thenReturn(List.of(match1));
            when(matchRepository.findByMatchdaySeasonId(season2.getId())).thenReturn(List.of(match2));

            // when
            var standings = standingsService.calculateAlltimeStandings();

            // then
            assertEquals(2, standings.size());

            var tnrStanding = findStanding(standings, tnr);
            assertEquals(2, tnrStanding.getWins());
            assertEquals(0, tnrStanding.getDraws());
            assertEquals(0, tnrStanding.getLosses());
            assertEquals(6, tnrStanding.getPoints());
            assertEquals(130, tnrStanding.getPointsFor());
            assertEquals(96, tnrStanding.getPointsAgainst());

            var p1rStanding = findStanding(standings, p1r);
            assertEquals(0, p1rStanding.getWins());
            assertEquals(2, p1rStanding.getLosses());
            assertEquals(0, p1rStanding.getPoints());
            assertEquals(96, p1rStanding.getPointsFor());
            assertEquals(130, p1rStanding.getPointsAgainst());
        }

        @Test
        void givenDifferentMatchScoringPerSeason_whenCalculateAlltimeStandings_thenRespectsSeasonsOwnRules() {
            // given
            season2.setMatchScoring(matchScoring210);

            season1.addTeam(tnr);
            season1.addTeam(p1r);
            var md1 = new Matchday(season1, "Spieltag 1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            season2.addTeam(tnr);
            season2.addTeam(p1r);
            var md2 = new Matchday(season2, "Spieltag 1", 1);
            var match2 = createMatchWithScore(md2, tnr, p1r, 60, 50);

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
            assertEquals(5, tnrStanding.getPoints()); // 3 + 2, not 6
        }

        @Test
        void givenSubTeamInOneSeason_whenCalculateAlltimeStandings_thenAggregesToParentTeam() {
            // given
            season1.addTeam(tnr);
            season1.addTeam(p1r);
            var md1 = new Matchday(season1, "Spieltag 1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            season2.addTeam(tnr2);
            season2.addTeam(p1r);
            var md2 = new Matchday(season2, "Spieltag 1", 1);
            var match2 = createMatchWithScore(md2, tnr2, p1r, 60, 50);

            when(seasonRepository.findAll()).thenReturn(List.of(season1, season2));
            when(seasonRepository.findById(season1.getId())).thenReturn(Optional.of(season1));
            when(seasonRepository.findById(season2.getId())).thenReturn(Optional.of(season2));
            when(matchRepository.findByMatchdaySeasonId(season1.getId())).thenReturn(List.of(match1));
            when(matchRepository.findByMatchdaySeasonId(season2.getId())).thenReturn(List.of(match2));

            // when
            var standings = standingsService.calculateAlltimeStandings();

            // then — TNR should have wins from both own and sub-team
            var tnrStanding = findStanding(standings, tnr);
            assertEquals(2, tnrStanding.getWins());
            assertEquals(6, tnrStanding.getPoints());
            assertEquals(130, tnrStanding.getPointsFor());
            assertEquals(96, tnrStanding.getPointsAgainst());
        }

        @Test
        void givenSeasonWithNoMatches_whenCalculateAlltimeStandings_thenSeasonExcluded() {
            // given
            season1.addTeam(tnr);
            season1.addTeam(p1r);
            var md1 = new Matchday(season1, "Spieltag 1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            // season2 has no matches
            season2.addTeam(tnr);
            season2.addTeam(p1r);

            when(seasonRepository.findAll()).thenReturn(List.of(season1, season2));
            when(seasonRepository.findById(season1.getId())).thenReturn(Optional.of(season1));
            when(seasonRepository.findById(season2.getId())).thenReturn(Optional.of(season2));
            when(matchRepository.findByMatchdaySeasonId(season1.getId())).thenReturn(List.of(match1));
            when(matchRepository.findByMatchdaySeasonId(season2.getId())).thenReturn(List.of());

            // when
            var standings = standingsService.calculateAlltimeStandings();

            // then — only season 1 results
            assertEquals(2, standings.size());
            var tnrStanding = findStanding(standings, tnr);
            assertEquals(1, tnrStanding.getWins());
            assertEquals(3, tnrStanding.getPoints());
        }

        @Test
        void givenNoSeasonsWithMatches_whenCalculateAlltimeStandings_thenReturnsEmptyList() {
            // given
            when(seasonRepository.findAll()).thenReturn(List.of());

            // when
            var standings = standingsService.calculateAlltimeStandings();

            // then
            assertTrue(standings.isEmpty());
        }

        @Test
        void givenAlltimeStandings_whenCalculated_thenSortedByPointsThenPointDiffThenPointsFor() {
            // given
            // Season with three teams: TNR wins big, CLR wins small, P1R loses both
            season1.addTeam(tnr);
            season1.addTeam(p1r);
            season1.addTeam(clr);
            var md1 = new Matchday(season1, "Spieltag 1", 1);
            var md2 = new Matchday(season1, "Spieltag 2", 2);
            var match1 = createMatchWithScore(md1, tnr, p1r, 80, 40); // TNR +40
            var match2 = createMatchWithScore(md2, clr, p1r, 60, 50); // CLR +10

            when(seasonRepository.findAll()).thenReturn(List.of(season1));
            when(seasonRepository.findById(season1.getId())).thenReturn(Optional.of(season1));
            when(matchRepository.findByMatchdaySeasonId(season1.getId())).thenReturn(List.of(match1, match2));

            // when
            var standings = standingsService.calculateAlltimeStandings();

            // then — both TNR and CLR have 3 pts, but TNR has better point diff
            assertEquals(3, standings.size());
            assertEquals(tnr.getId(), standings.get(0).getTeam().getId()); // 3pts, +40
            assertEquals(clr.getId(), standings.get(1).getTeam().getId()); // 3pts, +10
            assertEquals(p1r.getId(), standings.get(2).getTeam().getId()); // 0pts
        }

        @Test
        void givenNoBuchholz_whenCalculateAlltimeStandings_thenBuchholzIsZero() {
            // given
            season1.addTeam(tnr);
            season1.addTeam(p1r);
            var md1 = new Matchday(season1, "Spieltag 1", 1);
            var match1 = createMatchWithScore(md1, tnr, p1r, 70, 46);

            when(seasonRepository.findAll()).thenReturn(List.of(season1));
            when(seasonRepository.findById(season1.getId())).thenReturn(Optional.of(season1));
            when(matchRepository.findByMatchdaySeasonId(season1.getId())).thenReturn(List.of(match1));

            // when
            var standings = standingsService.calculateAlltimeStandings();

            // then — alltime standings never include Buchholz
            for (var standing : standings) {
                assertEquals(0, standing.getBuchholz());
            }
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
