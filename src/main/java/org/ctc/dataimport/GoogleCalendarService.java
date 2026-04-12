package org.ctc.dataimport;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class GoogleCalendarService {

	private static final String APPLICATION_NAME = "CTC Manager";
	private static final String TIME_ZONE = "Europe/London";

	private final String credentialsPath;
	private final String calendarId;
	private Calendar calendarClient;

	public GoogleCalendarService(
			@Value("${google.sheets.credentials-path:}") String credentialsPath,
			@Value("${google.calendar.id:}") String calendarId) {
		this.credentialsPath = credentialsPath;
		this.calendarId = calendarId;
	}

	@PostConstruct
	void logAvailability() {
		if (isAvailable()) {
			log.info("Google Calendar integration available (calendar: {})", calendarId);
		} else {
			log.info("Google Calendar integration not available (no credentials or calendar ID configured)");
		}
	}

	public boolean isAvailable() {
		return credentialsPath != null
				&& !credentialsPath.isBlank()
				&& Files.exists(Path.of(credentialsPath))
				&& calendarId != null
				&& !calendarId.isBlank();
	}

	public String createEvent(String title, LocalDateTime startTime, int durationMinutes) throws IOException {
		var client = getCalendarClient();
		var event = buildEvent(title, startTime, durationMinutes);

		var created = client.events().insert(calendarId, event).execute();
		log.info("Created calendar event '{}' (id: {})", title, created.getId());
		return created.getId();
	}

	public void updateEvent(String eventId, String title, LocalDateTime startTime, int durationMinutes) throws IOException {
		var client = getCalendarClient();
		var event = buildEvent(title, startTime, durationMinutes);

		client.events().update(calendarId, eventId, event).execute();
		log.info("Updated calendar event '{}' (id: {})", title, eventId);
	}

	private Event buildEvent(String title, LocalDateTime startTime, int durationMinutes) {
		var zoneId = ZoneId.of(TIME_ZONE);
		var startZoned = startTime.atZone(zoneId);
		var endZoned = startZoned.plusMinutes(durationMinutes);

		var event = new Event();
		event.setSummary(title);
		event.setStart(toEventDateTime(startZoned));
		event.setEnd(toEventDateTime(endZoned));
		return event;
	}

	private EventDateTime toEventDateTime(ZonedDateTime zdt) {
		var dateTime = new DateTime(zdt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")));
		return new EventDateTime().setDateTime(dateTime).setTimeZone(TIME_ZONE);
	}

	private synchronized Calendar getCalendarClient() throws IOException {
		if (calendarClient == null) {
			if (!isAvailable()) {
				throw new IllegalStateException(
						"Google Calendar credentials not configured or calendar ID missing");
			}
			try (var credentialsStream = new FileInputStream(credentialsPath)) {
				GoogleCredentials credentials = GoogleCredentials
						.fromStream(credentialsStream)
						.createScoped(CalendarScopes.CALENDAR_EVENTS);

				calendarClient = new Calendar.Builder(
						GoogleNetHttpTransport.newTrustedTransport(),
						GsonFactory.getDefaultInstance(),
						new HttpCredentialsAdapter(credentials))
						.setApplicationName(APPLICATION_NAME)
						.build();

				log.info("Google Calendar API client initialized");
			} catch (GeneralSecurityException e) {
				throw new IOException("Failed to initialize Google Calendar API client", e);
			}
		}
		return calendarClient;
	}
}
