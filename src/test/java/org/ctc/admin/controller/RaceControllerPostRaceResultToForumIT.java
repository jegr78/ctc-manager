package org.ctc.admin.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.TestHelper;
import org.ctc.admin.service.ResultsGraphicService;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class RaceControllerPostRaceResultToForumIT {

	private static final byte[] PNG_BYTES = new byte[] {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};
	private static final String THREAD_ID = "555555555555555555";

	@RegisterExtension
	static WireMockExtension wm = WireMockExtension.newInstance()
			.options(options().dynamicPort())
			.build();

	@DynamicPropertySource
	static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
		registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
		registry.add("app.discord.bot-token", () -> "test-bot-token");
		registry.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
		registry.add("app.discord.rate-limit.jitter-ms", () -> "0");
		registry.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestHelper helper;

	@Autowired
	SeasonRepository seasonRepository;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	RaceResultRepository raceResultRepository;

	@Autowired
	DiscordGlobalConfigRepository configRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@MockitoBean
	ResultsGraphicService resultsGraphicService;

	@BeforeEach
	void resetState() throws Exception {
		wm.resetAll();
		discordPostRepository.deleteAll();
		when(resultsGraphicService.generateResultsBytes(any(Race.class))).thenReturn(PNG_BYTES);
	}

	@AfterEach
	void cleanup() {
		discordPostRepository.deleteAll();
	}

	private record Seeded(Race race, Season season) {
	}

	private Seeded seedRace(String suffix, boolean withResults, boolean withThreadId, String webhookPath) {
		Season season = helper.createSeason("RCF " + suffix);
		if (withThreadId) {
			season.setDiscordRaceResultsThreadId(THREAD_ID);
			seasonRepository.save(season);
		}
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-RCF-" + suffix, 0);
		Team home = helper.createTeam("RCF Home " + suffix, "rcfh" + suffix);
		Team away = helper.createTeam("RCF Away " + suffix, "rcfa" + suffix);
		season.addTeam(home);
		season.addTeam(away);
		Match match = helper.createMatch(md, home, away);
		Race race = helper.createRace(md, match);
		if (withResults) {
			Driver driver = helper.createDriver("PSN-RCF-" + suffix, "Driver RCF-" + suffix);
			RaceResult result = new RaceResult(race, driver, 1, 1, false);
			raceResultRepository.save(result);
			race.getResults().add(result);
		}
		match.getRaces().add(race);
		matchRepository.save(match);

		DiscordGlobalConfig config = configRepository.findFirstByOrderByIdAsc();
		if (config == null) {
			config = new DiscordGlobalConfig();
		}
		if (webhookPath != null) {
			config.setRaceResultsForumWebhookUrl(wm.baseUrl() + webhookPath);
		} else {
			config.setRaceResultsForumWebhookUrl("");
		}
		configRepository.save(config);
		return new Seeded(race, season);
	}

	@Test
	void givenAllPreFlightGreen_whenPost_thenRedirectAndSuccessFlash() throws Exception {
		String webhookPath = "/webhooks/800/abc";
		Seeded s = seedRace("OK", true, true, webhookPath);
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + THREAD_ID))
				.willReturn(okJson("{\"id\":\"" + THREAD_ID + "\",\"name\":\"t\",\"type\":11,"
						+ "\"thread_metadata\":{\"archived\":false}}")));
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-rcf-1\",\"channel_id\":\"800\"}")));

		mockMvc.perform(MockMvcRequestBuilders
						.post("/admin/races/" + s.race().getId() + "/post-race-result-to-forum")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "Race result posted to forum-thread."));

		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
				.withQueryParam("thread_id", equalTo(THREAD_ID)));
	}

	@Test
	void givenNoResults_whenPost_thenBusinessRuleFlash() throws Exception {
		String webhookPath = "/webhooks/801/abc";
		Seeded s = seedRace("NR", false, true, webhookPath);

		mockMvc.perform(MockMvcRequestBuilders
						.post("/admin/races/" + s.race().getId() + "/post-race-result-to-forum")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "data-incomplete"));
	}

	@Test
	void givenNoThreadLinked_whenPost_thenBusinessRuleFlash() throws Exception {
		String webhookPath = "/webhooks/802/abc";
		Seeded s = seedRace("NT", true, false, webhookPath);

		mockMvc.perform(MockMvcRequestBuilders
						.post("/admin/races/" + s.race().getId() + "/post-race-result-to-forum")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "data-incomplete"));
	}

	@Test
	void givenNoWebhookConfigured_whenPost_thenBusinessRuleFlash() throws Exception {
		Seeded s = seedRace("NW", true, true, null);

		mockMvc.perform(MockMvcRequestBuilders
						.post("/admin/races/" + s.race().getId() + "/post-race-result-to-forum")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "data-incomplete"));
	}

	@Test
	void givenExistingPost_whenPostAgain_thenPatchWithThreadId() throws Exception {
		String webhookPath = "/webhooks/803/abc";
		Seeded s = seedRace("RP", true, true, webhookPath);
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + THREAD_ID))
				.willReturn(okJson("{\"id\":\"" + THREAD_ID + "\",\"name\":\"t\",\"type\":11,"
						+ "\"thread_metadata\":{\"archived\":false}}")));
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-rcf-2\",\"channel_id\":\"803\"}")));
		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-rcf-2"))
				.willReturn(okJson("{\"id\":\"msg-rcf-2\",\"channel_id\":\"803\"}")));

		mockMvc.perform(MockMvcRequestBuilders
						.post("/admin/races/" + s.race().getId() + "/post-race-result-to-forum")
						.with(csrf()))
				.andExpect(status().is3xxRedirection());
		mockMvc.perform(MockMvcRequestBuilders
						.post("/admin/races/" + s.race().getId() + "/post-race-result-to-forum")
						.with(csrf()))
				.andExpect(status().is3xxRedirection());

		wm.verify(patchRequestedFor(urlPathEqualTo(webhookPath + "/messages/msg-rcf-2"))
				.withQueryParam("thread_id", equalTo(THREAD_ID)));
	}

	@Test
	void givenArchivedThread_whenPost_thenUnarchiveBeforePost() throws Exception {
		String webhookPath = "/webhooks/804/abc";
		Seeded s = seedRace("AR", true, true, webhookPath);
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + THREAD_ID))
				.willReturn(okJson("{\"id\":\"" + THREAD_ID + "\",\"name\":\"t\",\"type\":11,"
						+ "\"thread_metadata\":{\"archived\":true}}")));
		wm.stubFor(patch(urlPathEqualTo("/api/v10/channels/" + THREAD_ID))
				.willReturn(okJson("{\"id\":\"" + THREAD_ID + "\",\"name\":\"t\",\"type\":11}")));
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-rcf-3\",\"channel_id\":\"804\"}")));

		mockMvc.perform(MockMvcRequestBuilders
						.post("/admin/races/" + s.race().getId() + "/post-race-result-to-forum")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "Race result posted to forum-thread."));

		wm.verify(getRequestedFor(urlPathEqualTo("/api/v10/channels/" + THREAD_ID)));
		wm.verify(patchRequestedFor(urlPathEqualTo("/api/v10/channels/" + THREAD_ID)));
	}

	@Test
	void whenGetDetail_thenForumPostModelAttrsPresent() throws Exception {
		String webhookPath = "/webhooks/805/abc";
		Seeded s = seedRace("MD", true, true, webhookPath);

		mockMvc.perform(MockMvcRequestBuilders.get("/admin/races/" + s.race().getId()))
				.andExpect(status().isOk())
				.andExpect(model().attribute("canPostRaceResultToForum", true))
				.andExpect(model().attribute("forumPostDisabledReason", (Object) null));
	}

	@Test
	void givenNoResultsAndNoThread_whenGetDetail_thenDisabledReasonIsResultsMissing() throws Exception {
		Seeded s = seedRace("D1", false, false, null);

		mockMvc.perform(MockMvcRequestBuilders.get("/admin/races/" + s.race().getId()))
				.andExpect(status().isOk())
				.andExpect(model().attribute("canPostRaceResultToForum", false))
				.andExpect(model().attribute("forumPostDisabledReason", "No race results yet"));
	}
}
