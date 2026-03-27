package de.ctc.domain.service;

import de.ctc.domain.model.Driver;
import de.ctc.domain.model.Race;
import de.ctc.domain.model.RaceResult;
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

    @Nested
    class RacePointsTest {

        @ParameterizedTest
        @CsvSource({
            "1, 20", "2, 17", "3, 14", "4, 12", "5, 10", "6, 8",
            "7, 7", "8, 6", "9, 5", "10, 4", "11, 3", "12, 2"
        })
        void shouldReturnCorrectRacePoints(int position, int expectedPoints) {
            assertEquals(expectedPoints, scoringService.calculateRacePoints(position));
        }

        @Test
        void shouldThrowForInvalidPosition() {
            assertThrows(IllegalArgumentException.class, () -> scoringService.calculateRacePoints(0));
            assertThrows(IllegalArgumentException.class, () -> scoringService.calculateRacePoints(13));
        }
    }

    @Nested
    class QualiPointsTest {

        @ParameterizedTest
        @CsvSource({
            "1, 3", "2, 2", "3, 1", "4, 0", "5, 0", "12, 0"
        })
        void shouldReturnCorrectQualiPoints(int position, int expectedPoints) {
            assertEquals(expectedPoints, scoringService.calculateQualiPoints(position));
        }

        @Test
        void shouldThrowForInvalidQualiPosition() {
            assertThrows(IllegalArgumentException.class, () -> scoringService.calculateQualiPoints(0));
            assertThrows(IllegalArgumentException.class, () -> scoringService.calculateQualiPoints(13));
        }
    }

    @Nested
    class FastestLapPointsTest {

        @Test
        void shouldReturn2ForFastestLap() {
            assertEquals(2, scoringService.calculateFastestLapPoints(true));
        }

        @Test
        void shouldReturn0ForNoFastestLap() {
            assertEquals(0, scoringService.calculateFastestLapPoints(false));
        }
    }

    @Nested
    class CalculatePointsTest {

        @Test
        void shouldCalculateAllPointsForResult() {
            // panicpotato17: Position 1, Quali 1, no FL -> 20 + 3 + 0 = 23
            var driver = new Driver("panicpotato17", "panicpotato17");
            var result = new RaceResult(new Race(), driver, 1, 1, false);

            scoringService.calculatePoints(result);

            assertEquals(20, result.getPointsRace());
            assertEquals(3, result.getPointsQuali());
            assertEquals(0, result.getPointsFl());
            assertEquals(23, result.getPointsTotal());
        }

        @Test
        void shouldCalculatePointsWithFastestLap() {
            // P1R_Jake: Position 11, Quali 2, FL -> 3 + 2 + 2 = 7
            var driver = new Driver("P1R_Jake", "P1R_Jake");
            var result = new RaceResult(new Race(), driver, 11, 2, true);

            scoringService.calculatePoints(result);

            assertEquals(3, result.getPointsRace());
            assertEquals(2, result.getPointsQuali());
            assertEquals(2, result.getPointsFl());
            assertEquals(7, result.getPointsTotal());
        }

        @Test
        void shouldCalculatePointsForList() {
            var driver1 = new Driver("driver1", "Driver 1");
            var driver2 = new Driver("driver2", "Driver 2");
            var race = new Race();
            var results = List.of(
                    new RaceResult(race, driver1, 1, 1, false),
                    new RaceResult(race, driver2, 12, 12, false)
            );

            scoringService.calculatePoints(results);

            assertEquals(23, results.get(0).getPointsTotal());
            assertEquals(2, results.get(1).getPointsTotal());
        }
    }

    @Nested
    class TeamTotalTest {

        @Test
        void shouldSumTeamResults() {
            // TNR A example: 70 total
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
