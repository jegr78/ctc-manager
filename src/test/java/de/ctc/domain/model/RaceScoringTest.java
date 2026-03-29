package de.ctc.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaceScoringTest {

    @Nested
    class RacePointsArrayTest {

        @Test
        void shouldParseCommaSeparatedRacePoints() {
            var scoring = new RaceScoring("Test", "20,17,14,12,10,8,7,6,5,4,3,2", null, 0);
            assertArrayEquals(new int[]{20, 17, 14, 12, 10, 8, 7, 6, 5, 4, 3, 2}, scoring.getRacePointsArray());
        }

        @Test
        void shouldHandleSpacesInCsv() {
            var scoring = new RaceScoring("Test", "20, 17, 14", null, 0);
            assertArrayEquals(new int[]{20, 17, 14}, scoring.getRacePointsArray());
        }

        @Test
        void shouldHandleSingleValue() {
            var scoring = new RaceScoring("Test", "10", null, 0);
            assertArrayEquals(new int[]{10}, scoring.getRacePointsArray());
        }
    }

    @Nested
    class QualiPointsArrayTest {

        @Test
        void shouldParseCommaSeparatedQualiPoints() {
            var scoring = new RaceScoring("Test", "20,17,14", "3,2,1", 0);
            assertArrayEquals(new int[]{3, 2, 1}, scoring.getQualiPointsArray());
        }

        @Test
        void shouldReturnEmptyArrayForNullQualiPoints() {
            var scoring = new RaceScoring("Test", "20,17,14", null, 0);
            assertArrayEquals(new int[]{}, scoring.getQualiPointsArray());
        }

        @Test
        void shouldReturnEmptyArrayForEmptyQualiPoints() {
            var scoring = new RaceScoring("Test", "20,17,14", "", 0);
            assertArrayEquals(new int[]{}, scoring.getQualiPointsArray());
        }

        @Test
        void shouldReturnEmptyArrayForBlankQualiPoints() {
            var scoring = new RaceScoring("Test", "20,17,14", "  ", 0);
            assertArrayEquals(new int[]{}, scoring.getQualiPointsArray());
        }
    }

    @Nested
    class MonotonicallyDecreasingValidationTest {

        @Test
        void shouldAcceptStrictlyDecreasingValues() {
            var scoring = new RaceScoring("Test", "20,17,14,12,10", "3,2,1", 0);
            assertTrue(scoring.isValid());
        }

        @Test
        void shouldAcceptEqualConsecutiveValues() {
            var scoring = new RaceScoring("Test", "20,17,14,14,10", "3,2,1", 0);
            assertTrue(scoring.isValid());
        }

        @Test
        void shouldRejectIncreasingRacePoints() {
            var scoring = new RaceScoring("Test", "20,17,14,15,10", "3,2,1", 0);
            assertFalse(scoring.isValid());
        }

        @Test
        void shouldRejectIncreasingQualiPoints() {
            var scoring = new RaceScoring("Test", "20,17,14", "3,2,4", 0);
            assertFalse(scoring.isValid());
        }

        @Test
        void shouldAcceptNullQualiPointsAsValid() {
            var scoring = new RaceScoring("Test", "20,17,14", null, 0);
            assertTrue(scoring.isValid());
        }

        @Test
        void shouldAcceptSingleValueAsValid() {
            var scoring = new RaceScoring("Test", "10", null, 0);
            assertTrue(scoring.isValid());
        }

        @Test
        void shouldAcceptAllEqualValues() {
            var scoring = new RaceScoring("Test", "10,10,10", null, 0);
            assertTrue(scoring.isValid());
        }
    }
}
