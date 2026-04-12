package org.ctc.dataimport;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoogleCalendarServiceTest {

	@Nested
	class IsAvailableTest {

		@Test
		void givenCredentialsAndCalendarId_whenIsAvailable_thenReturnsTrue(@TempDir Path tempDir) throws IOException {
			// given
			Path credentialsFile = tempDir.resolve("credentials.json");
			Files.writeString(credentialsFile, "{}");

			var service = new GoogleCalendarService(credentialsFile.toString(), "test-calendar-id@group.calendar.google.com");

			// when / then
			assertTrue(service.isAvailable());
		}

		@Test
		void givenEmptyCalendarId_whenIsAvailable_thenReturnsFalse(@TempDir Path tempDir) throws IOException {
			// given
			Path credentialsFile = tempDir.resolve("credentials.json");
			Files.writeString(credentialsFile, "{}");

			var service = new GoogleCalendarService(credentialsFile.toString(), "");

			// when / then
			assertFalse(service.isAvailable());
		}

		@Test
		void givenNullCalendarId_whenIsAvailable_thenReturnsFalse(@TempDir Path tempDir) throws IOException {
			// given
			Path credentialsFile = tempDir.resolve("credentials.json");
			Files.writeString(credentialsFile, "{}");

			var service = new GoogleCalendarService(credentialsFile.toString(), null);

			// when / then
			assertFalse(service.isAvailable());
		}

		@Test
		void givenEmptyCredentials_whenIsAvailable_thenReturnsFalse() {
			// given
			var service = new GoogleCalendarService("", "test-calendar-id");

			// when / then
			assertFalse(service.isAvailable());
		}

		@Test
		void givenNullCredentials_whenIsAvailable_thenReturnsFalse() {
			// given
			var service = new GoogleCalendarService(null, "test-calendar-id");

			// when / then
			assertFalse(service.isAvailable());
		}

		@Test
		void givenNonExistentCredentialsFile_whenIsAvailable_thenReturnsFalse() {
			// given
			var service = new GoogleCalendarService("/nonexistent/path.json", "test-calendar-id");

			// when / then
			assertFalse(service.isAvailable());
		}
	}
}
