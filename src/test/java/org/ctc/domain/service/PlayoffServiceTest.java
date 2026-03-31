package org.ctc.domain.service;

import org.ctc.admin.dto.SeedForm;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
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

        season = new Season("Playoff Test " + UUID.randomUUID().toString().substring(0, 8), 2026, 1);
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

    @Nested
    class SetRoundLegs {

        @Test
        void shouldUpdateBestOfLegs() {
            var playoff = playoffService.createPlayoff(season.getId(), "Legs Test", 4);
            var roundId = playoff.getRounds().get(0).getId();

            var updated = playoffService.setRoundLegs(roundId, 3);

            assertEquals(3, updated.getBestOfLegs());
            assertEquals("Halbfinale", updated.getLabel());
        }

        @Test
        void shouldRejectUnknownRound() {
            assertThrows(IllegalArgumentException.class, () ->
                    playoffService.setRoundLegs(UUID.randomUUID(), 3));
        }
    }

    @Nested
    class AddRaceToMatchup {

        @Test
        void shouldCreateRaceAndMatchday() {
            var playoff = playoffService.createPlayoff(season.getId(), "Race Test", 4);
            var matchup = playoff.getRounds().get(0).getMatchups().get(0);

            playoffService.seedTeam(matchup.getId(), teams.get(0).getId(), 1);
            playoffService.seedTeam(matchup.getId(), teams.get(1).getId(), 2);

            var race = playoffService.addRaceToMatchup(matchup.getId(), null, null, null);

            assertNotNull(race.getId());
            assertNotNull(race.getMatchday());
            assertEquals(matchup.getId(), race.getPlayoffMatchup().getId());
            assertTrue(race.getMatchday().getLabel().contains("Leg 1"));
        }

        @Test
        void shouldRejectWhenTeamsNotSet() {
            var playoff = playoffService.createPlayoff(season.getId(), "NoTeams Test", 4);
            var matchup = playoff.getRounds().get(0).getMatchups().get(0);

            assertThrows(IllegalStateException.class, () ->
                    playoffService.addRaceToMatchup(matchup.getId(), null, null, null));
        }

        @Test
        void shouldRejectWhenMaxLegsReached() {
            var playoff = playoffService.createPlayoff(season.getId(), "MaxLegs Test", 4);
            var matchup = playoff.getRounds().get(0).getMatchups().get(0);

            playoffService.seedTeam(matchup.getId(), teams.get(0).getId(), 1);
            playoffService.seedTeam(matchup.getId(), teams.get(1).getId(), 2);

            // Default bestOfLegs is 1, so adding one race fills it
            playoffService.addRaceToMatchup(matchup.getId(), null, null, null);

            assertThrows(IllegalStateException.class, () ->
                    playoffService.addRaceToMatchup(matchup.getId(), null, null, null));
        }

        @Test
        void shouldAllowMultipleLegsWhenBestOfIsHigher() {
            var playoff = playoffService.createPlayoff(season.getId(), "MultiLeg Test", 4);
            var round = playoff.getRounds().get(0);
            playoffService.setRoundLegs(round.getId(), 3);
            var matchup = round.getMatchups().get(0);

            playoffService.seedTeam(matchup.getId(), teams.get(0).getId(), 1);
            playoffService.seedTeam(matchup.getId(), teams.get(1).getId(), 2);

            var race1 = playoffService.addRaceToMatchup(matchup.getId(), null, null, null);
            var race2 = playoffService.addRaceToMatchup(matchup.getId(), null, null, null);

            assertNotEquals(race1.getId(), race2.getId());
            assertTrue(race1.getMatchday().getLabel().contains("Leg 1"));
            assertTrue(race2.getMatchday().getLabel().contains("Leg 2"));
        }
    }

    @Nested
    class GetSeedingData {

        @Test
        void shouldReturnCorrectTeamsAndRound() {
            // Add teams to the season first
            for (var team : teams) {
                season.addTeam(team);
            }
            seasonRepository.save(season);

            var playoff = playoffService.createPlayoff(season.getId(), "Seed Data Test", 4);
            var data = playoffService.getSeedingData(playoff.getId());

            assertNotNull(data.playoff());
            assertNotNull(data.firstRound());
            assertNotNull(data.bracketView());
            assertFalse(data.teams().isEmpty());
            assertTrue(data.seededTeamIds().isEmpty());
        }

        @Test
        void shouldTrackSeededTeams() {
            for (var team : teams) {
                season.addTeam(team);
            }
            seasonRepository.save(season);

            var playoff = playoffService.createPlayoff(season.getId(), "Seeded Track Test", 4);
            var matchup = playoff.getRounds().get(0).getMatchups().get(0);
            playoffService.seedTeam(matchup.getId(), teams.get(0).getId(), 1);

            var data = playoffService.getSeedingData(playoff.getId());

            assertTrue(data.seededTeamIds().contains(teams.get(0).getId()));
            assertEquals(1, data.seededTeamIds().size());
        }
    }

    @Nested
    class SaveSeed {

        @Test
        void shouldSeedAllEntriesFromForm() {
            var playoff = playoffService.createPlayoff(season.getId(), "Form Seed Test", 4);
            var matchups = playoff.getRounds().get(0).getMatchups();

            var form = new SeedForm();
            form.setPlayoffId(playoff.getId());

            var entry1 = new SeedForm.SeedEntry();
            entry1.setMatchupId(matchups.get(0).getId());
            entry1.setTeamId(teams.get(0).getId());
            entry1.setSlot(1);

            var entry2 = new SeedForm.SeedEntry();
            entry2.setMatchupId(matchups.get(0).getId());
            entry2.setTeamId(teams.get(1).getId());
            entry2.setSlot(2);

            form.getSeeds().add(entry1);
            form.getSeeds().add(entry2);

            playoffService.saveSeed(playoff.getId(), form);

            var matchup = playoffMatchupRepository.findById(matchups.get(0).getId()).orElseThrow();
            assertEquals(teams.get(0).getId(), matchup.getTeam1().getId());
            assertEquals(teams.get(1).getId(), matchup.getTeam2().getId());
        }

        @Test
        void shouldSkipNullTeamIds() {
            var playoff = playoffService.createPlayoff(season.getId(), "Null Seed Test", 4);
            var matchups = playoff.getRounds().get(0).getMatchups();

            var form = new SeedForm();
            form.setPlayoffId(playoff.getId());

            var entry = new SeedForm.SeedEntry();
            entry.setMatchupId(matchups.get(0).getId());
            entry.setTeamId(null);
            entry.setSlot(1);
            form.getSeeds().add(entry);

            playoffService.saveSeed(playoff.getId(), form);

            var matchup = playoffMatchupRepository.findById(matchups.get(0).getId()).orElseThrow();
            assertNull(matchup.getTeam1());
        }
    }

    @Nested
    class MatchupDetail {

        @Test
        void shouldReturnMatchupWithLegs() {
            var playoff = playoffService.createPlayoff(season.getId(), "Detail Test", 4);
            var matchup = playoff.getRounds().get(0).getMatchups().get(0);

            var data = playoffService.getMatchupDetail(matchup.getId());

            assertEquals(matchup.getId(), data.matchup().getId());
            assertNotNull(data.playoff());
            assertTrue(data.legs().isEmpty());
        }
    }

    @Nested
    class PlayoffListData {

        @Test
        void shouldReturnAllSeasonsWithNoSelection() {
            var data = playoffService.getPlayoffListData(null);

            assertNotNull(data.allSeasons());
            assertFalse(data.allSeasons().isEmpty());
        }

        @Test
        void shouldReturnPlayoffForSelectedSeason() {
            playoffService.createPlayoff(season.getId(), "List Test", 4);

            var data = playoffService.getPlayoffListData(season.getId());

            assertNotNull(data.playoff());
            assertNotNull(data.bracketView());
            assertEquals(season.getId(), data.selectedSeasonId());
        }

        @Test
        void shouldReturnNullPlayoffWhenNoneExists() {
            var data = playoffService.getPlayoffListData(season.getId());

            assertNull(data.playoff());
            assertNull(data.bracketView());
        }
    }

    @Nested
    class SeasonIdLookups {

        @Test
        void shouldReturnSeasonIdForPlayoff() {
            var playoff = playoffService.createPlayoff(season.getId(), "Lookup Test", 4);
            assertEquals(season.getId(), playoffService.getSeasonIdForPlayoff(playoff.getId()));
        }

        @Test
        void shouldReturnSeasonIdForMatchup() {
            var playoff = playoffService.createPlayoff(season.getId(), "Matchup Lookup Test", 4);
            var matchupId = playoff.getRounds().get(0).getMatchups().get(0).getId();
            assertEquals(season.getId(), playoffService.getSeasonIdForMatchup(matchupId));
        }

        @Test
        void shouldReturnSeasonIdForRound() {
            var playoff = playoffService.createPlayoff(season.getId(), "Round Lookup Test", 4);
            var roundId = playoff.getRounds().get(0).getId();
            assertEquals(season.getId(), playoffService.getSeasonIdForRound(roundId));
        }
    }
}
