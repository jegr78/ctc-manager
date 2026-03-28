package de.ctc.domain.service;

import de.ctc.domain.model.*;
import de.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SwissPairingServiceTest {

    @Autowired
    private SwissPairingService swissPairingService;

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private RaceRepository raceRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private SeasonDriverRepository seasonDriverRepository;

    private Season season;

    @BeforeEach
    void setUp() {
        season = new Season("Swiss Test " + UUID.randomUUID().toString().substring(0, 4));
        season.setFormat(SeasonFormat.SWISS);
        season.setTotalRounds(5);
        season = seasonRepository.save(season);
    }

    @Test
    void shouldGenerateFirstRoundWithCorrectNumberOfPairings() {
        addTeams(6);

        var matchday = swissPairingService.generateNextRound(season.getId());

        assertEquals("Round 1", matchday.getLabel());
        assertEquals(1, matchday.getSortIndex());

        var races = raceRepository.findByMatchdayId(matchday.getId());
        assertEquals(3, races.size());
        races.forEach(r -> assertFalse(r.isBye()));
    }

    @Test
    void shouldGenerateByeForOddNumberOfTeams() {
        addTeams(5);

        var matchday = swissPairingService.generateNextRound(season.getId());

        var races = raceRepository.findByMatchdayId(matchday.getId());
        assertEquals(3, races.size()); // 2 regular + 1 bye

        long byeCount = races.stream().filter(Race::isBye).count();
        assertEquals(1, byeCount);

        var byeRace = races.stream().filter(Race::isBye).findFirst().orElseThrow();
        assertNotNull(byeRace.getHomeTeam());
        assertNull(byeRace.getAwayTeam());
    }

    @Test
    void shouldNotAllowGeneratingBeyondTotalRounds() {
        addTeams(4);
        season.setTotalRounds(1);
        seasonRepository.save(season);

        var md = swissPairingService.generateNextRound(season.getId());
        addDummyResults(md.getId());

        assertThrows(IllegalStateException.class,
                () -> swissPairingService.generateNextRound(season.getId()));
    }

    @Test
    void shouldRejectNonSwissSeason() {
        var leagueSeason = new Season("League Test " + UUID.randomUUID().toString().substring(0, 4));
        leagueSeason.setFormat(SeasonFormat.LEAGUE);
        leagueSeason = seasonRepository.save(leagueSeason);

        UUID id = leagueSeason.getId();
        assertThrows(IllegalArgumentException.class,
                () -> swissPairingService.generateNextRound(id));
    }

    @Test
    void shouldAvoidRematchesInSubsequentRounds() {
        addTeams(6);

        // Generate first round and add results
        var md1 = swissPairingService.generateNextRound(season.getId());
        addDummyResults(md1.getId());

        // Record first round pairs
        var races1 = raceRepository.findByMatchdayId(md1.getId());
        Set<String> firstRoundPairs = new HashSet<>();
        for (var race : races1) {
            firstRoundPairs.add(pairKey(race.getHomeTeam().getId(), race.getAwayTeam().getId()));
        }

        // Generate second round
        var md2 = swissPairingService.generateNextRound(season.getId());
        var races2 = raceRepository.findByMatchdayId(md2.getId());

        // No rematches
        for (var race : races2) {
            String pair = pairKey(race.getHomeTeam().getId(), race.getAwayTeam().getId());
            assertFalse(firstRoundPairs.contains(pair),
                    "Rematch: " + race.getHomeTeam().getShortName() + " vs " + race.getAwayTeam().getShortName());
        }
    }

    @Test
    void shouldCalculateBuchholz() {
        addTeams(4);

        var md = swissPairingService.generateNextRound(season.getId());
        addDummyResults(md.getId());

        var buchholz = swissPairingService.calculateBuchholz(season.getId());
        assertFalse(buchholz.isEmpty());
    }

    private void addTeams(int count) {
        for (int i = 0; i < count; i++) {
            var team = teamRepository.save(new Team("SwissT " + i + "_" + UUID.randomUUID().toString().substring(0, 4),
                    "SW" + i + UUID.randomUUID().toString().substring(0, 2)));
            season.getTeams().add(team);
        }
        seasonRepository.save(season);
    }

    private void addDummyResults(UUID matchdayId) {
        var races = raceRepository.findByMatchdayId(matchdayId);
        for (var race : races) {
            if (race.isBye()) continue;

            var homeDriver = driverRepository.save(new Driver(
                    "sh_" + UUID.randomUUID().toString().substring(0, 8), "Home Driver"));
            seasonDriverRepository.save(new SeasonDriver(season, homeDriver, race.getHomeTeam()));

            var awayDriver = driverRepository.save(new Driver(
                    "sa_" + UUID.randomUUID().toString().substring(0, 8), "Away Driver"));
            seasonDriverRepository.save(new SeasonDriver(season, awayDriver, race.getAwayTeam()));

            var hr = new RaceResult();
            hr.setRace(race);
            hr.setDriver(homeDriver);
            hr.setPosition(1);
            hr.setQualiPosition(1);
            hr.setPointsTotal(20);
            race.getResults().add(hr);

            var ar = new RaceResult();
            ar.setRace(race);
            ar.setDriver(awayDriver);
            ar.setPosition(2);
            ar.setQualiPosition(2);
            ar.setPointsTotal(10);
            race.getResults().add(ar);

            raceRepository.save(race);
        }
    }

    private String pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }
}
