package de.ctc.dataimport;

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
        void shouldExtractFromFullUrl() {
            var url = "https://docs.google.com/spreadsheets/d/" + SAMPLE_ID + "/edit";

            assertEquals(SAMPLE_ID, service.extractSpreadsheetId(url));
        }

        @Test
        void shouldExtractFromUrlWithGid() {
            var url = "https://docs.google.com/spreadsheets/d/" + SAMPLE_ID + "/edit#gid=0";

            assertEquals(SAMPLE_ID, service.extractSpreadsheetId(url));
        }

        @Test
        void shouldExtractFromUrlWithTrailingSlash() {
            var url = "https://docs.google.com/spreadsheets/d/" + SAMPLE_ID + "/";

            assertEquals(SAMPLE_ID, service.extractSpreadsheetId(url));
        }

        @Test
        void shouldExtractFromUrlWithoutTrailingSlash() {
            var url = "https://docs.google.com/spreadsheets/d/" + SAMPLE_ID;

            assertEquals(SAMPLE_ID, service.extractSpreadsheetId(url));
        }

        @Test
        void shouldAcceptBareSpreadsheetId() {
            assertEquals(SAMPLE_ID, service.extractSpreadsheetId(SAMPLE_ID));
        }

        @Test
        void shouldThrowForNullUrl() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.extractSpreadsheetId(null));
        }

        @Test
        void shouldThrowForBlankUrl() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.extractSpreadsheetId("   "));
        }

        @Test
        void shouldThrowForInvalidUrl() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.extractSpreadsheetId("https://example.com/not-a-sheet"));
        }
    }

    @Nested
    class IsAvailableTest {

        @Test
        void shouldReturnTrueWhenCredentialsExist(@TempDir Path tempDir) throws IOException {
            Path credentialsFile = tempDir.resolve("credentials.json");
            Files.writeString(credentialsFile, "{}");

            var service = new GoogleSheetsService(credentialsFile.toString());

            assertTrue(service.isAvailable());
        }

        @Test
        void shouldReturnFalseWhenPathIsEmpty() {
            var service = new GoogleSheetsService("");

            assertFalse(service.isAvailable());
        }

        @Test
        void shouldReturnFalseWhenPathIsNull() {
            var service = new GoogleSheetsService(null);

            assertFalse(service.isAvailable());
        }

        @Test
        void shouldReturnFalseWhenFileDoesNotExist() {
            var service = new GoogleSheetsService("/nonexistent/path.json");

            assertFalse(service.isAvailable());
        }
    }
}
