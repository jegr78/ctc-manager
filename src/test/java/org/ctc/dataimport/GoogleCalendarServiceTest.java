package org.ctc.dataimport;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.calendar.Calendar;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.ctc.dataimport.exception.AuthGoogleApiException;
import org.ctc.dataimport.exception.GoogleApiException;
import org.ctc.dataimport.exception.TransientGoogleApiException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

	/**
	 * Asserts the typed-throws contract introduced in Phase 91 / UX-01:
	 * IOExceptions surfacing from the Calendar client are mapped to the
	 * sealed {@link GoogleApiException} subtypes via the mapper. The mocked
	 * client is injected through the private {@code calendarClient} field.
	 */
	@Nested
	class TypedThrowsContractTest {

		@Test
		void givenCreateEventSignature_whenInspectingThrows_thenDeclaresGoogleApiException() throws NoSuchMethodException {
			// given / when
			var method = GoogleCalendarService.class.getMethod(
					"createEvent", String.class, LocalDateTime.class, int.class);

			// then
			assertThat(method.getExceptionTypes()).contains(GoogleApiException.class);
		}

		@Test
		void givenCalendarClientThrowing401_whenCreateEvent_thenWrapsToAuthGoogleApiException() throws Exception {
			// given
			var service = new GoogleCalendarService("", "cal-id");
			var mockCalendar = mock(Calendar.class, RETURNS_DEEP_STUBS);
			var gjre = mock(GoogleJsonResponseException.class);
			when(gjre.getStatusCode()).thenReturn(401);
			when(mockCalendar.events().insert(anyString(), any()).execute()).thenThrow(gjre);
			injectCalendarClient(service, mockCalendar);

			// when / then
			assertThatThrownBy(() -> service.createEvent("Race", LocalDateTime.of(2026, 6, 1, 19, 0), 90))
					.isInstanceOf(AuthGoogleApiException.class);
		}

		@Test
		void givenCalendarClientThrowingNetworkFailure_whenUpdateEvent_thenWrapsToTransientGoogleApiException() throws Exception {
			// given
			var service = new GoogleCalendarService("", "cal-id");
			var mockCalendar = mock(Calendar.class, RETURNS_DEEP_STUBS);
			when(mockCalendar.events().update(anyString(), anyString(), any()).execute())
					.thenThrow(new SocketTimeoutException("connection reset"));
			injectCalendarClient(service, mockCalendar);

			// when / then
			assertThatThrownBy(() -> service.updateEvent(
					"event-id", "Race", LocalDateTime.of(2026, 6, 1, 19, 0), 90))
					.isInstanceOf(TransientGoogleApiException.class);
		}

		private static void injectCalendarClient(GoogleCalendarService service, Calendar client) throws Exception {
			Field field = GoogleCalendarService.class.getDeclaredField("calendarClient");
			field.setAccessible(true);
			field.set(service, client);
		}
	}
}
