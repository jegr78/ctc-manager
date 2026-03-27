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
    @Autowired private ScoringService scoringService;
    @Autowired private EntityManager entityManager;

    private Season season;
    private List<Team> teams;

    @BeforeEach
    void setUp() {
        season = new Season("Playoff Test " + UUID.randomUUID().toString().substring(0, 8));
        season.setActive(true);
        season = seasonRepository.save(season);

        // Create 8 teams with 2 drivers each
        teams = new java.util.ArrayList<>();
        String[] names = {"TNR", "P1R", "CLR", "AHR", "VRX", "DTM", "NFS", "GTR"};
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
            var playoff = playoffService.createPlayoff(season.getId(), "Test Playoffs", 2, 8);

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
            var playoff = playoffService.createPlayoff(season.getId(), "Small Playoffs", 1, 4);

            assertEquals(2, playoff.getRounds().size());
            assertEquals("Halbfinale", playoff.getRounds().get(0).getLabel());
            assertEquals("Finale", playoff.getRounds().get(1).getLabel());

            assertEquals(2, playoff.getRounds().get(0).getMatchups().size());
            assertEquals(1, playoff.getRounds().get(1).getMatchups().size());
        }

        @Test
        void shouldWireNextMatchupLinks() {
            var playoff = playoffService.createPlayoff(season.getId(), "Test Playoffs", 2, 8);

            var qf = playoff.getRounds().get(0).getMatchups();
            var sf = playoff.getRounds().get(1).getMatchups();
            var finale = playoff.getRounds().get(2).getMatchups().get(0);

            // QF 0 and QF 1 should feed into SF 0
            assertEquals(sf.get(0).getId(), qf.get(0).getNextMatchup().getId());
            assertEquals(sf.get(0).getId(), qf.get(1).getNextMatchup().getId());

            // QF 2 and QF 3 should feed into SF 1
            assertEquals(sf.get(1).getId(), qf.get(2).getNextMatchup().getId());
            assertEquals(sf.get(1).getId(), qf.get(3).getNextMatchup().getId());

            // SF 0 and SF 1 should feed into Finale
            assertEquals(finale.getId(), sf.get(0).getNextMatchup().getId());
            assertEquals(finale.getId(), sf.get(1).getNextMatchup().getId());

            // Finale has no next
            assertNull(finale.getNextMatchup());
        }

        @Test
        void shouldRejectInvalidTeamCount() {
            assertThrows(IllegalArgumentException.class, () ->
                    playoffService.createPlayoff(season.getId(), "Bad", 2, 6));
        }
    }

    @Nested
    class Seeding {

        @Test
        void shouldSeedTeamsIntoMatchups() {
            var playoff = playoffService.createPlayoff(season.getId(), "Test", 2, 4);
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
            var playoff = playoffService.createPlayoff(season.getId(), "Test", 2, 4);
            var sf = playoff.getRounds().get(0).getMatchups();

            // Seed SF matchup 0
            playoffService.seedTeam(sf.get(0).getId(), teams.get(0).getId(), 1);
            playoffService.seedTeam(sf.get(0).getId(), teams.get(1).getId(), 2);

            // Create a matchday and race for this matchup
            var matchday = new Matchday(season, "HF Hinspiel", 1);
            matchday = matchdayRepository.save(matchday);

            var race = new Race(matchday, teams.get(0), teams.get(1));
            race.setPlayoffMatchup(playoffMatchupRepository.findById(sf.get(0).getId()).orElseThrow());
            race = raceRepository.save(race);

            // Add results — team 0 drivers score more
            var team0Drivers = seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), teams.get(0).getId());
            var team1Drivers = seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), teams.get(1).getId());

            var results = new java.util.ArrayList<RaceResult>();
            int pos = 1;
            for (var sd : team0Drivers) {
                var rr = new RaceResult(race, sd.getDriver(), pos, pos, false);
                scoringService.calculatePoints(rr);
                results.add(rr);
                pos++;
            }
            for (var sd : team1Drivers) {
                var rr = new RaceResult(race, sd.getDriver(), pos, pos, false);
                scoringService.calculatePoints(rr);
                results.add(rr);
                pos++;
            }
            race.getResults().addAll(results);
            raceRepository.save(race);

            // Determine winner — team 0 has positions 1+2, team 1 has positions 3+4
            // So team 0 scores more (20+17=37 vs 14+12=26)
            playoffService.determineWinner(sf.get(0).getId());

            var matchup = playoffMatchupRepository.findById(sf.get(0).getId()).orElseThrow();
            assertNotNull(matchup.getWinner(), "Winner should be set");
            assertTrue(matchup.isComplete());

            // Winner should be advanced to the finale (next matchup)
            var finale = playoff.getRounds().get(1).getMatchups().get(0);
            var finaleRefreshed = playoffMatchupRepository.findById(finale.getId()).orElseThrow();
            // bracketPosition 0 is even → winner goes to team1
            assertEquals(matchup.getWinner().getId(), finaleRefreshed.getTeam1().getId(),
                    "Winner should be advanced to finale as team1");
        }
    }

    @Nested
    class TieBreaking {

        @Test
        void shouldThrowOnTie() {
            var playoff = playoffService.createPlayoff(season.getId(), "Tie Test", 1, 4);
            var sf = playoff.getRounds().get(0).getMatchups();

            playoffService.seedTeam(sf.get(0).getId(), teams.get(0).getId(), 1);
            playoffService.seedTeam(sf.get(0).getId(), teams.get(1).getId(), 2);

            var matchday = matchdayRepository.save(new Matchday(season, "HF", 1));
            var race = new Race(matchday, teams.get(0), teams.get(1));
            race.setPlayoffMatchup(playoffMatchupRepository.findById(sf.get(0).getId()).orElseThrow());
            race = raceRepository.save(race);

            // Give both teams identical total scores
            var team0Drivers = seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), teams.get(0).getId());
            var team1Drivers = seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), teams.get(1).getId());

            // team0 drivers get positions 1,4 → 20+12=32
            // team1 drivers get positions 2,3 → 17+14=31 ... not equal
            // Instead: set pointsTotal explicitly to force a tie
            int pos = 1;
            for (var sd : team0Drivers) {
                var rr = new RaceResult(race, sd.getDriver(), pos, pos, false);
                scoringService.calculatePoints(rr);
                rr.setPointsTotal(25); // force equal total
                race.getResults().add(rr);
                pos++;
            }
            for (var sd : team1Drivers) {
                var rr = new RaceResult(race, sd.getDriver(), pos, pos, false);
                scoringService.calculatePoints(rr);
                rr.setPointsTotal(25); // force equal total
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
            var playoff = playoffService.createPlayoff(season.getId(), "View Test", 2, 4);
            var view = playoffService.getBracketView(playoff.getId());

            assertEquals("View Test", view.getName());
            assertEquals(2, view.getRounds().size());
            assertEquals("Halbfinale", view.getRounds().get(0).getLabel());
            assertEquals(2, view.getRounds().get(0).getMatchups().size());
        }
    }
}
