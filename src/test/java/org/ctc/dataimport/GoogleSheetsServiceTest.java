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

		// --- Coverage Tests: Additional edge cases ---

		@Test
		void givenEmptySheetNames_whenFilterRaceSheets_thenReturnsEmpty() {
			// given - empty list of sheet names
			var allSheets = java.util.List.<String>of();

			// when
			var raceSheets = service.filterRaceSheets(allSheets);

			// then
			assertTrue(raceSheets.isEmpty());
		}

		@Test
		void givenOnlyOverallTab_whenFilterRaceSheets_thenReturnsEmpty() {
			// given - only "Overall" sheet exists
			var allSheets = java.util.List.of("Overall");

			// when
			var raceSheets = service.filterRaceSheets(allSheets);

			// then
			assertTrue(raceSheets.isEmpty());
		}

		@Test
		void givenMultipleOverallSheets_whenFilterRaceSheets_thenReturnsEmpty() {
			// given - multiple "Overall" variations
			var allSheets = java.util.List.of("Overall", "OVERALL", "overall");

			// when
			var raceSheets = service.filterRaceSheets(allSheets);

			// then - should filter all Overall sheets
			assertTrue(raceSheets.isEmpty());
		}

		@Test
		void givenMixedRaceAndOverallSheets_whenFilterRaceSheets_thenReturnsOnlyRaceSheets() {
			// given - mixed sheets with multiple races and overall
			var allSheets = java.util.List.of("Race 1", "Race 2", "Overall", "race 3");

			// when
			var raceSheets = service.filterRaceSheets(allSheets);

			// then - should return all race sheets (case-insensitive) and exclude overall
			assertEquals(3, raceSheets.size());
			assertTrue(raceSheets.contains("Race 1"));
			assertTrue(raceSheets.contains("Race 2"));
			assertTrue(raceSheets.contains("race 3"));
			assertFalse(raceSheets.contains("Overall"));
		}

		@Test
		void givenWildcardSheetNames_whenFilterRaceSheets_thenHandlesCorrectly() {
			// given - sheets with various naming patterns
			var allSheets = java.util.List.of("Race 1", "Racer vs Team", "Racing Results", "Summary");

			// when
			var raceSheets = service.filterRaceSheets(allSheets);

			// then - should match "Race" in various contexts
			assertTrue(raceSheets.size() >= 2);  // At least Race 1 and Racer vs Team
			assertFalse(raceSheets.contains("Summary"));
		}

		@Test
		void givenAllFieldsNull_whenIsAvailable_thenReturnsFalse() {
			// given - service with null credentials path
			var service = new GoogleSheetsService(null);

			// when
			var available = service.isAvailable();

			// then
			assertFalse(available);
		}

		@Test
		void givenBlankCredentialsPath_whenIsAvailable_thenReturnsFalse() {
			// given - service with blank credentials path
			var service = new GoogleSheetsService("   ");

			// when
			var available = service.isAvailable();

			// then
			assertFalse(available);
		}

		@Test
		void givenCredentialsPathWithSpecialCharacters_whenExtractSpreadsheetId_thenHandlesProperly() {
			// given
			var service = new GoogleSheetsService("");
			var urlWithSpecialChars = "https://docs.google.com/spreadsheets/d/abc-123_XYZ/edit";

			// when
			var id = service.extractSpreadsheetId(urlWithSpecialChars);

			// then
			assertEquals("abc-123_XYZ", id);
		}

		@Test
		void givenSpreadsheetIdWithHyphensAndUnderscores_whenExtractSpreadsheetId_thenReturnsId() {
			// given
			var service = new GoogleSheetsService("");
			var bareId = "abc-123_def-456";

			// when
			var id = service.extractSpreadsheetId(bareId);

			// then
			assertEquals("abc-123_def-456", id);
		}

		@Test
		void givenInvalidSpreadsheetIdWithSpecialChars_whenExtractSpreadsheetId_thenThrows() {
			// given
			var service = new GoogleSheetsService("");
			var invalidId = "abc@123#def";

			// when/then
			assertThrows(IllegalArgumentException.class, () -> service.extractSpreadsheetId(invalidId));
		}

		@Test
		void givenUrlWithQueryParams_whenExtractSpreadsheetId_thenExtracts() {
			// given
			var service = new GoogleSheetsService("");
			var urlWithParams = "https://docs.google.com/spreadsheets/d/abc123def456/edit?usp=sharing#gid=0";

			// when
			var id = service.extractSpreadsheetId(urlWithParams);

			// then
			assertEquals("abc123def456", id);
		}
	}
}
