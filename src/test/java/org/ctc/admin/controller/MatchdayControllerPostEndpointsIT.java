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
import org.ctc.discord.exception.DiscordMissingPermissionsException;
import org.ctc.discord.exception.DiscordNotFoundException;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.ctc.discord.service.DiscordPostService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
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
class MatchdayControllerPostEndpointsIT {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestHelper helper;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	MatchdayRepository matchdayRepository;

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
		when(discordPostService.resolveAnnouncementChannelId(any(String.class))).thenReturn("ch-md");
		when(discordPostService.canPostMatchdayResults(any(), any()))
				.thenReturn(new MatchPreviewPreFlightResult(true, null));
		when(discordPostService.canPostPowerRankings(any(), any()))
				.thenReturn(new MatchPreviewPreFlightResult(true, null));
	}

	@AfterEach
	void cleanup() {
		discordPostRepository.deleteAll();
	}

	private Matchday seedMatchday(String suffix, String webhookUrl) {
		Season season = helper.createSeason("Ctrl MD " + suffix);
		season.setDiscordRaceResultsThreadId("thread-ctrl-" + suffix);
		seasonRepository.save(season);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-Ctrl-" + suffix, 0);
		Team home = helper.createTeam("Ctrl MD Home " + suffix, "ch" + suffix);
		Team away = helper.createTeam("Ctrl MD Away " + suffix, "ca" + suffix);
		Match match = helper.createMatch(md, home, away);
		match.setHomeScore(3);
		match.setAwayScore(2);
		matchRepository.save(match);
		md.getMatches().add(match);
		matchdayRepository.save(md);

		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setRaceResultsForumWebhookUrl(webhookUrl);
		globalConfigRepository.save(cfg);
		return md;
	}

	@Test
	void givenComplete_whenPostMatchdayResults_thenRedirectAndSuccessFlash() throws Exception {
		Matchday md = seedMatchday("R1", "https://discord.com/api/webhooks/700/tok-ctrl-r1");

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matchdays/" + md.getId() + "/post-matchday-results")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "Match day results posted."));

		verify(discordPostService).postMatchdayResults(any(Matchday.class));
	}

	@Test
	void givenComplete_whenPostPowerRankings_thenRedirectAndSuccessFlash() throws Exception {
		Matchday md = seedMatchday("R2", "https://discord.com/api/webhooks/701/tok-ctrl-r2");

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matchdays/" + md.getId() + "/post-power-rankings")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "Power rankings posted."));

		verify(discordPostService).postPowerRankings(any(Matchday.class));
	}

	@Test
	void givenTransientException_whenPostMatchdayResults_thenTransientFlash() throws Exception {
		Matchday md = seedMatchday("R3", "https://discord.com/api/webhooks/702/tok-ctrl-r3");
		doThrow(new DiscordTransientException("boom", new RuntimeException()))
				.when(discordPostService).postMatchdayResults(any(Matchday.class));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matchdays/" + md.getId() + "/post-matchday-results")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "transient"));
	}

	@Test
	void givenAuthException_whenPostMatchdayResults_thenAuthFlash() throws Exception {
		Matchday md = seedMatchday("R4", "https://discord.com/api/webhooks/703/tok-ctrl-r4");
		doThrow(new DiscordAuthException("boom", new RuntimeException()))
				.when(discordPostService).postMatchdayResults(any(Matchday.class));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matchdays/" + md.getId() + "/post-matchday-results")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "auth"));
	}

	@Test
	void givenNotFoundException_whenPostMatchdayResults_thenNotFoundFlash() throws Exception {
		Matchday md = seedMatchday("R5", "https://discord.com/api/webhooks/704/tok-ctrl-r5");
		doThrow(new DiscordNotFoundException("boom", new RuntimeException()))
				.when(discordPostService).postMatchdayResults(any(Matchday.class));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matchdays/" + md.getId() + "/post-matchday-results")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "not-found"));
	}

	@Test
	void givenMissingPermissionsException_whenPostPowerRankings_thenMissingPermissionsFlash() throws Exception {
		Matchday md = seedMatchday("R6", "https://discord.com/api/webhooks/705/tok-ctrl-r6");
		doThrow(new DiscordMissingPermissionsException("boom", new RuntimeException()))
				.when(discordPostService).postPowerRankings(any(Matchday.class));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matchdays/" + md.getId() + "/post-power-rankings")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "missing-permissions"));
	}

	@Test
	void givenBusinessRuleException_whenPostMatchdayResults_thenDataIncompleteFlash() throws Exception {
		Matchday md = seedMatchday("R7", "https://discord.com/api/webhooks/706/tok-ctrl-r7");
		doThrow(new BusinessRuleException("Mark all matches as final first"))
				.when(discordPostService).postMatchdayResults(any(Matchday.class));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matchdays/" + md.getId() + "/post-matchday-results")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "data-incomplete"));
	}

	@Test
	void givenThreadAndWebhookConfigured_whenDetailGet_thenModelHasDiscordEnrichment() throws Exception {
		Matchday md = seedMatchday("R8", "https://discord.com/api/webhooks/707/tok-ctrl-r8");

		mockMvc.perform(MockMvcRequestBuilders.get("/admin/matchdays/" + md.getId()))
				.andExpect(status().isOk())
				.andExpect(model().attribute("matchdayDiscordActive", true))
				.andExpect(model().attribute("canPostMatchdayResults", true))
				.andExpect(model().attribute("canPostPowerRankings", true))
				.andExpect(model().attribute("matchdayOverviewPost", (Object) null))
				.andExpect(model().attribute("powerRankingsPost", (Object) null))
				.andExpect(model().attribute("matchdayResultsStale", false))
				.andExpect(model().attribute("powerRankingsStale", false));
	}

	@Test
	void givenNoThreadConfigured_whenDetailGet_thenMatchdayDiscordActiveFalse() throws Exception {
		Season season = helper.createSeason("Ctrl MD R9");
		season.setDiscordRaceResultsThreadId(null);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-Ctrl-R9", 0);
		matchdayRepository.save(md);

		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setRaceResultsForumWebhookUrl("https://discord.com/api/webhooks/708/tok-ctrl-r9");
		globalConfigRepository.save(cfg);

		mockMvc.perform(MockMvcRequestBuilders.get("/admin/matchdays/" + md.getId()))
				.andExpect(status().isOk())
				.andExpect(model().attribute("matchdayDiscordActive", false));
	}
}
