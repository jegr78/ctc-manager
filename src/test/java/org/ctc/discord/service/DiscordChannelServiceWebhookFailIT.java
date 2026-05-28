package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.TestHelper;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class DiscordChannelServiceWebhookFailIT {

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
	DiscordChannelService service;

	@Autowired
	DiscordGlobalConfigRepository configRepo;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	TeamRepository teamRepository;

	@Autowired
	TestHelper helper;

	private static final String BOT_USER_ID = "bot-id-99";

	@BeforeEach
	void resetWireMock() {
		wm.resetAll();
		stubBotIdentity();
	}

	private void stubBotIdentity() {
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(okJson(
						"{\"id\":\"" + BOT_USER_ID + "\",\"username\":\"CTC-Bot\",\"discriminator\":\"0001\"}")));
	}

	private void seedConfig() {
		DiscordGlobalConfig cfg = configRepo.findFirstByOrderByIdAsc();
		if (cfg == null) {
			cfg = new DiscordGlobalConfig();
		}
		cfg.setGuildId("g1");
		cfg.setCurrentMatchCategoryId("cat1");
		configRepo.save(cfg);
	}

	private Match seedMatch(String suffix) {
		Season season = helper.createSeason("Webhook Fail Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-1-" + suffix, 0);
		Team home = helper.createTeam("Home " + suffix, "h" + suffix);
		Team away = helper.createTeam("Away " + suffix, "a" + suffix);
		home.setDiscordRoleId("100");
		away.setDiscordRoleId("200");
		teamRepository.save(home);
		teamRepository.save(away);
		return helper.createMatch(md, home, away);
	}

	@Test
	void givenWebhookCreateFails_whenCreateMatchChannel_thenCleanupDeleteIsIssued() {
		// given — channel creation succeeds, webhook creation returns 5xx
		seedConfig();
		Match match = seedMatch("W");
		wm.stubFor(post(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.willReturn(okJson(
						"{\"id\":\"c1\",\"name\":\"md1-rs-h-vs-a\",\"type\":0,\"parent_id\":\"cat1\"}")));
		wm.stubFor(post(urlPathEqualTo("/api/v10/channels/c1/webhooks"))
				.willReturn(aResponse().withStatus(500)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"message\":\"upstream error\",\"code\":0}")));
		wm.stubFor(delete(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(aResponse().withStatus(204)));

		// when / then
		assertThatThrownBy(() -> service.createMatchChannel(match))
				.isInstanceOf(DiscordApiException.class);

		wm.verify(deleteRequestedFor(urlPathEqualTo("/api/v10/channels/c1")));
		assertThat(matchRepository.findById(match.getId()).orElseThrow().getDiscordChannelId()).isNull();
	}
}
