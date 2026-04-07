package org.ctc.domain.service;

import org.ctc.domain.exception.EntityNotFoundException;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PlayoffServiceTest {

    @Autowired private PlayoffService playoffService;
    @Autowired private PlayoffBracketViewService playoffBracketViewService;
    @Autowired private PlayoffSeedingService playoffSeedingService;
    @Autowired private PlayoffMatchupRepository playoffMatchupRepository;
    @Autowired private PlayoffSeedRepository playoffSeedRepository;
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
        void whenCreate8TeamPlayoff_thenBracketHasThreeRoundsWithCorrectMatchups() {
            // when
            var playoff = playoffService.createPlayoff(season.getId(), "Test Playoffs", 8);

            // then
            assertNotNull(playoff.getId());
            assertEquals(3, playoff.getRounds().size());
            assertEquals("Quarterfinal", playoff.getRounds().get(0).getLabel());
            assertEquals("Semifinal", playoff.getRounds().get(1).getLabel());
            assertEquals("Final", playoff.getRounds().get(2).getLabel());

            assertEquals(4, playoff.getRounds().get(0).getMatchups().size());
            assertEquals(2, playoff.getRounds().get(1).getMatchups().size());
            assertEquals(1, playoff.getRounds().get(2).getMatchups().size());
        }

        @Test
        void whenCreate2TeamPlayoff_thenBracketHasOneRoundWithOneMatchup() {
            // when
            var playoff = playoffService.createPlayoff(season.getId(), "Final Only", 2);

            // then
            assertEquals(1, playoff.getRounds().size());
            assertEquals("Final", playoff.getRounds().get(0).getLabel());
            assertEquals(1, playoff.getRounds().get(0).getMatchups().size());
            assertNull(playoff.getRounds().get(0).getMatchups().get(0).getNextMatchup());
        }

        @Test
        void whenCreate4TeamPlayoff_thenBracketHasTwoRoundsWithCorrectMatchups() {
            // when
            var playoff = playoffService.createPlayoff(season.getId(), "Small Playoffs", 4);

            // then
            assertEquals(2, playoff.getRounds().size());
            assertEquals("Semifinal", playoff.getRounds().get(0).getLabel());
            assertEquals("Final", playoff.getRounds().get(1).getLabel());

            assertEquals(2, playoff.getRounds().get(0).getMatchups().size());
            assertEquals(1, playoff.getRounds().get(1).getMatchups().size());
        }

        @Test
        void whenCreate8TeamPlayoff_thenNextMatchupLinksWiredCorrectly() {
            // when
            var playoff = playoffService.createPlayoff(season.getId(), "Test Playoffs", 8);

            // then
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
        void givenInvalidTeamCount_whenCreatePlayoff_thenThrowsException() {
            // when / then
            assertThrows(IllegalArgumentException.class, () ->
                    playoffService.createPlayoff(season.getId(), "Bad", 6));
        }
    }

    @Nested
    class Seeding {

        @Test
        void givenMatchup_whenSeedTeams_thenTeamsAssignedAndMatchupReady() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Test", 4);
            var matchups = playoff.getRounds().get(0).getMatchups();

            // when
            playoffSeedingService.seedTeam(matchups.get(0).getId(), teams.get(0).getId(), 1);
            playoffSeedingService.seedTeam(matchups.get(0).getId(), teams.get(1).getId(), 2);

            // then
            var matchup = playoffMatchupRepository.findById(matchups.get(0).getId()).orElseThrow();
            assertEquals(teams.get(0).getId(), matchup.getTeam1().getId());
            assertEquals(teams.get(1).getId(), matchup.getTeam2().getId());
            assertTrue(matchup.isReady());
        }
    }

    @Nested
    class WinnerDetermination {

        @Test
        void givenRaceResultsWithClearWinner_whenDetermineWinner_thenWinnerSetAndAdvanced() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Test", 4);
            var sf = playoff.getRounds().get(0).getMatchups();

            playoffSeedingService.seedTeam(sf.get(0).getId(), teams.get(0).getId(), 1);
            playoffSeedingService.seedTeam(sf.get(0).getId(), teams.get(1).getId(), 2);

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

            // when
            // team 0: 20+17=37, team 1: 14+12=26
            playoffService.determineWinner(sf.get(0).getId());

            // then
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
        void givenTiedScores_whenDetermineWinner_thenThrowsException() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Tie Test", 4);
            var sf = playoff.getRounds().get(0).getMatchups();

            playoffSeedingService.seedTeam(sf.get(0).getId(), teams.get(0).getId(), 1);
            playoffSeedingService.seedTeam(sf.get(0).getId(), teams.get(1).getId(), 2);

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

            // when / then
            assertThrows(IllegalStateException.class, () ->
                    playoffService.determineWinner(sf.get(0).getId()));
        }
    }

    @Nested
    class BracketView {

        @Test
        void givenExistingPlayoff_whenGetBracketView_thenReturnsCorrectView() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "View Test", 4);

            // when
            var view = playoffBracketViewService.getBracketView(playoff.getId());

            // then
            assertEquals("View Test", view.getName());
            assertEquals(2, view.getRounds().size());
            assertEquals("Semifinal", view.getRounds().get(0).getLabel());
            assertEquals(2, view.getRounds().get(0).getMatchups().size());
        }

        @Test
        void givenSeededPlayoff_whenGetBracketView_thenMatchupsContainSeedNumbers() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Seed View Test", 4);
            Map<UUID, Integer> teamSeeds = new LinkedHashMap<>();
            teamSeeds.put(teams.get(0).getId(), 1);
            teamSeeds.put(teams.get(1).getId(), 2);
            teamSeeds.put(teams.get(2).getId(), 3);
            teamSeeds.put(teams.get(3).getId(), 4);
            playoffSeedingService.saveSeedNumbers(playoff.getId(), teamSeeds);
            playoffSeedingService.autoSeedBracket(playoff.getId());
            entityManager.flush();
            entityManager.clear();

            // when
            var view = playoffBracketViewService.getBracketView(playoff.getId());

            // then — Matchup 0: Seed 1 vs Seed 4, Matchup 1: Seed 2 vs Seed 3
            var matchups = view.getRounds().get(0).getMatchups();
            assertEquals(Integer.valueOf(1), matchups.get(0).getTeam1Seed());
            assertEquals(Integer.valueOf(4), matchups.get(0).getTeam2Seed());
            assertEquals(Integer.valueOf(2), matchups.get(1).getTeam1Seed());
            assertEquals(Integer.valueOf(3), matchups.get(1).getTeam2Seed());
        }

        @Test
        void givenUnseededPlayoff_whenGetBracketView_thenSeedsAreNull() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "No Seed View Test", 4);

            // when
            var view = playoffBracketViewService.getBracketView(playoff.getId());

            // then
            var matchups = view.getRounds().get(0).getMatchups();
            assertNull(matchups.get(0).getTeam1Seed());
            assertNull(matchups.get(0).getTeam2Seed());
            assertNull(matchups.get(1).getTeam1Seed());
            assertNull(matchups.get(1).getTeam2Seed());
        }

        @Test
        void givenPartiallyFilledBracket_whenGetBracketView_thenTBDSlotsHaveNullSeed() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Partial Seed Test", 4);
            Map<UUID, Integer> teamSeeds = new LinkedHashMap<>();
            teamSeeds.put(teams.get(0).getId(), 1);
            teamSeeds.put(teams.get(1).getId(), 2);
            teamSeeds.put(teams.get(2).getId(), 3);
            teamSeeds.put(teams.get(3).getId(), 4);
            playoffSeedingService.saveSeedNumbers(playoff.getId(), teamSeeds);
            playoffSeedingService.autoSeedBracket(playoff.getId());
            entityManager.flush();
            entityManager.clear();

            // when — Finale has no teams yet (TBD)
            var view = playoffBracketViewService.getBracketView(playoff.getId());

            // then
            var finaleMatchups = view.getRounds().get(1).getMatchups();
            assertNull(finaleMatchups.get(0).getTeam1Seed());
            assertNull(finaleMatchups.get(0).getTeam2Seed());
        }
    }

    @Nested
    class SetRoundLegs {

        @Test
        void givenRound_whenSetRoundLegs_thenBestOfLegsUpdated() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Legs Test", 4);
            var roundId = playoff.getRounds().get(0).getId();

            // when
            var updated = playoffService.setRoundLegs(roundId, 3);

            // then
            assertEquals(3, updated.getBestOfLegs());
            assertEquals("Semifinal", updated.getLabel());
        }

        @Test
        void givenUnknownRoundId_whenSetRoundLegs_thenThrowsException() {
            // when / then
            assertThrows(EntityNotFoundException.class, () ->
                    playoffService.setRoundLegs(UUID.randomUUID(), 3));
        }
    }

    @Nested
    class AddRaceToMatchup {

        @Test
        void givenSeededMatchup_whenAddRaceToMatchup_thenRaceAndMatchdayCreated() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Race Test", 4);
            var matchup = playoff.getRounds().get(0).getMatchups().get(0);

            playoffSeedingService.seedTeam(matchup.getId(), teams.get(0).getId(), 1);
            playoffSeedingService.seedTeam(matchup.getId(), teams.get(1).getId(), 2);

            // when
            var race = playoffService.addRaceToMatchup(matchup.getId(), null, null, null);

            // then
            assertNotNull(race.getId());
            assertNotNull(race.getMatchday());
            assertEquals(matchup.getId(), race.getPlayoffMatchup().getId());
            assertTrue(race.getMatchday().getLabel().contains("Leg 1"));
        }

        @Test
        void givenUnseededMatchup_whenAddRaceToMatchup_thenThrowsException() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "NoTeams Test", 4);
            var matchup = playoff.getRounds().get(0).getMatchups().get(0);

            // when / then
            assertThrows(IllegalStateException.class, () ->
                    playoffService.addRaceToMatchup(matchup.getId(), null, null, null));
        }

        @Test
        void givenMaxLegsReached_whenAddRaceToMatchup_thenThrowsException() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "MaxLegs Test", 4);
            var matchup = playoff.getRounds().get(0).getMatchups().get(0);

            playoffSeedingService.seedTeam(matchup.getId(), teams.get(0).getId(), 1);
            playoffSeedingService.seedTeam(matchup.getId(), teams.get(1).getId(), 2);

            // Default bestOfLegs is 1, so adding one race fills it
            playoffService.addRaceToMatchup(matchup.getId(), null, null, null);

            // when / then
            assertThrows(IllegalStateException.class, () ->
                    playoffService.addRaceToMatchup(matchup.getId(), null, null, null));
        }

        @Test
        void givenHigherBestOfLegs_whenAddMultipleRaces_thenEachLegLabeled() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "MultiLeg Test", 4);
            var round = playoff.getRounds().get(0);
            playoffService.setRoundLegs(round.getId(), 3);
            var matchup = round.getMatchups().get(0);

            playoffSeedingService.seedTeam(matchup.getId(), teams.get(0).getId(), 1);
            playoffSeedingService.seedTeam(matchup.getId(), teams.get(1).getId(), 2);

            // when
            var race1 = playoffService.addRaceToMatchup(matchup.getId(), null, null, null);
            var race2 = playoffService.addRaceToMatchup(matchup.getId(), null, null, null);

            // then
            assertNotEquals(race1.getId(), race2.getId());
            assertTrue(race1.getMatchday().getLabel().contains("Leg 1"));
            assertTrue(race2.getMatchday().getLabel().contains("Leg 2"));
        }
    }

    @Nested
    class GetSeedingData {

        @Test
        void givenSeasonWithTeams_whenGetSeedingData_thenReturnsTeamsAndEmptySeededIds() {
            // given
            // Add teams to the season first
            for (var team : teams) {
                season.addTeam(team);
            }
            seasonRepository.save(season);

            var playoff = playoffService.createPlayoff(season.getId(), "Seed Data Test", 4);

            // when
            var data = playoffSeedingService.getSeedingData(playoff.getId());

            // then
            assertNotNull(data.playoff());
            assertNotNull(data.firstRound());
            assertNotNull(data.bracketView());
            assertFalse(data.teams().isEmpty());
            assertTrue(data.seededTeamIds().isEmpty());
        }

        @Test
        void givenOneTeamSeeded_whenGetSeedingData_thenSeededTeamIdTracked() {
            // given
            for (var team : teams) {
                season.addTeam(team);
            }
            seasonRepository.save(season);

            var playoff = playoffService.createPlayoff(season.getId(), "Seeded Track Test", 4);
            var matchup = playoff.getRounds().get(0).getMatchups().get(0);
            playoffSeedingService.seedTeam(matchup.getId(), teams.get(0).getId(), 1);

            // when
            var data = playoffSeedingService.getSeedingData(playoff.getId());

            // then
            assertTrue(data.seededTeamIds().contains(teams.get(0).getId()));
            assertEquals(1, data.seededTeamIds().size());
        }
    }

    @Nested
    class SaveSeed {

        @Test
        void givenSeedEntries_whenSaveSeed_thenAllEntriesSeeded() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Form Seed Test", 4);
            var matchups = playoff.getRounds().get(0).getMatchups();

            var seeds = List.of(
                    new PlayoffSeedingService.SeedEntry(matchups.get(0).getId(), 1, teams.get(0).getId(), null),
                    new PlayoffSeedingService.SeedEntry(matchups.get(0).getId(), 2, teams.get(1).getId(), null)
            );

            // when
            playoffSeedingService.saveSeed(playoff.getId(), seeds);

            // then
            var matchup = playoffMatchupRepository.findById(matchups.get(0).getId()).orElseThrow();
            assertEquals(teams.get(0).getId(), matchup.getTeam1().getId());
            assertEquals(teams.get(1).getId(), matchup.getTeam2().getId());
        }

        @Test
        void givenSeedEntryWithNullTeamId_whenSaveSeed_thenEntrySkipped() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Null Seed Test", 4);
            var matchups = playoff.getRounds().get(0).getMatchups();

            var seeds = List.of(
                    new PlayoffSeedingService.SeedEntry(matchups.get(0).getId(), 1, null, null)
            );

            // when
            playoffSeedingService.saveSeed(playoff.getId(), seeds);

            // then
            var matchup = playoffMatchupRepository.findById(matchups.get(0).getId()).orElseThrow();
            assertNull(matchup.getTeam1());
        }
    }

    @Nested
    class MatchupDetail {

        @Test
        void givenMatchup_whenGetMatchupDetail_thenReturnsMatchupWithLegs() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Detail Test", 4);
            var matchup = playoff.getRounds().get(0).getMatchups().get(0);

            // when
            var data = playoffService.getMatchupDetail(matchup.getId());

            // then
            assertEquals(matchup.getId(), data.matchup().getId());
            assertNotNull(data.playoff());
            assertTrue(data.legs().isEmpty());
        }
    }

    @Nested
    class PlayoffListData {

        @Test
        void givenNoSeasonSelected_whenGetPlayoffListData_thenReturnsAllSeasons() {
            // when
            var data = playoffService.getPlayoffListData(null);

            // then
            assertNotNull(data.allSeasons());
            assertFalse(data.allSeasons().isEmpty());
        }

        @Test
        void givenSeasonWithPlayoff_whenGetPlayoffListData_thenReturnsPlayoffForSeason() {
            // given
            playoffService.createPlayoff(season.getId(), "List Test", 4);

            // when
            var data = playoffService.getPlayoffListData(season.getId());

            // then
            assertNotNull(data.playoff());
            assertNotNull(data.bracketView());
            assertEquals(season.getId(), data.selectedSeasonId());
        }

        @Test
        void givenSeasonWithoutPlayoff_whenGetPlayoffListData_thenReturnsNullPlayoff() {
            // when
            var data = playoffService.getPlayoffListData(season.getId());

            // then
            assertNull(data.playoff());
            assertNull(data.bracketView());
        }
    }

    @Nested
    class SeedManagement {

        @Test
        void givenPlayoffWithTeams_whenSaveSeedNumbers_thenSeedsArePersisted() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Seed Test", 4);
            Map<UUID, Integer> teamSeeds = new LinkedHashMap<>();
            teamSeeds.put(teams.get(0).getId(), 1);
            teamSeeds.put(teams.get(1).getId(), 2);
            teamSeeds.put(teams.get(2).getId(), 3);
            teamSeeds.put(teams.get(3).getId(), 4);

            // when
            playoffSeedingService.saveSeedNumbers(playoff.getId(), teamSeeds);
            entityManager.flush();

            // then
            var seeds = playoffSeedRepository.findByPlayoffId(playoff.getId());
            assertEquals(4, seeds.size());
            var seedForTeam0 = seeds.stream()
                    .filter(s -> s.getTeam().getId().equals(teams.get(0).getId()))
                    .findFirst().orElseThrow();
            assertEquals(1, seedForTeam0.getSeed());
        }

        @Test
        void givenExistingSeeds_whenSaveSeedNumbers_thenOldSeedsReplaced() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Replace Seed Test", 4);
            Map<UUID, Integer> original = new LinkedHashMap<>();
            original.put(teams.get(0).getId(), 1);
            original.put(teams.get(1).getId(), 2);
            playoffSeedingService.saveSeedNumbers(playoff.getId(), original);
            entityManager.flush();

            // when — swap seeds 1 and 2
            Map<UUID, Integer> swapped = new LinkedHashMap<>();
            swapped.put(teams.get(0).getId(), 2);
            swapped.put(teams.get(1).getId(), 1);
            playoffSeedingService.saveSeedNumbers(playoff.getId(), swapped);
            entityManager.flush();

            // then
            var seeds = playoffSeedRepository.findByPlayoffId(playoff.getId());
            assertEquals(2, seeds.size());
            var seedForTeam0 = seeds.stream()
                    .filter(s -> s.getTeam().getId().equals(teams.get(0).getId()))
                    .findFirst().orElseThrow();
            assertEquals(2, seedForTeam0.getSeed());
            var seedForTeam1 = seeds.stream()
                    .filter(s -> s.getTeam().getId().equals(teams.get(1).getId()))
                    .findFirst().orElseThrow();
            assertEquals(1, seedForTeam1.getSeed());
        }

        @Test
        void givenNoSeedNumbers_whenAutoSeedBracket_thenThrowsIllegalState() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Test Playoffs", 4);
            entityManager.flush();
            entityManager.clear();

            // when / then
            assertThatThrownBy(() -> playoffSeedingService.autoSeedBracket(playoff.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No seed numbers assigned");
        }

        @Test
        void givenSeedNumbers_whenAutoSeedBracket_thenMatchupsPopulatedCorrectly() {
            // given
            for (var team : teams) {
                season.addTeam(team);
            }
            seasonRepository.save(season);

            var playoff = playoffService.createPlayoff(season.getId(), "Auto Seed Test", 4);
            Map<UUID, Integer> teamSeeds = new LinkedHashMap<>();
            teamSeeds.put(teams.get(0).getId(), 1);
            teamSeeds.put(teams.get(1).getId(), 2);
            teamSeeds.put(teams.get(2).getId(), 3);
            teamSeeds.put(teams.get(3).getId(), 4);
            playoffSeedingService.saveSeedNumbers(playoff.getId(), teamSeeds);
            entityManager.flush();

            // when
            playoffSeedingService.autoSeedBracket(playoff.getId());
            entityManager.flush();

            // then — Matchup 0: Seed 1 vs Seed 4, Matchup 1: Seed 2 vs Seed 3
            var matchups = playoff.getRounds().get(0).getMatchups().stream()
                    .sorted(java.util.Comparator.comparingInt(PlayoffMatchup::getBracketPosition))
                    .toList();

            var m0 = playoffMatchupRepository.findById(matchups.get(0).getId()).orElseThrow();
            assertEquals(teams.get(0).getId(), m0.getTeam1().getId()); // Seed 1
            assertEquals(teams.get(3).getId(), m0.getTeam2().getId()); // Seed 4

            var m1 = playoffMatchupRepository.findById(matchups.get(1).getId()).orElseThrow();
            assertEquals(teams.get(1).getId(), m1.getTeam1().getId()); // Seed 2
            assertEquals(teams.get(2).getId(), m1.getTeam2().getId()); // Seed 3
        }
    }

    @Nested
    class FindRoundById {

        @Test
        void givenPlayoffRoundExists_whenFindRoundById_thenReturnsRound() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "FindRound Test", 4);
            var roundId = playoff.getRounds().get(0).getId();

            // when
            var result = playoffService.findRoundById(roundId);

            // then
            assertNotNull(result);
            assertEquals(roundId, result.getId());
            assertEquals("Semifinal", result.getLabel());
        }

        @Test
        void givenPlayoffRoundNotFound_whenFindRoundById_thenThrowsEntityNotFoundException() {
            // when / then
            assertThrows(EntityNotFoundException.class, () ->
                    playoffService.findRoundById(UUID.randomUUID()));
        }
    }

    @Nested
    class SeasonIdLookups {

        @Test
        void givenPlayoff_whenGetSeasonIdForPlayoff_thenReturnsCorrectSeasonId() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Lookup Test", 4);

            // when / then
            assertEquals(season.getId(), playoffService.getSeasonIdForPlayoff(playoff.getId()));
        }

        @Test
        void givenMatchup_whenGetSeasonIdForMatchup_thenReturnsCorrectSeasonId() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Matchup Lookup Test", 4);
            var matchupId = playoff.getRounds().get(0).getMatchups().get(0).getId();

            // when / then
            assertEquals(season.getId(), playoffService.getSeasonIdForMatchup(matchupId));
        }

        @Test
        void givenRound_whenGetSeasonIdForRound_thenReturnsCorrectSeasonId() {
            // given
            var playoff = playoffService.createPlayoff(season.getId(), "Round Lookup Test", 4);
            var roundId = playoff.getRounds().get(0).getId();

            // when / then
            assertEquals(season.getId(), playoffService.getSeasonIdForRound(roundId));
        }
    }
}
