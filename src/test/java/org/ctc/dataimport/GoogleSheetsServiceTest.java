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
}
