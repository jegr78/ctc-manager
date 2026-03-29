package de.ctc.domain.service;

import de.ctc.domain.model.Driver;
import de.ctc.domain.model.Race;
import de.ctc.domain.model.RaceResult;
import de.ctc.domain.model.RaceScoring;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScoringServiceTest {

    private ScoringService scoringService;

    @BeforeEach
    void setUp() {
        scoringService = new ScoringService();
    }

    // Standard scoring preset matching current hardcoded values
    private static RaceScoring standardScoring() {
        return new RaceScoring("CTC Standard", "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2);
    }

    @Nested
    class CalculatePointsWithScoringTest {

        @Test
        void shouldCalculateWithStandardScoring() {
            var scoring = standardScoring();
            var driver = new Driver("panicpotato17", "panicpotato17");
            var result = new RaceResult(new Race(), driver, 1, 1, false);

            scoringService.calculatePoints(result, scoring);

            assertEquals(20, result.getPointsRace());
            assertEquals(3, result.getPointsQuali());
            assertEquals(0, result.getPointsFl());
            assertEquals(23, result.getPointsTotal());
        }

        @Test
        void shouldCalculateWithFastestLap() {
            var scoring = standardScoring();
            var driver = new Driver("P1R_Jake", "P1R_Jake");
            var result = new RaceResult(new Race(), driver, 11, 2, true);

            scoringService.calculatePoints(result, scoring);

            assertEquals(3, result.getPointsRace());
            assertEquals(2, result.getPointsQuali());
            assertEquals(2, result.getPointsFl());
            assertEquals(7, result.getPointsTotal());
        }

        @Test
        void shouldCalculateWithLegacyScoring() {
            var scoring = new RaceScoring("Legacy", "15,12,10,8,6,4,3,2,1", null, 0);
            var driver = new Driver("driver1", "Driver 1");
            var result = new RaceResult(new Race(), driver, 1, 1, true);

            scoringService.calculatePoints(result, scoring);

            assertEquals(15, result.getPointsRace());
            assertEquals(0, result.getPointsQuali()); // no quali points
            assertEquals(0, result.getPointsFl());     // FL disabled
            assertEquals(15, result.getPointsTotal());
        }

        @Test
        void shouldReturnZeroForPositionBeyondScale() {
            var scoring = new RaceScoring("Short", "10,5", "3", 0);
            var driver = new Driver("driver1", "Driver 1");
            var result = new RaceResult(new Race(), driver, 3, 2, false);

            scoringService.calculatePoints(result, scoring);

            assertEquals(0, result.getPointsRace());  // position 3 not in scale
            assertEquals(0, result.getPointsQuali());  // quali pos 2 not in scale (only 1 entry)
            assertEquals(0, result.getPointsTotal());
        }

        @Test
        void shouldCalculateListWithScoring() {
            var scoring = standardScoring();
            var driver1 = new Driver("driver1", "Driver 1");
            var driver2 = new Driver("driver2", "Driver 2");
            var race = new Race();
            var results = List.of(
                    new RaceResult(race, driver1, 1, 1, false),
                    new RaceResult(race, driver2, 12, 12, false)
            );

            scoringService.calculatePoints(results, scoring);

            assertEquals(23, results.get(0).getPointsTotal());
            assertEquals(2, results.get(1).getPointsTotal());
        }
    }

    @Nested
    class TeamTotalTest {

        @Test
        void shouldSumTeamResults() {
            var race = new Race();
            var results = List.of(
                    createResultWithTotal(race, "LEVITIUS", 11),
                    createResultWithTotal(race, "panicpotato17", 23),
                    createResultWithTotal(race, "Deekuhn", 14),
                    createResultWithTotal(race, "LotariRacing", 12),
                    createResultWithTotal(race, "Nutcap_1", 8),
                    createResultWithTotal(race, "Ghostriderz16173", 2)
            );

            assertEquals(70, scoringService.calculateTeamTotal(results));
        }

        @Test
        void shouldReturn0ForEmptyList() {
            assertEquals(0, scoringService.calculateTeamTotal(List.of()));
        }

        private RaceResult createResultWithTotal(Race race, String psnId, int total) {
            var result = new RaceResult();
            result.setRace(race);
            result.setDriver(new Driver(psnId, psnId));
            result.setPointsTotal(total);
            return result;
        }
    }
}
