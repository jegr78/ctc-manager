package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.TestHelper;
import org.ctc.discord.exception.DiscordAuthException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@ExtendWith(OutputCaptureExtension.class)
@Transactional
class DiscordChannelServiceCleanupFailIT {

	private static final String WEBHOOK_SECRET = "tok-cleanup-secret-xyz";

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
	TeamRepository teamRepository;

	@Autowired
	TestHelper helper;

	@BeforeEach
	void resetWireMock() {
		wm.resetAll();
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

	private Match seedMatch() {
		Season season = helper.createSeason("Cleanup Season");
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-1-C", 0);
		Team home = helper.createTeam("Home C", "hc");
		Team away = helper.createTeam("Away C", "ac");
		home.setDiscordRoleId("100");
		away.setDiscordRoleId("200");
		teamRepository.save(home);
		teamRepository.save(away);
		return helper.createMatch(md, home, away);
	}

	@Test
	void givenAuditFailAndDeleteReturns500_whenCreateMatchChannel_thenComposedMessageAndWarnLogCaptured(
			CapturedOutput output) {
		// given — audit fails AND cleanup DELETE returns 500
		seedConfig();
		Match match = seedMatch();

		wm.stubFor(post(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.willReturn(okJson(
						"{\"id\":\"c1\",\"name\":\"md1-hc-vs-ac\",\"type\":0,\"parent_id\":\"cat1\"}")));
		wm.stubFor(post(urlPathEqualTo("/api/v10/channels/c1/webhooks"))
				.willReturn(okJson(
						"{\"id\":\"w1\",\"token\":\"" + WEBHOOK_SECRET + "\","
								+ "\"url\":\"https://discord.com/api/webhooks/w1/" + WEBHOOK_SECRET + "\","
								+ "\"channel_id\":\"c1\"}")));
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(okJson(
						"{\"id\":\"c1\",\"name\":\"md1-hc-vs-ac\",\"type\":0,\"parent_id\":\"cat1\","
								+ "\"permission_overwrites\":["
								+ "{\"id\":\"g1\",\"type\":0,\"allow\":\"0\",\"deny\":\"1024\"},"
								+ "{\"id\":\"100\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
								+ "{\"id\":\"200\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
								+ "{\"id\":\"999\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"}"
								+ "]}")));
		wm.stubFor(delete(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(aResponse().withStatus(500)));

		// when / then
		assertThatThrownBy(() -> service.createMatchChannel(match))
				.isInstanceOf(DiscordAuthException.class)
				.hasMessageContaining("Cleanup failed: please manually delete channel c1 via Discord.");

		// and — WARN log captured with channel ID, no webhook secret echo
		assertThat(output.getOut()).contains("Audit-fail cleanup DELETE failed for channel c1:");
		assertThat(output.getOut()).doesNotContain(WEBHOOK_SECRET);
	}
}
