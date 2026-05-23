package org.ctc.admin.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.TestHelper;
import org.ctc.admin.service.ProvisionalScoresGraphicService;
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
class MatchControllerProvisionalPostIT {

	private static final byte[] PNG_BYTES = new byte[] {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};

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
	RaceResultRepository raceResultRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@MockitoBean
	ProvisionalScoresGraphicService provisionalScoresGraphicService;

	@BeforeEach
	void resetState() throws Exception {
		wm.resetAll();
		discordPostRepository.deleteAll();
		when(provisionalScoresGraphicService.generateProvisional(any(Race.class), anyInt())).thenReturn(PNG_BYTES);
	}

	@AfterEach
	void cleanup() {
		discordPostRepository.deleteAll();
	}

	private Match seedMatch(String suffix, String webhookUrl, int racesWithResults) {
		Season season = helper.createSeason("PP Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-PP-" + suffix, 0);
		Team home = helper.createTeam("PP Home " + suffix, "pp-h" + suffix);
		Team away = helper.createTeam("PP Away " + suffix, "pp-a" + suffix);
		season.addTeam(home);
		season.addTeam(away);
		Match match = helper.createMatch(md, home, away);
		Driver driver = helper.createDriver("PSN-PP-" + suffix, "Driver PP-" + suffix);
		for (int i = 0; i < 2; i++) {
			Race race = helper.createRace(md, match);
			if (i < racesWithResults) {
				RaceResult result = new RaceResult(race, driver, 1, 1, false);
				raceResultRepository.save(result);
				race.getResults().add(result);
			}
			match.getRaces().add(race);
		}
		match.setDiscordChannelId("chan-pp-" + suffix);
		match.setDiscordChannelWebhookUrl(webhookUrl);
		return matchRepository.save(match);
	}

	@Test
	void givenAtLeastOneRaceHasResults_whenPostProvisional_thenRedirectAndSuccessFlash() throws Exception {
		String webhookPath = "/webhooks/930/tok-pp1";
		Match match = seedMatch("PP1", wm.baseUrl() + webhookPath, 1);
		wm.stubFor(WireMock.post(WireMock.urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-pp1\",\"channel_id\":\"chan-pp-PP1\"}")));

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matches/" + match.getId() + "/post-provisional")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "Provisional scores posted."));
	}

	@Test
	void givenNoRaceResults_whenPostProvisional_thenDataIncompleteFlash() throws Exception {
		Match match = seedMatch("PP2", wm.baseUrl() + "/webhooks/931/tok-pp2", 0);

		mockMvc.perform(MockMvcRequestBuilders.post("/admin/matches/" + match.getId() + "/post-provisional")
						.with(csrf()))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorMessage", "Provisional needs at least one completed race"))
				.andExpect(flash().attribute("errorCategory", "data-incomplete"));
	}
}
