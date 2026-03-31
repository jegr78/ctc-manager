package org.ctc.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RaceScoringTest {

    @Nested
    class RacePointsArrayTest {

        @Test
        void whenGetRacePointsArray_thenParsesCommaSeparatedValues() {
            // given
            var scoring = new RaceScoring("Test", "20,17,14,12,10,8,7,6,5,4,3,2", null, 0);

            // when / then
            assertArrayEquals(new int[]{20, 17, 14, 12, 10, 8, 7, 6, 5, 4, 3, 2}, scoring.getRacePointsArray());
        }

        @Test
        void givenCsvWithSpaces_whenGetRacePointsArray_thenTrimsAndParses() {
            // given
            var scoring = new RaceScoring("Test", "20, 17, 14", null, 0);

            // when / then
            assertArrayEquals(new int[]{20, 17, 14}, scoring.getRacePointsArray());
        }

        @Test
        void givenSingleValue_whenGetRacePointsArray_thenReturnsSingleElementArray() {
            // given
            var scoring = new RaceScoring("Test", "10", null, 0);

            // when / then
            assertArrayEquals(new int[]{10}, scoring.getRacePointsArray());
        }
    }

    @Nested
    class QualiPointsArrayTest {

        @Test
        void whenGetQualiPointsArray_thenParsesCommaSeparatedValues() {
            // given
            var scoring = new RaceScoring("Test", "20,17,14", "3,2,1", 0);

            // when / then
            assertArrayEquals(new int[]{3, 2, 1}, scoring.getQualiPointsArray());
        }

        @Test
        void givenNullQualiPoints_whenGetQualiPointsArray_thenReturnsEmptyArray() {
            // given
            var scoring = new RaceScoring("Test", "20,17,14", null, 0);

            // when / then
            assertArrayEquals(new int[]{}, scoring.getQualiPointsArray());
        }

        @Test
        void givenEmptyQualiPoints_whenGetQualiPointsArray_thenReturnsEmptyArray() {
            // given
            var scoring = new RaceScoring("Test", "20,17,14", "", 0);

            // when / then
            assertArrayEquals(new int[]{}, scoring.getQualiPointsArray());
        }

        @Test
        void givenBlankQualiPoints_whenGetQualiPointsArray_thenReturnsEmptyArray() {
            // given
            var scoring = new RaceScoring("Test", "20,17,14", "  ", 0);

            // when / then
            assertArrayEquals(new int[]{}, scoring.getQualiPointsArray());
        }
    }

    @Nested
    class MonotonicallyDecreasingValidationTest {

        @Test
        void givenStrictlyDecreasingValues_whenIsValid_thenReturnsTrue() {
            // given
            var scoring = new RaceScoring("Test", "20,17,14,12,10", "3,2,1", 0);

            // when / then
            assertTrue(scoring.isValid());
        }

        @Test
        void givenEqualConsecutiveValues_whenIsValid_thenReturnsTrue() {
            // given
            var scoring = new RaceScoring("Test", "20,17,14,14,10", "3,2,1", 0);

            // when / then
            assertTrue(scoring.isValid());
        }

        @Test
        void givenIncreasingRacePoints_whenIsValid_thenReturnsFalse() {
            // given
            var scoring = new RaceScoring("Test", "20,17,14,15,10", "3,2,1", 0);

            // when / then
            assertFalse(scoring.isValid());
        }

        @Test
        void givenIncreasingQualiPoints_whenIsValid_thenReturnsFalse() {
            // given
            var scoring = new RaceScoring("Test", "20,17,14", "3,2,4", 0);

            // when / then
            assertFalse(scoring.isValid());
        }

        @Test
        void givenNullQualiPoints_whenIsValid_thenReturnsTrue() {
            // given
            var scoring = new RaceScoring("Test", "20,17,14", null, 0);

            // when / then
            assertTrue(scoring.isValid());
        }

        @Test
        void givenSingleValue_whenIsValid_thenReturnsTrue() {
            // given
            var scoring = new RaceScoring("Test", "10", null, 0);

            // when / then
            assertTrue(scoring.isValid());
        }

        @Test
        void givenAllEqualValues_whenIsValid_thenReturnsTrue() {
            // given
            var scoring = new RaceScoring("Test", "10,10,10", null, 0);

            // when / then
            assertTrue(scoring.isValid());
        }
    }
}
