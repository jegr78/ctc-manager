package de.ctc.domain.service;

import de.ctc.domain.model.*;
import de.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PlayoffServiceTest {

    @Autowired private PlayoffService playoffService;
    @Autowired private PlayoffMatchupRepository playoffMatchupRepository;
    @Autowired private SeasonRepository seasonRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private DriverRepository driverRepository;
    @Autowired private SeasonDriverRepository seasonDriverRepository;
    @Autowired private RaceRepository raceRepository;
    @Autowired private MatchdayRepository matchdayRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private RaceScoringRepository raceScoringRepository;
    @Autowired private MatchScoringRepository matchScoringRepository;
    @Autowired private ScoringService scoringService;
    @Autowired private EntityManager entityManager;

    private Season season;
    private RaceScoring raceScoring;
    private List<Team> teams;

    @BeforeEach
    void setUp() {
        // Create unique scoring presets for this test run
        var uniqueSuffix = UUID.randomUUID().toString().substring(0, 4);
        raceScoring = raceScoringRepository.save(
                new RaceScoring("Test RS " + uniqueSuffix, "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
        var matchScoring = matchScoringRepository.save(
                new MatchScoring("Test MS " + uniqueSuffix, 3, 1, 0));

        season = new Season("Playoff Test " + UUID.randomUUID().toString().substring(0, 8));
        season.setActive(true);
        season.setRaceScoring(raceScoring);
        season.setMatchScoring(matchScoring);
        season = seasonRepository.save(season);

        teams = new java.util.ArrayList<>();
        String[] names = {"TA1", "TB2", "TC3", "TD4", "TE5", "TF6", "TG7", "TH8"};
        for (String name : names) {
            var team = new Team(name + " Racing", name);
            team = teamRepository.save(team);
            teams.add(team);

            for (int d = 1; d <= 2; d++) {
                var driver = new Driver(name.toLowerCase() + "_driver" + d, name + " Driver " + d);
                driver = driverRepository.save(driver);
                seasonDriverRepository.save(new SeasonDriver(season, driver, team));
            }
        }
    }

    @Nested
    class BracketCreation {

        @Test
        void shouldCreate8TeamBracket() {
            var playoff = playoffService.createPlayoff(season.getId(), "Test Playoffs", 8);

            assertNotNull(playoff.getId());
            assertEquals(3, playoff.getRounds().size());
            assertEquals("Viertelfinale", playoff.getRounds().get(0).getLabel());
            assertEquals("Halbfinale", playoff.getRounds().get(1).getLabel());
            assertEquals("Finale", playoff.getRounds().get(2).getLabel());

            assertEquals(4, playoff.getRounds().get(0).getMatchups().size());
            assertEquals(2, playoff.getRounds().get(1).getMatchups().size());
            assertEquals(1, playoff.getRounds().get(2).getMatchups().size());
        }

        @Test
        void shouldCreate4TeamBracket() {
            var playoff = playoffService.createPlayoff(season.getId(), "Small Playoffs", 4);

            assertEquals(2, playoff.getRounds().size());
            assertEquals("Halbfinale", playoff.getRounds().get(0).getLabel());
            assertEquals("Finale", playoff.getRounds().get(1).getLabel());

            assertEquals(2, playoff.getRounds().get(0).getMatchups().size());
            assertEquals(1, playoff.getRounds().get(1).getMatchups().size());
        }

        @Test
        void shouldWireNextMatchupLinks() {
            var playoff = playoffService.createPlayoff(season.getId(), "Test Playoffs", 8);

            var qf = playoff.getRounds().get(0).getMatchups();
            var sf = playoff.getRounds().get(1).getMatchups();
            var finale = playoff.getRounds().get(2).getMatchups().get(0);

            assertEquals(sf.get(0).getId(), qf.get(0).getNextMatchup().getId());
            assertEquals(sf.get(0).getId(), qf.get(1).getNextMatchup().getId());
            assertEquals(sf.get(1).getId(), qf.get(2).getNextMatchup().getId());
            assertEquals(sf.get(1).getId(), qf.get(3).getNextMatchup().getId());
            assertEquals(finale.getId(), sf.get(0).getNextMatchup().getId());
            assertEquals(finale.getId(), sf.get(1).getNextMatchup().getId());
            assertNull(finale.getNextMatchup());
        }

        @Test
        void shouldRejectInvalidTeamCount() {
            assertThrows(IllegalArgumentException.class, () ->
                    playoffService.createPlayoff(season.getId(), "Bad", 6));
        }
    }

    @Nested
    class Seeding {

        @Test
        void shouldSeedTeamsIntoMatchups() {
            var playoff = playoffService.createPlayoff(season.getId(), "Test", 4);
            var matchups = playoff.getRounds().get(0).getMatchups();

            playoffService.seedTeam(matchups.get(0).getId(), teams.get(0).getId(), 1);
            playoffService.seedTeam(matchups.get(0).getId(), teams.get(1).getId(), 2);

            var matchup = playoffMatchupRepository.findById(matchups.get(0).getId()).orElseThrow();
            assertEquals(teams.get(0).getId(), matchup.getTeam1().getId());
            assertEquals(teams.get(1).getId(), matchup.getTeam2().getId());
            assertTrue(matchup.isReady());
        }
    }

    @Nested
    class WinnerDetermination {

        @Test
        void shouldDetermineWinnerAndAdvance() {
            var playoff = playoffService.createPlayoff(season.getId(), "Test", 4);
            var sf = playoff.getRounds().get(0).getMatchups();

            playoffService.seedTeam(sf.get(0).getId(), teams.get(0).getId(), 1);
            playoffService.seedTeam(sf.get(0).getId(), teams.get(1).getId(), 2);

            var matchday = matchdayRepository.save(new Matchday(season, "HF Hinspiel", 1));
            var matchup = playoffMatchupRepository.findById(sf.get(0).getId()).orElseThrow();

            var race = new Race();
            race.setMatchday(matchday);
            race.setPlayoffMatchup(matchup);
            race = raceRepository.save(race);

            // Add results — team 0 drivers score more (positions 1,2 vs 3,4)
            var team0Drivers = seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), teams.get(0).getId());
            var team1Drivers = seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), teams.get(1).getId());

            int pos = 1;
            for (var sd : team0Drivers) {
                var rr = new RaceResult(race, sd.getDriver(), pos, pos, false);
                scoringService.calculatePoints(rr, raceScoring);
                race.getResults().add(rr);
                pos++;
            }
            for (var sd : team1Drivers) {
                var rr = new RaceResult(race, sd.getDriver(), pos, pos, false);
                scoringService.calculatePoints(rr, raceScoring);
                race.getResults().add(rr);
                pos++;
            }
            raceRepository.save(race);
            entityManager.flush();
            entityManager.clear();

            // team 0: 20+17=37, team 1: 14+12=26
            playoffService.determineWinner(sf.get(0).getId());

            var resolved = playoffMatchupRepository.findById(sf.get(0).getId()).orElseThrow();
            assertNotNull(resolved.getWinner(), "Winner should be set");
            assertTrue(resolved.isComplete());
            // Team 0: Pos1(20)+Quali1(3) + Pos2(17)+Quali2(2) = 42
            // Team 1: Pos3(14)+Quali3(1) + Pos4(12)+Quali4(0) = 27
            assertEquals(42, resolved.getHomeScore());
            assertEquals(27, resolved.getAwayScore());

            // Winner advanced to finale
            var finale = playoff.getRounds().get(1).getMatchups().get(0);
            var finaleRefreshed = playoffMatchupRepository.findById(finale.getId()).orElseThrow();
            assertEquals(resolved.getWinner().getId(), finaleRefreshed.getTeam1().getId());
        }
    }

    @Nested
    class TieBreaking {

        @Test
        void shouldThrowOnTie() {
            var playoff = playoffService.createPlayoff(season.getId(), "Tie Test", 4);
            var sf = playoff.getRounds().get(0).getMatchups();

            playoffService.seedTeam(sf.get(0).getId(), teams.get(0).getId(), 1);
            playoffService.seedTeam(sf.get(0).getId(), teams.get(1).getId(), 2);

            var matchday = matchdayRepository.save(new Matchday(season, "HF", 1));
            var matchup = playoffMatchupRepository.findById(sf.get(0).getId()).orElseThrow();

            var race = new Race();
            race.setMatchday(matchday);
            race.setPlayoffMatchup(matchup);
            race = raceRepository.save(race);

            var team0Drivers = seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), teams.get(0).getId());
            var team1Drivers = seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), teams.get(1).getId());

            int pos = 1;
            for (var sd : team0Drivers) {
                var rr = new RaceResult(race, sd.getDriver(), pos, pos, false);
                scoringService.calculatePoints(rr, raceScoring);
                rr.setPointsTotal(25);
                race.getResults().add(rr);
                pos++;
            }
            for (var sd : team1Drivers) {
                var rr = new RaceResult(race, sd.getDriver(), pos, pos, false);
                scoringService.calculatePoints(rr, raceScoring);
                rr.setPointsTotal(25);
                race.getResults().add(rr);
                pos++;
            }
            raceRepository.save(race);
            entityManager.flush();
            entityManager.clear();

            assertThrows(IllegalStateException.class, () ->
                    playoffService.determineWinner(sf.get(0).getId()));
        }
    }

    @Nested
    class BracketView {

        @Test
        void shouldReturnBracketView() {
            var playoff = playoffService.createPlayoff(season.getId(), "View Test", 4);
            var view = playoffService.getBracketView(playoff.getId());

            assertEquals("View Test", view.getName());
            assertEquals(2, view.getRounds().size());
            assertEquals("Halbfinale", view.getRounds().get(0).getLabel());
            assertEquals(2, view.getRounds().get(0).getMatchups().size());
        }
    }
}
