package org.ctc.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ctc.TestHelper;
import org.ctc.admin.dto.MatchPreviewPreFlightResult;
import org.ctc.discord.exception.DiscordAuthException;
import org.ctc.discord.exception.DiscordNotFoundException;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.ctc.discord.service.DiscordPostService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class SeasonControllerPostStandingsIT {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestHelper helper;

	@Autowired
	SeasonRepository seasonRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Autowired
	DiscordGlobalConfigRepository globalConfigRepository;

	@Autowired
	DiscordGlobalConfigService globalConfigService;

	@MockitoBean
	DiscordPostService discordPostService;

	@BeforeEach
	void resetState() {
		discordPostRepository.deleteAll();
		when(discordPostService.canPostStandings(any(Season.class), any(DiscordGlobalConfig.class)))
				.thenReturn(new MatchPreviewPreFlightResult(true, null));
		when(discordPostService.resolveAnnouncementChannelId(any(String.class))).thenReturn("ch-st");
	}

	@AfterEach
	void cleanup() {
		discordPostRepository.deleteAll();
	}

	private Season seedSeason(String suffix, String webhookUrl) {
		Season season = helper.createSeason("CtrlSt " + suffix);
		season.setDiscordStandingsThreadId("thread-ctrl-st-" + suffix);
		Season saved = seasonRepository.save(season);

		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setStandingsForumWebhookUrl(webhookUrl);
		globalConfigRepository.save(cfg);
		return saved;
	}

	@Test
	void givenValidPhaseId_whenPostStandings_thenRedirectAndSuccessFlash() throws Exception {
		Season season = seedSeason("V1", "https://discord.com/api/webhooks/700/tok-st-v1");
		SeasonPhase phase = season.getPhases().get(0);

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/seasons/" + season.getId() + "/post-standings")
						.param("phaseId", phase.getId().toString())
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "Standings posted."));

		verify(discordPostService).postStandings(any(Season.class), any(SeasonPhase.class));
	}

	@Test
	void givenMissingPhaseId_whenPostStandings_thenBindingErrorFlashAndRedirect() throws Exception {
		Season season = seedSeason("V2", "https://discord.com/api/webhooks/701/tok-st-v2");

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/seasons/" + season.getId() + "/post-standings")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "data-incomplete"))
				.andExpect(flash().attribute("errorMessage", "Phase is required."));
	}

	@Test
	void givenTransientException_whenPostStandings_thenTransientFlash() throws Exception {
		Season season = seedSeason("V3", "https://discord.com/api/webhooks/702/tok-st-v3");
		SeasonPhase phase = season.getPhases().get(0);
		doThrow(new DiscordTransientException("boom", new RuntimeException()))
				.when(discordPostService).postStandings(any(Season.class), any(SeasonPhase.class));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/seasons/" + season.getId() + "/post-standings")
						.param("phaseId", phase.getId().toString())
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "transient"));
	}

	@Test
	void givenAuthException_whenPostStandings_thenAuthFlash() throws Exception {
		Season season = seedSeason("V4", "https://discord.com/api/webhooks/703/tok-st-v4");
		SeasonPhase phase = season.getPhases().get(0);
		doThrow(new DiscordAuthException("boom", new RuntimeException()))
				.when(discordPostService).postStandings(any(Season.class), any(SeasonPhase.class));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/seasons/" + season.getId() + "/post-standings")
						.param("phaseId", phase.getId().toString())
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "auth"));
	}

	@Test
	void givenNotFoundException_whenPostStandings_thenNotFoundFlash() throws Exception {
		Season season = seedSeason("V5", "https://discord.com/api/webhooks/704/tok-st-v5");
		SeasonPhase phase = season.getPhases().get(0);
		doThrow(new DiscordNotFoundException("boom", new RuntimeException()))
				.when(discordPostService).postStandings(any(Season.class), any(SeasonPhase.class));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/seasons/" + season.getId() + "/post-standings")
						.param("phaseId", phase.getId().toString())
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "not-found"));
	}

	@Test
	void givenBusinessRuleException_whenPostStandings_thenDataIncompleteFlash() throws Exception {
		Season season = seedSeason("V6", "https://discord.com/api/webhooks/705/tok-st-v6");
		SeasonPhase phase = season.getPhases().get(0);
		doThrow(new BusinessRuleException("Link a standings forum-thread above first"))
				.when(discordPostService).postStandings(any(Season.class), any(SeasonPhase.class));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/seasons/" + season.getId() + "/post-standings")
						.param("phaseId", phase.getId().toString())
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "data-incomplete"));
	}

	@Test
	void givenWebhookConfigured_whenEditGet_thenModelContainsStandingsEnrichment() throws Exception {
		Season season = seedSeason("V7", "https://discord.com/api/webhooks/706/tok-st-v7");

		mockMvc.perform(MockMvcRequestBuilders.get("/admin/seasons/" + season.getId() + "/edit"))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("allPhases"))
				.andExpect(model().attribute("canPostStandings", true))
				.andExpect(model().attributeExists("standingsPostByPhase"))
				.andExpect(model().attributeExists("standingsStaleByPhase"));
	}
}
