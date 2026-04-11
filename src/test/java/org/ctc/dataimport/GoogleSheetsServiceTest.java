package org.ctc.dataimport;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GoogleSheetsServiceTest {

    private static final String SAMPLE_ID = "1wMECayK2ZgycYwqEeypiPBZyYvJdAsdJwPQVYm7J1nQ";

    @Nested
    class ExtractSpreadsheetIdTest {

        private final GoogleSheetsService service = new GoogleSheetsService("");

        @Test
        void givenFullUrl_whenExtractSpreadsheetId_thenReturnsId() {
            // given
            var url = "https://docs.google.com/spreadsheets/d/" + SAMPLE_ID + "/edit";

            // when / then
            assertEquals(SAMPLE_ID, service.extractSpreadsheetId(url));
        }

        @Test
        void givenUrlWithGid_whenExtractSpreadsheetId_thenReturnsId() {
            // given
            var url = "https://docs.google.com/spreadsheets/d/" + SAMPLE_ID + "/edit#gid=0";

            // when / then
            assertEquals(SAMPLE_ID, service.extractSpreadsheetId(url));
        }

        @Test
        void givenUrlWithTrailingSlash_whenExtractSpreadsheetId_thenReturnsId() {
            // given
            var url = "https://docs.google.com/spreadsheets/d/" + SAMPLE_ID + "/";

            // when / then
            assertEquals(SAMPLE_ID, service.extractSpreadsheetId(url));
        }

        @Test
        void givenUrlWithoutTrailingSlash_whenExtractSpreadsheetId_thenReturnsId() {
            // given
            var url = "https://docs.google.com/spreadsheets/d/" + SAMPLE_ID;

            // when / then
            assertEquals(SAMPLE_ID, service.extractSpreadsheetId(url));
        }

        @Test
        void givenBareSpreadsheetId_whenExtractSpreadsheetId_thenReturnsId() {
            // when / then
            assertEquals(SAMPLE_ID, service.extractSpreadsheetId(SAMPLE_ID));
        }

        @Test
        void givenNullUrl_whenExtractSpreadsheetId_thenThrowsIllegalArgumentException() {
            // when / then
            assertThrows(IllegalArgumentException.class,
                    () -> service.extractSpreadsheetId(null));
        }

        @Test
        void givenBlankUrl_whenExtractSpreadsheetId_thenThrowsIllegalArgumentException() {
            // when / then
            assertThrows(IllegalArgumentException.class,
                    () -> service.extractSpreadsheetId("   "));
        }

        @Test
        void givenInvalidUrl_whenExtractSpreadsheetId_thenThrowsIllegalArgumentException() {
            // when / then
            assertThrows(IllegalArgumentException.class,
                    () -> service.extractSpreadsheetId("https://example.com/not-a-sheet"));
        }
    }

    @Nested
    class IsAvailableTest {

        @Test
        void givenExistingCredentialsFile_whenIsAvailable_thenReturnsTrue(@TempDir Path tempDir) throws IOException {
            // given
            Path credentialsFile = tempDir.resolve("credentials.json");
            Files.writeString(credentialsFile, "{}");

            var service = new GoogleSheetsService(credentialsFile.toString());

            // when / then
            assertTrue(service.isAvailable());
        }

        @Test
        void givenEmptyCredentialsPath_whenIsAvailable_thenReturnsFalse() {
            // given
            var service = new GoogleSheetsService("");

            // when / then
            assertFalse(service.isAvailable());
        }

        @Test
        void givenNullCredentialsPath_whenIsAvailable_thenReturnsFalse() {
            // given
            var service = new GoogleSheetsService(null);

            // when / then
            assertFalse(service.isAvailable());
        }

        @Test
        void givenNonExistentCredentialsFile_whenIsAvailable_thenReturnsFalse() {
            // given
            var service = new GoogleSheetsService("/nonexistent/path.json");

            // when / then
            assertFalse(service.isAvailable());
        }
    }

    @Nested
    class GetSheetNamesTest {

        private final GoogleSheetsService service = new GoogleSheetsService("");

        @Test
        void givenSheetNames_whenFilterRaceSheets_thenReturnsOnlyRaceTabs() {
            // given
            var allSheets = java.util.List.of("Race 1", "Race 2", "Overall", "Archive");

            // when
            var raceSheets = service.filterRaceSheets(allSheets);

            // then
            assertEquals(2, raceSheets.size());
            assertTrue(raceSheets.contains("Race 1"));
            assertTrue(raceSheets.contains("Race 2"));
            assertFalse(raceSheets.contains("Overall"));
        }

        @Test
        void givenSheetNamesWithoutRace_whenFilterRaceSheets_thenReturnsFallbackSheet() {
            // given - no "Race" sheets, but has a usable sheet
            var allSheets = java.util.List.of("Overall", "Archive");

            // when
            var raceSheets = service.filterRaceSheets(allSheets);

            // then - should fall back to non-Overall sheets
            assertTrue(raceSheets.contains("Archive"));
            assertFalse(raceSheets.contains("Overall"));
        }

        @Test
        void givenOnlyOverallSheet_whenFilterRaceSheets_thenReturnsEmpty() {
            // given - only summary sheet
            var allSheets = java.util.List.of("Overall");

            // when
            var raceSheets = service.filterRaceSheets(allSheets);

            // then - no usable sheets
            assertTrue(raceSheets.isEmpty());
        }

        @Test
        void givenSingleRaceSheet_whenFilterRaceSheets_thenReturnsSingleSheet() {
            // given
            var allSheets = java.util.List.of("Race 1", "Overall");

            // when
            var raceSheets = service.filterRaceSheets(allSheets);

            // then
            assertEquals(1, raceSheets.size());
            assertEquals("Race 1", raceSheets.get(0));
        }

        @Test
        void givenSingleTabWithArbitraryName_whenFilterRaceSheets_thenReturnsFallback() {
            // given - single tab with name that doesn't contain "Race"
            var allSheets = java.util.List.of("Matchday 1", "Overall");

            // when
            var raceSheets = service.filterRaceSheets(allSheets);

            // then - should fall back to accepting non-Overall sheets
            assertEquals(1, raceSheets.size());
            assertEquals("Matchday 1", raceSheets.get(0));
        }
    }
}
