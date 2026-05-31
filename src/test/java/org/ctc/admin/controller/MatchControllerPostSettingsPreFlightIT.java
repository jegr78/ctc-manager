package org.ctc.admin.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import org.ctc.TestHelper;
import org.ctc.admin.service.SettingsGraphicService;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
class MatchControllerPostSettingsPreFlightIT {

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
	MatchRepository matchRepository;

	@Autowired
	RaceRepository raceRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Value("${app.upload-dir:uploads}")
	String uploadDir;

	@MockitoBean
	SettingsGraphicService settingsGraphicService;

	@BeforeEach
	void resetState() throws Exception {
		wm.resetAll();
		discordPostRepository.deleteAll();
		Path dummy = Path.of(uploadDir, "team-cards/preflight-dummy.png").toAbsolutePath().normalize();
		Files.createDirectories(dummy.getParent());
		if (!Files.exists(dummy)) {
			Files.write(dummy, new byte[] {0x01});
		}
		when(settingsGraphicService.generateSettings(any(Race.class)))
				.thenReturn("/uploads/team-cards/preflight-dummy.png");
	}

	@AfterEach
	void cleanup() {
		discordPostRepository.deleteAll();
	}

	private Match seedMatch(String suffix, String webhookUrl, boolean allComplete) {
		Season season = helper.createSeason("PF Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-PF-" + suffix, 0);
		Team home = helper.createTeam("PF Home " + suffix, "pf-h" + suffix);
		Team away = helper.createTeam("PF Away " + suffix, "pf-a" + suffix);
		season.addTeam(home);
		season.addTeam(away);
		Match match = helper.createMatch(md, home, away);
		for (int i = 0; i < 2; i++) {
			Race race = helper.createRace(md, match);
			if (allComplete || i == 0) {
				race.setSettings(helper.completeRaceSettings(race));
				raceRepository.save(race);
			}
		}
		match.setDiscordChannelId("chan-pf-" + suffix);
		match.setDiscordChannelWebhookUrl(webhookUrl);
		return matchRepository.save(match);
	}

	@Test
	void givenAllComplete_whenPostSettings_thenRedirectAndSuccessFlash() throws Exception {
		String webhookPath = "/webhooks/820/tok-pf1";
		Match match = seedMatch("PF1", wm.baseUrl() + webhookPath, true);
		wm.stubFor(WireMock.post(WireMock.urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-pf1\",\"channel_id\":\"chan-pf-PF1\"}")));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matches/" + match.getId() + "/post-settings")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "Settings posted."));
	}

	@Test
	void givenPreFlightFails_whenPostSettings_thenDataIncompleteFlash() throws Exception {
		Match match = seedMatch("PF2", wm.baseUrl() + "/webhooks/821/tok-pf2", false);

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matches/" + match.getId() + "/post-settings")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorMessage", "Configure settings for all races first"))
				.andExpect(flash().attribute("errorCategory", "data-incomplete"));
	}

	@Test
	void givenWireMock5xx_whenPostSettings_thenTransientFlash() throws Exception {
		String webhookPath = "/webhooks/822/tok-pf3";
		Match match = seedMatch("PF3", wm.baseUrl() + webhookPath, true);
		wm.stubFor(WireMock.post(WireMock.urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(500).withBody("server error")));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matches/" + match.getId() + "/post-settings")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "transient"));
	}
}
