package org.ctc.discord.web;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.hamcrest.Matchers;
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
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class DiscordConfigControllerIT {

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
	private MockMvc mockMvc;

	@Autowired
	private DiscordGlobalConfigRepository configRepo;

	@BeforeEach
	void resetWireMockAndConfigSeed() {
		wm.resetAll();
		resetConfigSeedToEmptyDefaults();
	}

	@AfterEach
	void leaveDbInKnownEmptyState() {
		// Tests POST committed mutations (not @Transactional). Reset so downstream
		// IT classes in the same forked JVM (e.g. DiscordGlobalConfigRepositoryIT)
		// see a clean discord_global_config row.
		resetConfigSeedToEmptyDefaults();
	}

	private void resetConfigSeedToEmptyDefaults() {
		DiscordGlobalConfig seed = configRepo.findFirstByOrderByIdAsc();
		if (seed == null) {
			configRepo.save(new DiscordGlobalConfig());
			return;
		}
		seed.setGuildId("");
		seed.setAnnouncementWebhookUrl("");
		seed.setRaceResultsForumChannelId("");
		seed.setStandingsForumChannelId("");
		seed.setVsEmojiName("CTC");
		seed.setBotApplicationId(null);
		configRepo.save(seed);
	}

	@Test
	void givenSeedRow_whenGetView_thenRendersFormWithNotConfiguredBadges() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.get("/admin/discord-config"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/discord-config"))
				.andExpect(content().string(Matchers.containsString("Discord Config")))
				.andExpect(content().string(Matchers.containsString("not configured")));
	}

	@Test
	void givenCsrfAndValidForm_whenPostSave_thenRedirectsWithSuccessFlash() throws Exception {
		mockMvc.perform(MockMvcRequestBuilders.post("/admin/discord-config/save")
						.with(csrf())
						.param("guildId", "123456789012345678")
						.param("announcementWebhookUrl", "")
						.param("raceResultsForumChannelId", "")
						.param("standingsForumChannelId", "")
						.param("vsEmojiName", "CTC")
						.param("botApplicationId", ""))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "Configuration saved."));
	}

	@Test
	void givenCsrfAndWireMock200_whenPostTestConnection_thenFlashesSuccess() throws Exception {
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(okJson("{\"id\":\"42\",\"username\":\"CTC-Bot\",\"discriminator\":\"0001\"}")));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/discord-config/test-connection").with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "Connected as CTC-Bot"));
	}

	@Test
	void givenCsrfAndWireMock401_whenPostTestConnection_thenFlashesAuthCategory() throws Exception {
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(aResponse().withStatus(401)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"message\":\"Unauthorized\",\"code\":0}")));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/discord-config/test-connection").with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "auth"));
	}

	@Test
	void givenCsrfAndConfiguredGuildAndEmojiList_whenPostRefreshEmojiCache_thenFlashesSuccess() throws Exception {
		// Seed the guildId so refreshEmojiCache passes the precondition check
		mockMvc.perform(MockMvcRequestBuilders.post("/admin/discord-config/save")
						.with(csrf())
						.param("guildId", "123456789012345678")
						.param("vsEmojiName", "CTC"))
				.andExpect(status().is3xxRedirection());

		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/123456789012345678/emojis"))
				.willReturn(okJson("[{\"id\":\"100\",\"name\":\"flag_de\"},{\"id\":\"101\",\"name\":\"trophy\"}]")));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/discord-config/refresh-emoji-cache").with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "Emoji cache refreshed (2 entries)."));
	}
}
