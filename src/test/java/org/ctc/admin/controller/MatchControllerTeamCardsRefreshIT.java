package org.ctc.admin.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.TestHelper;
import org.ctc.admin.service.TeamCardService;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class MatchControllerTeamCardsRefreshIT {

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

	@org.springframework.beans.factory.annotation.Value("${app.upload-dir:uploads}")
	private String uploadDir;

	@Autowired
	private TestHelper helper;

	@Autowired
	private SeasonRepository seasonRepository;

	@Autowired
	private MatchRepository matchRepository;

	@Autowired
	private DiscordPostRepository discordPostRepository;

	@MockitoBean
	private TeamCardService teamCardService;

	@BeforeEach
	void reset() throws Exception {
		wm.resetAll();
		discordPostRepository.deleteAll();
		when(teamCardService.generateCard(any(SeasonTeam.class))).thenReturn("/uploads/x.png");
		when(teamCardService.cardExists(any(SeasonTeam.class))).thenReturn(true);
		when(teamCardService.getCardPath(any(SeasonTeam.class)))
				.thenReturn("/uploads/team-cards/dummy.png");
	}

	@AfterEach
	void cleanup() {
		discordPostRepository.deleteAll();
	}

	private Match seedMatch(String suffix, String webhookUrl) {
		Season season = helper.createSeason("Refresh " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-R-" + suffix, 0);
		Team home = helper.createTeam("R Home " + suffix, "r-h" + suffix);
		Team away = helper.createTeam("R Away " + suffix, "r-a" + suffix);
		season.addTeam(home);
		season.addTeam(away);
		seasonRepository.save(season);
		Match match = helper.createMatch(md, home, away);
		match.setDiscordChannelId("chan-r-" + suffix);
		match.setDiscordChannelWebhookUrl(webhookUrl);
		return matchRepository.save(match);
	}

	@Test
	void givenSuccessfulRefresh_whenPost_thenRedirectAndFlashSuccessAndGenerateBothCards() throws Exception {
		String webhookPath = "/webhooks/600/tok-r1";
		Match match = seedMatch("R1", wm.baseUrl() + webhookPath);
		java.nio.file.Path file = java.nio.file.Path.of(uploadDir, "team-cards/dummy.png").toAbsolutePath().normalize();
		java.nio.file.Files.createDirectories(file.getParent());
		java.nio.file.Files.write(file, new byte[] {0x01});
		try {
			wm.stubFor(WireMock.post(urlPathEqualTo(webhookPath))
					.willReturn(okJson("{\"id\":\"msg-r1\",\"channel_id\":\"chan-r-R1\"}")));

			mockMvc.perform(MockMvcRequestBuilders.post("/admin/matches/" + match.getId() + "/refresh-team-cards").with(csrf()))
					.andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("successMessage", "Team cards regenerated and re-posted."));

			verify(teamCardService, times(2)).generateCard(any(SeasonTeam.class));
		} finally {
			java.nio.file.Files.deleteIfExists(file);
		}
	}

	@Test
	void givenWireMock5xx_whenRefresh_thenTransientFlash() throws Exception {
		String webhookPath = "/webhooks/601/tok-r2";
		Match match = seedMatch("R2", wm.baseUrl() + webhookPath);
		java.nio.file.Path file = java.nio.file.Path.of(uploadDir, "team-cards/dummy.png").toAbsolutePath().normalize();
		java.nio.file.Files.createDirectories(file.getParent());
		java.nio.file.Files.write(file, new byte[] {0x01});
		try {
			wm.stubFor(WireMock.post(urlPathEqualTo(webhookPath))
					.willReturn(aResponse().withStatus(500).withBody("server error")));

			mockMvc.perform(MockMvcRequestBuilders.post("/admin/matches/" + match.getId() + "/refresh-team-cards").with(csrf()))
					.andExpect(status().is3xxRedirection())
					.andExpect(flash().attributeExists("errorMessage"))
					.andExpect(flash().attribute("errorCategory", "transient"));
		} finally {
			java.nio.file.Files.deleteIfExists(file);
		}
	}

	@Test
	void givenPostEndpoint_whenSuccess_thenSuccessFlash() throws Exception {
		String webhookPath = "/webhooks/602/tok-r3";
		Match match = seedMatch("R3", wm.baseUrl() + webhookPath);
		java.nio.file.Path file = java.nio.file.Path.of(uploadDir, "team-cards/dummy.png").toAbsolutePath().normalize();
		java.nio.file.Files.createDirectories(file.getParent());
		java.nio.file.Files.write(file, new byte[] {0x01});
		try {
			wm.stubFor(WireMock.post(urlPathEqualTo(webhookPath))
					.willReturn(okJson("{\"id\":\"msg-r3\",\"channel_id\":\"chan-r-R3\"}")));

			mockMvc.perform(MockMvcRequestBuilders.post("/admin/matches/" + match.getId() + "/post-team-cards").with(csrf()))
					.andExpect(status().is3xxRedirection())
					.andExpect(flash().attribute("successMessage", "Team cards posted."));

			assertThat(discordPostRepository.findAll()).isNotEmpty();
		} finally {
			java.nio.file.Files.deleteIfExists(file);
		}
	}
}
