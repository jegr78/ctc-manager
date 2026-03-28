package de.ctc.domain.service;

import de.ctc.domain.model.*;
import de.ctc.domain.repository.MatchdayLineupRepository;
import de.ctc.domain.repository.RaceRepository;
import de.ctc.domain.repository.SeasonRepository;
import de.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StandingsServiceTest {

    @Mock
    private RaceRepository raceRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private MatchdayLineupRepository matchdayLineupRepository;

    @Spy
    private ScoringService scoringService;

    @InjectMocks
    private StandingsService standingsService;

    private Season season;
    private Team tnr;
    private Team p1r;
    private Team clr;

    @BeforeEach
    void setUp() {
        season = new Season("2026");
        season.setId(UUID.randomUUID());

        tnr = new Team("The Neutrals Racing", "TNR");
        tnr.setId(UUID.randomUUID());

        p1r = new Team("Project One Racing", "P1R");
        p1r.setId(UUID.randomUUID());

        clr = new Team("Community League Racing", "CLR");
        clr.setId(UUID.randomUUID());
    }

    @Test
    void shouldCalculateStandingsForWin() {
        // TNR beats P1R 70:46
        var matchday = new Matchday(season, "Spieltag 1", 1);
        var race = createRaceWithResults(matchday, tnr, p1r, 70, 46);

        season.setTeams(new java.util.ArrayList<>(List.of(tnr, p1r)));
        when(raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(season.getId())).thenReturn(List.of(race));
        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

        var standings = standingsService.calculateStandings(season.getId());

        assertEquals(2, standings.size());

        var tnrStanding = standings.stream()
                .filter(s -> s.getTeam().getId().equals(tnr.getId()))
                .findFirst().orElseThrow();

        assertEquals(1, tnrStanding.getWins());
        assertEquals(0, tnrStanding.getDraws());
        assertEquals(0, tnrStanding.getLosses());
        assertEquals(3, tnrStanding.getPoints());
        assertEquals(70, tnrStanding.getPointsFor());
        assertEquals(46, tnrStanding.getPointsAgainst());
        assertEquals(24, tnrStanding.getPointDifference());
        assertEquals("70:46", tnrStanding.getPointsRatio());

        var p1rStanding = standings.stream()
                .filter(s -> s.getTeam().getId().equals(p1r.getId()))
                .findFirst().orElseThrow();

        assertEquals(0, p1rStanding.getWins());
        assertEquals(1, p1rStanding.getLosses());
        assertEquals(0, p1rStanding.getPoints());
    }

    @Test
    void shouldCalculateStandingsForDraw() {
        // CLR draws with AHR 54:54
        var matchday = new Matchday(season, "Spieltag 1", 1);
        var race = createRaceWithResults(matchday, clr, tnr, 54, 54);

        season.setTeams(new java.util.ArrayList<>(List.of(clr, tnr)));
        when(raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(season.getId())).thenReturn(List.of(race));
        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

        var standings = standingsService.calculateStandings(season.getId());

        assertEquals(2, standings.size());
        standings.forEach(s -> {
            assertEquals(1, s.getDraws());
            assertEquals(1, s.getPoints());
        });
    }

    @Test
    void shouldSortByPointsThenPointDifference() {
        var md1 = new Matchday(season, "Spieltag 1", 1);
        var md2 = new Matchday(season, "Spieltag 2", 2);

        // TNR beats P1R 70:46, CLR beats P1R 80:40
        var race1 = createRaceWithResults(md1, tnr, p1r, 70, 46);
        var race2 = createRaceWithResults(md2, clr, p1r, 80, 40);

        season.setTeams(new java.util.ArrayList<>(List.of(tnr, p1r, clr)));
        when(raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(season.getId())).thenReturn(List.of(race1, race2));
        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

        var standings = standingsService.calculateStandings(season.getId());

        // CLR should be first (3pts, +40 diff), TNR second (3pts, +24 diff)
        assertEquals(clr.getId(), standings.get(0).getTeam().getId());
        assertEquals(tnr.getId(), standings.get(1).getTeam().getId());
        assertEquals(p1r.getId(), standings.get(2).getTeam().getId());
    }

    @Test
    void shouldExcludeTeamsWithNoGames() {
        var matchday = new Matchday(season, "Spieltag 1", 1);
        var race = createRaceWithResults(matchday, tnr, p1r, 70, 46);

        season.setTeams(new java.util.ArrayList<>(List.of(tnr, p1r, clr)));
        when(raceRepository.findByMatchdaySeasonIdAndPlayoffMatchupIsNull(season.getId())).thenReturn(List.of(race));
        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

        var standings = standingsService.calculateStandings(season.getId());

        // CLR had no games, should not be in standings
        assertEquals(2, standings.size());
        assertTrue(standings.stream().noneMatch(s -> s.getTeam().getId().equals(clr.getId())));
    }

    @Test
    void shouldCalculateAlltimeStandingsAcrossSeasons() {
        var season2 = new Season("2025");
        season2.setId(UUID.randomUUID());

        var md1 = new Matchday(season, "Spieltag 1", 1);
        var md2 = new Matchday(season2, "Spieltag 1", 1);

        // Season 2026: TNR beats P1R 70:46
        var race1 = createRaceWithResults(md1, tnr, p1r, 70, 46);
        // Season 2025: CLR beats TNR 60:50
        var race2 = createRaceWithResults(md2, clr, tnr, 60, 50);

        when(raceRepository.findByPlayoffMatchupIsNull()).thenReturn(List.of(race1, race2));
        when(teamRepository.findAll()).thenReturn(List.of(tnr, p1r, clr));

        var standings = standingsService.calculateAlltimeStandings();

        assertEquals(3, standings.size());

        var tnrStanding = standings.stream()
                .filter(s -> s.getTeam().getId().equals(tnr.getId()))
                .findFirst().orElseThrow();
        assertEquals(1, tnrStanding.getWins());
        assertEquals(1, tnrStanding.getLosses());
        // 1W + 1L = 3pts
        assertEquals(3, tnrStanding.getPoints());
    }

    @Test
    void shouldMergeSubTeamsUnderParentInAlltime() {
        var clr1 = new Team("CLR 1", "CLR 1", clr);
        clr1.setId(UUID.randomUUID());
        var clr2 = new Team("CLR 2", "CLR 2", clr);
        clr2.setId(UUID.randomUUID());

        var md = new Matchday(season, "Spieltag 1", 1);
        // CLR 1 beats TNR 70:46
        var race = createRaceWithResults(md, clr1, tnr, 70, 46);

        when(raceRepository.findByPlayoffMatchupIsNull()).thenReturn(List.of(race));
        when(teamRepository.findAll()).thenReturn(List.of(tnr, clr, clr1, clr2));

        var standings = standingsService.calculateAlltimeStandings();

        // CLR 1 should be merged under CLR
        var clrStanding = standings.stream()
                .filter(s -> s.getTeam().getId().equals(clr.getId()))
                .findFirst().orElseThrow();
        assertEquals(1, clrStanding.getWins());
        assertEquals(70, clrStanding.getPointsFor());

        // No separate CLR 1 or CLR 2 entries
        assertTrue(standings.stream().noneMatch(s -> s.getTeam().getId().equals(clr1.getId())));
        assertTrue(standings.stream().noneMatch(s -> s.getTeam().getId().equals(clr2.getId())));
    }

    @Test
    void shouldSkipIntraParentRacesInAlltime() {
        var clr1 = new Team("CLR 1", "CLR 1", clr);
        clr1.setId(UUID.randomUUID());
        var clr2 = new Team("CLR 2", "CLR 2", clr);
        clr2.setId(UUID.randomUUID());

        var md = new Matchday(season, "Spieltag 1", 1);
        // CLR 1 vs CLR 2 — same parent, should be skipped
        var race = createRaceWithResults(md, clr1, clr2, 70, 46);

        when(raceRepository.findByPlayoffMatchupIsNull()).thenReturn(List.of(race));
        when(teamRepository.findAll()).thenReturn(List.of(clr, clr1, clr2));

        var standings = standingsService.calculateAlltimeStandings();

        // CLR should have 0 games (intra-parent race skipped)
        assertTrue(standings.isEmpty());
    }

    private Race createRaceWithResults(Matchday matchday, Team home, Team away,
                                        int homeTotal, int awayTotal) {
        var race = new Race(matchday, home, away);
        race.setId(UUID.randomUUID());

        // Create 6 drivers per team with distributed points
        var raceSeason = matchday.getSeason();
        var homeDrivers = createTeamDrivers(home, raceSeason, 6);
        var awayDrivers = createTeamDrivers(away, raceSeason, 6);

        int[] homePoints = distributePoints(homeTotal, 6);
        int[] awayPoints = distributePoints(awayTotal, 6);

        var results = new java.util.ArrayList<RaceResult>();
        for (int i = 0; i < 6; i++) {
            var hr = new RaceResult();
            hr.setRace(race);
            hr.setDriver(homeDrivers.get(i));
            hr.setPosition(i + 1);
            hr.setQualiPosition(i + 1);
            hr.setPointsTotal(homePoints[i]);
            results.add(hr);

            var ar = new RaceResult();
            ar.setRace(race);
            ar.setDriver(awayDrivers.get(i));
            ar.setPosition(i + 7);
            ar.setQualiPosition(i + 7);
            ar.setPointsTotal(awayPoints[i]);
            results.add(ar);
        }

        race.setResults(results);
        return race;
    }

    private List<Driver> createTeamDrivers(Team team, Season season, int count) {
        var drivers = new java.util.ArrayList<Driver>();
        for (int i = 0; i < count; i++) {
            var driver = new Driver(team.getShortName() + "_driver" + i, team.getShortName() + " Driver " + i);
            driver.setId(UUID.randomUUID());
            var sd = new SeasonDriver(season, driver, team);
            driver.setSeasonDrivers(List.of(sd));
            drivers.add(driver);
        }
        return drivers;
    }

    private int[] distributePoints(int total, int count) {
        int[] points = new int[count];
        int remaining = total;
        for (int i = 0; i < count - 1; i++) {
            points[i] = remaining / (count - i);
            remaining -= points[i];
        }
        points[count - 1] = remaining;
        return points;
    }
}
