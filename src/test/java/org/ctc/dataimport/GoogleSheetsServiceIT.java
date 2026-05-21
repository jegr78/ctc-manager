package org.ctc.dataimport;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sheets.v4.Sheets;
import java.io.IOException;
import java.lang.reflect.Field;
import org.ctc.dataimport.exception.NotFoundGoogleApiException;
import org.ctc.dataimport.exception.TransientGoogleApiException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration test exercising the real Spring-wired {@link GoogleSheetsService}
 * end-to-end through the {@link org.ctc.dataimport.exception.GoogleApiExceptionMapper}
 * IOException translation path (default mapping + 404 GoogleJsonResponseException
 * discriminator), supplementing the unit-level coverage in
 * {@code GoogleSheetsServiceTest.TypedThrowsContractTest}.
 *
 * <p>Restores the JaCoCo line-coverage cold spots identified in v1.12 COV-01 audit
 * by routing the synthesized exceptions through the real mapper inside a full
 * Spring context (rather than a hand-constructed service instance).
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Tag("integration")
class GoogleSheetsServiceIT {

	@Autowired
	private GoogleSheetsService googleSheetsService;

	private static void injectSheetsClient(GoogleSheetsService service, Sheets client) throws Exception {
		Field field = GoogleSheetsService.class.getDeclaredField("sheetsClient");
		field.setAccessible(true);
		field.set(service, client);
	}

	@Test
	void givenIoException_whenGetSheetNames_thenThrowsTransientGoogleApiException() throws Exception {
		// given
		var mockSheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(mockSheets.spreadsheets().get(anyString()).execute())
				.thenThrow(new IOException("network timeout"));
		injectSheetsClient(googleSheetsService, mockSheets);

		// when / then
		assertThatThrownBy(() -> googleSheetsService.getSheetNames("abc"))
				.isInstanceOf(TransientGoogleApiException.class)
				.hasCauseInstanceOf(IOException.class);
	}

	@Test
	void givenNotFoundJsonResponse_whenGetSheetNames_thenThrowsNotFoundGoogleApiException() throws Exception {
		// given
		var mockSheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		var gjre = mock(GoogleJsonResponseException.class);
		when(gjre.getStatusCode()).thenReturn(404);
		when(mockSheets.spreadsheets().get(anyString()).execute()).thenThrow(gjre);
		injectSheetsClient(googleSheetsService, mockSheets);

		// when / then
		assertThatThrownBy(() -> googleSheetsService.getSheetNames("does-not-exist"))
				.isInstanceOf(NotFoundGoogleApiException.class);
	}

	@Test
	void givenIoException_whenReadRange_thenThrowsTransientGoogleApiException() throws Exception {
		// given
		var mockSheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
		when(mockSheets.spreadsheets().values().get(anyString(), anyString()).execute())
				.thenThrow(new IOException("connection reset"));
		injectSheetsClient(googleSheetsService, mockSheets);

		// when / then
		assertThatThrownBy(() -> googleSheetsService.readRange("id", "A1:B2"))
				.isInstanceOf(TransientGoogleApiException.class);
	}
}
