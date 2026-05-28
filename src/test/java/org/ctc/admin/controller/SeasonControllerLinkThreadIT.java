package org.ctc.admin.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.SeasonRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class SeasonControllerLinkThreadIT {

	private static final String GUILD_ID = "111111111111111111";
	private static final String FORUM_RR = "222222222222222222";
	private static final String FORUM_ST = "333333333333333333";
	private static final String THREAD_ID = "444444444444444444";

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
	DiscordGlobalConfigRepository configRepository;

	@BeforeEach
	void resetState() {
		wm.resetAll();
		DiscordGlobalConfig config = configRepository.findFirstByOrderByIdAsc();
		if (config == null) {
			config = new DiscordGlobalConfig();
		}
		config.setGuildId(GUILD_ID);
		config.setRaceResultsForumChannelId(FORUM_RR);
		config.setStandingsForumChannelId(FORUM_ST);
		configRepository.save(config);
	}

	@Test
	void givenSeasonAndThreadId_whenLinkRaceResults_thenRedirectAndPersist() throws Exception {
		Season season = helper.createSeason("Link Test Season");

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/seasons/" + season.getId() + "/link-thread")
						.param("threadId", THREAD_ID)
						.param("type", "race-results")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "Thread linked."));

		Season reloaded = seasonRepository.findById(season.getId()).orElseThrow();
		assertThat(reloaded.getDiscordRaceResultsThreadId()).isEqualTo(THREAD_ID);
	}

	@Test
	void givenLinkedSeason_whenUnlinkStandings_thenRedirectAndClearField() throws Exception {
		Season season = helper.createSeason("Unlink Test Season");
		season.setDiscordStandingsThreadId(THREAD_ID);
		seasonRepository.save(season);

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/seasons/" + season.getId() + "/unlink-thread")
						.param("type", "standings")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "Thread unlinked."));

		Season reloaded = seasonRepository.findById(season.getId()).orElseThrow();
		assertThat(reloaded.getDiscordStandingsThreadId()).isNull();
	}

	@Test
	void givenUnknownType_whenLink_thenErrorFlash() throws Exception {
		Season season = helper.createSeason("Unknown Type Season");

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/seasons/" + season.getId() + "/link-thread")
						.param("threadId", THREAD_ID)
						.param("type", "bogus")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attributeExists("errorMessage"));
	}

	@Test
	void givenForumChannelsConfigured_whenGetEdit_thenPreloadsThreadOptions() throws Exception {
		Season season = helper.createSeason("Edit Options Season");
		stubForumListings();

		mockMvc.perform(MockMvcRequestBuilders.get("/admin/seasons/" + season.getId() + "/edit"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("discordIntegrationActive", true))
				.andExpect(model().attributeExists("raceResultsThreadOptions"))
				.andExpect(model().attributeExists("standingsThreadOptions"));
	}

	@Test
	void givenLinkedThreadInOptions_whenGetEdit_thenResolvesLinkedThread() throws Exception {
		Season season = helper.createSeason("Linked Thread Season");
		season.setDiscordRaceResultsThreadId("t1");
		seasonRepository.save(season);
		stubForumListings();

		mockMvc.perform(MockMvcRequestBuilders.get("/admin/seasons/" + season.getId() + "/edit"))
				.andExpect(status().isOk())
				.andExpect(model().attributeExists("linkedRaceResultsThread"));
	}

	@Test
	void givenNoForumChannels_whenGetEdit_thenIntegrationInactive() throws Exception {
		DiscordGlobalConfig config = configRepository.findFirstByOrderByIdAsc();
		config.setRaceResultsForumChannelId("");
		config.setStandingsForumChannelId("");
		configRepository.save(config);
		Season season = helper.createSeason("No Forum Season");

		mockMvc.perform(MockMvcRequestBuilders.get("/admin/seasons/" + season.getId() + "/edit"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("discordIntegrationActive", false));
	}

	private void stubForumListings() {
		String activePayload = """
				{
				  "threads": [
				    {"id":"t1","name":"Pinned RR","parent_id":"%s","flags":2,"last_message_id":"500"},
				    {"id":"t2","name":"Pinned ST","parent_id":"%s","flags":2,"last_message_id":"600"}
				  ]
				}
				""".formatted(FORUM_RR, FORUM_ST);
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/" + GUILD_ID + "/threads/active"))
				.willReturn(okJson(activePayload)));
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + FORUM_RR + "/threads/archived/public"))
				.willReturn(okJson("{\"threads\":[]}")));
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + FORUM_ST + "/threads/archived/public"))
				.willReturn(okJson("{\"threads\":[]}")));
	}
}
