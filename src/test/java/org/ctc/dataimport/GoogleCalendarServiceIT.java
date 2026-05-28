package org.ctc.dataimport;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.calendar.Calendar;
import java.lang.reflect.Field;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.util.List;
import org.ctc.dataimport.exception.AuthGoogleApiException;
import org.ctc.dataimport.exception.GoogleApiExceptionMapper;
import org.ctc.dataimport.exception.PermissionGoogleApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test exercising the real Spring-wired {@link GoogleCalendarService}
 * through the {@link GoogleApiExceptionMapper} 403-discriminator path
 * (reason=authError → Auth vs. fall-through → Permission) and the
 * GeneralSecurityException → AuthGoogleApiException path that the static helper
 * exposes but no unit test currently exercises.
 *
 * <p>Restores the JaCoCo line-coverage cold spots identified in v1.12 COV-01 audit.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Tag("integration")
class GoogleCalendarServiceIT {

	@Autowired
	private GoogleCalendarService googleCalendarService;

	private Calendar originalClient;

	@BeforeEach
	void snapshotClient() throws Exception {
		Field field = GoogleCalendarService.class.getDeclaredField("calendarClient");
		field.setAccessible(true);
		originalClient = (Calendar) field.get(googleCalendarService);
	}

	@AfterEach
	void restoreClient() throws Exception {
		Field field = GoogleCalendarService.class.getDeclaredField("calendarClient");
		field.setAccessible(true);
		field.set(googleCalendarService, originalClient);
	}

	private static void injectCalendarClient(GoogleCalendarService service, Calendar client) throws Exception {
		Field field = GoogleCalendarService.class.getDeclaredField("calendarClient");
		field.setAccessible(true);
		field.set(service, client);
	}

	private static GoogleJsonResponseException mock403With(String reason) {
		var gjre = mock(GoogleJsonResponseException.class);
		when(gjre.getStatusCode()).thenReturn(403);
		var details = new GoogleJsonError();
		var errorInfo = new GoogleJsonError.ErrorInfo();
		errorInfo.setReason(reason);
		details.setErrors(List.of(errorInfo));
		when(gjre.getDetails()).thenReturn(details);
		return gjre;
	}

	@Test
	void givenAuthErrorReason_whenCreateEvent_thenThrowsAuthGoogleApiException() throws Exception {
		// given
		var authError = mock403With("authError");
		var mockCalendar = mock(Calendar.class, RETURNS_DEEP_STUBS);
		when(mockCalendar.events().insert(anyString(), org.mockito.ArgumentMatchers.any()).execute())
				.thenThrow(authError);
		injectCalendarClient(googleCalendarService, mockCalendar);

		// when / then
		assertThatThrownBy(() -> googleCalendarService.createEvent("Race", LocalDateTime.now(), 60))
				.isInstanceOf(AuthGoogleApiException.class);
	}

	@Test
	void givenForbiddenWithoutAuthReason_whenCreateEvent_thenThrowsPermissionGoogleApiException() throws Exception {
		// given
		var forbidden = mock403With("forbidden");
		var mockCalendar = mock(Calendar.class, RETURNS_DEEP_STUBS);
		when(mockCalendar.events().insert(anyString(), org.mockito.ArgumentMatchers.any()).execute())
				.thenThrow(forbidden);
		injectCalendarClient(googleCalendarService, mockCalendar);

		// when / then
		assertThatThrownBy(() -> googleCalendarService.createEvent("Race", LocalDateTime.now(), 60))
				.isInstanceOf(PermissionGoogleApiException.class);
	}

	@Test
	void givenGeneralSecurityException_whenMapperFrom_thenReturnsAuthGoogleApiException() {
		// given
		var raw = new GeneralSecurityException("invalid key");

		// when
		var mapped = GoogleApiExceptionMapper.from(raw);

		// then
		assertThat(mapped)
				.isInstanceOf(AuthGoogleApiException.class)
				.hasCause(raw);
	}
}
