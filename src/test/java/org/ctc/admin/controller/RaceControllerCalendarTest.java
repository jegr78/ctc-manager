package org.ctc.admin.controller;

import java.util.Map;
import java.util.UUID;
import org.ctc.admin.dto.MatchPreviewPreFlightResult;
import org.ctc.admin.service.RaceGraphicService;
import org.ctc.dataimport.exception.AuthGoogleApiException;
import org.ctc.dataimport.exception.NotFoundGoogleApiException;
import org.ctc.dataimport.exception.PermissionGoogleApiException;
import org.ctc.dataimport.exception.TransientGoogleApiException;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.ctc.domain.service.RaceAttachmentService;
import org.ctc.domain.service.RaceCalendarService;
import org.ctc.domain.service.RaceFormDataService;
import org.ctc.domain.service.RaceService;
import org.ctc.domain.service.RaceService.RaceDetailData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Focused tests for RaceController calendar-event surface — closes the JaCoCo
 * cold-spot identified in v1.12 COV-01 audit. Covers POST /create-calendar-event
 * (4 typed-catch arms + IllegalStateException + happy path) and the 3 GET
 * model attributes calendarAvailable / hasCalendarEvent / canCreateCalendarEvent.
 *
 * The defensive {@code catch (GoogleApiException e)} arm on the sealed base is
 * acknowledged unreachable from external tests (sealed permits forbid subclassing)
 * and not exercised here.
 *
 * GET tests use a separate standalone MockMvc with a noop view-resolver to
 * exercise the GET handler's model population without forcing Thymeleaf to
 * render the full race-detail template (which would require hydrating Race +
 * Matchday + Match + Track + Season — unrelated to COV-01).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class RaceControllerCalendarTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private RaceService raceService;
	@MockitoBean
	private RaceFormDataService raceFormDataService;
	@MockitoBean
	private RaceCalendarService raceCalendarService;
	@MockitoBean
	private RaceAttachmentService raceAttachmentService;
	@MockitoBean
	private RaceGraphicService raceGraphicService;
	@MockitoBean
	private org.ctc.discord.service.DiscordPostService discordPostService;
	@MockitoBean
	private org.ctc.discord.service.DiscordGlobalConfigService discordGlobalConfigService;
	@MockitoBean
	private org.ctc.discord.repository.DiscordPostRepository discordPostRepository;

	private MockMvc modelOnlyMockMvc;

	/** View-resolver that returns a noop View — populates model into request,
	 * never writes a response body, never invokes Thymeleaf. Lets the GET
	 * handler run end-to-end while skipping template rendering. */
	private static final ViewResolver NOOP_VIEW_RESOLVER = (viewName, locale) -> (View) (model, request, response) -> {};

	@BeforeEach
	void setupModelOnlyMockMvc() {
		when(discordPostService.canPostRaceResultToForum(any(), any()))
				.thenReturn(new MatchPreviewPreFlightResult(false, "No race results yet"));
		var controller = new RaceController(
				raceService, raceFormDataService, raceCalendarService,
				raceAttachmentService, raceGraphicService,
				discordPostService, discordGlobalConfigService, discordPostRepository);
		modelOnlyMockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setViewResolvers(NOOP_VIEW_RESOLVER)
				.build();
	}

	private Race homeAwayRace() {
		var home = new Team();
		home.setShortName("HOME");
		var away = new Team();
		away.setShortName("AWAY");
		var race = new Race();
		race.setId(UUID.randomUUID());
		race.setHomeTeamOverride(home);
		race.setAwayTeamOverride(away);
		return race;
	}

	private RaceDetailData detailData(boolean calendarAvailable, boolean hasCalendarEvent, boolean canCreateCalendarEvent) {
		return new RaceDetailData(
				homeAwayRace(), 0, 0, Map.of(), Map.of(),
				false, false, false, false,
				false, false, false,
				false, false,
				false, false, false,
				false, false,
				calendarAvailable, hasCalendarEvent, canCreateCalendarEvent,
				false, false, false);
	}

	@Test
	void givenCalendarAuthFailure_whenPostCreateCalendarEvent_thenRedirectsWithAuthBadge() throws Exception {
		// given
		var raceId = UUID.randomUUID();
		doThrow(new AuthGoogleApiException("auth failure", null))
				.when(raceCalendarService).createOrUpdateCalendarEvent(any(UUID.class));

		// when
		mockMvc.perform(post("/admin/races/" + raceId + "/create-calendar-event"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + raceId))
				.andExpect(flash().attribute("errorMessage", equalTo("Authentication problem — re-link Google account")))
				.andExpect(flash().attribute("errorCategory", equalTo("AUTH")));
	}

	@Test
	void givenCalendarNotFound_whenPostCreateCalendarEvent_thenRedirectsWithNotFoundBadge() throws Exception {
		// given
		var raceId = UUID.randomUUID();
		doThrow(new NotFoundGoogleApiException("404 not found", null))
				.when(raceCalendarService).createOrUpdateCalendarEvent(any(UUID.class));

		// when
		mockMvc.perform(post("/admin/races/" + raceId + "/create-calendar-event"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + raceId))
				.andExpect(flash().attribute("errorMessage", equalTo("Calendar not found — check the calendar ID configuration")))
				.andExpect(flash().attribute("errorCategory", equalTo("NOT_FOUND")));
	}

	@Test
	void givenCalendarPermissionDenied_whenPostCreateCalendarEvent_thenRedirectsWithPermissionBadge() throws Exception {
		// given
		var raceId = UUID.randomUUID();
		doThrow(new PermissionGoogleApiException("403 forbidden", null))
				.when(raceCalendarService).createOrUpdateCalendarEvent(any(UUID.class));

		// when
		mockMvc.perform(post("/admin/races/" + raceId + "/create-calendar-event"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + raceId))
				.andExpect(flash().attribute("errorMessage", equalTo("Access denied — share the calendar with the service account")))
				.andExpect(flash().attribute("errorCategory", equalTo("PERMISSION")));
	}

	@Test
	void givenCalendarTransientFailure_whenPostCreateCalendarEvent_thenRedirectsWithTransientBadge() throws Exception {
		// given
		var raceId = UUID.randomUUID();
		doThrow(new TransientGoogleApiException("network error", null))
				.when(raceCalendarService).createOrUpdateCalendarEvent(any(UUID.class));

		// when
		mockMvc.perform(post("/admin/races/" + raceId + "/create-calendar-event"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + raceId))
				.andExpect(flash().attribute("errorMessage", equalTo("Connection problem — retry")))
				.andExpect(flash().attribute("errorCategory", equalTo("TRANSIENT")));
	}

	@Test
	void givenCalendarIllegalState_whenPostCreateCalendarEvent_thenRedirectsWithPlainMessage() throws Exception {
		// given
		var raceId = UUID.randomUUID();
		doThrow(new IllegalStateException("Calendar API not configured"))
				.when(raceCalendarService).createOrUpdateCalendarEvent(any(UUID.class));

		// when
		mockMvc.perform(post("/admin/races/" + raceId + "/create-calendar-event"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + raceId))
				.andExpect(flash().attribute("errorMessage", equalTo("Calendar: Calendar API not configured")))
				.andExpect(flash().attributeCount(1));
	}

	@Test
	void givenNoFailure_whenPostCreateCalendarEvent_thenRedirectsWithSuccess() throws Exception {
		// given
		var raceId = UUID.randomUUID();
		// when raceCalendarService.createOrUpdateCalendarEvent returns void without throwing

		// when
		mockMvc.perform(post("/admin/races/" + raceId + "/create-calendar-event"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/races/" + raceId))
				.andExpect(flash().attribute("successMessage", equalTo("Calendar event saved")));
	}

	@Test
	void givenCalendarAvailable_whenGetRaceDetail_thenModelHasCalendarAvailableTrue() throws Exception {
		// given
		var raceId = UUID.randomUUID();
		when(raceService.getRaceDetailData(raceId)).thenReturn(detailData(true, false, true));

		// when
		modelOnlyMockMvc.perform(get("/admin/races/" + raceId))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/race-detail"))
				.andExpect(model().attribute("calendarAvailable", true));
	}

	@Test
	void givenExistingCalendarEvent_whenGetRaceDetail_thenModelHasEventFlag() throws Exception {
		// given
		var raceId = UUID.randomUUID();
		when(raceService.getRaceDetailData(raceId)).thenReturn(detailData(true, true, false));

		// when
		modelOnlyMockMvc.perform(get("/admin/races/" + raceId))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/race-detail"))
				.andExpect(model().attribute("hasCalendarEvent", true));
	}

	@Test
	void givenCanCreateEvent_whenGetRaceDetail_thenModelHasCanCreateFlag() throws Exception {
		// given
		var raceId = UUID.randomUUID();
		when(raceService.getRaceDetailData(raceId)).thenReturn(detailData(true, false, true));

		// when
		modelOnlyMockMvc.perform(get("/admin/races/" + raceId))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/race-detail"))
				.andExpect(model().attribute("canCreateCalendarEvent", true));
	}
}
