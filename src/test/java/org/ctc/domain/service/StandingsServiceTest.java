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
