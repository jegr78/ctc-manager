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
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.exception.DiscordAuthException;
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
class DiscordChannelServicePermissionAuditFailIT {

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

	private static final String BOT_USER_ID = "bot-id-42";

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
		Season season = helper.createSeason("Audit Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-1-" + suffix, 0);
		Team home = helper.createTeam("Home " + suffix, "h" + suffix);
		Team away = helper.createTeam("Away " + suffix, "a" + suffix);
		home.setDiscordRoleId("100");
		away.setDiscordRoleId("200");
		teamRepository.save(home);
		teamRepository.save(away);
		return helper.createMatch(md, home, away);
	}

	private void stubHappyPathCreate() {
		wm.stubFor(post(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.willReturn(okJson(
						"{\"id\":\"c1\",\"name\":\"md1-h-vs-a\",\"type\":0,\"parent_id\":\"cat1\"}")));
		wm.stubFor(post(urlPathEqualTo("/api/v10/channels/c1/webhooks"))
				.willReturn(okJson(
						"{\"id\":\"w1\",\"token\":\"tok-abc\","
								+ "\"url\":\"https://discord.com/api/webhooks/w1/tok-abc\",\"channel_id\":\"c1\"}")));
		wm.stubFor(delete(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(aResponse().withStatus(204)));
	}

	@Test
	void givenFetchChannelReturnsFiveOverwritesWithExtraRoleVIEW_whenAudit_thenAuthExceptionAndCleanupDeleteAndDbRollback() {
		// given — fetchChannel returns 5 overwrites (size mismatch — expected 4)
		seedConfig();
		Match match = seedMatch("E");
		stubHappyPathCreate();
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(okJson(
						"{\"id\":\"c1\",\"name\":\"md1-h-vs-a\",\"type\":0,\"parent_id\":\"cat1\","
								+ "\"permission_overwrites\":["
								+ "{\"id\":\"g1\",\"type\":0,\"allow\":\"0\",\"deny\":\"1024\"},"
								+ "{\"id\":\"100\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
								+ "{\"id\":\"200\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
								+ "{\"id\":\"999\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
								+ "{\"id\":\"" + BOT_USER_ID + "\",\"type\":1,\"allow\":\"1024\",\"deny\":\"0\"}"
								+ "]}")));

		// when / then
		assertThatThrownBy(() -> service.createMatchChannel(match))
				.isInstanceOf(DiscordAuthException.class)
				.hasMessage(DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE);

		wm.verify(deleteRequestedFor(urlPathEqualTo("/api/v10/channels/c1")));
		assertThat(matchRepository.findById(match.getId()).orElseThrow().getDiscordChannelId()).isNull();
	}

	@Test
	void givenFetchChannelReturnsFourOverwritesWithWrongRoleSet_whenAudit_thenAuthExceptionAndCleanupDeleteAndDbRollback() {
		// given — fetchChannel returns 4 overwrites but the role set is {100, 999} not {100, 200}
		seedConfig();
		Match match = seedMatch("S");
		stubHappyPathCreate();
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(okJson(
						"{\"id\":\"c1\",\"name\":\"md1-h-vs-a\",\"type\":0,\"parent_id\":\"cat1\","
								+ "\"permission_overwrites\":["
								+ "{\"id\":\"g1\",\"type\":0,\"allow\":\"0\",\"deny\":\"1024\"},"
								+ "{\"id\":\"100\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
								+ "{\"id\":\"999\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
								+ "{\"id\":\"" + BOT_USER_ID + "\",\"type\":1,\"allow\":\"1024\",\"deny\":\"0\"}"
								+ "]}")));

		// when / then
		assertThatThrownBy(() -> service.createMatchChannel(match))
				.isInstanceOf(DiscordAuthException.class)
				.hasMessage(DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE);

		wm.verify(deleteRequestedFor(urlPathEqualTo("/api/v10/channels/c1")));
		assertThat(matchRepository.findById(match.getId()).orElseThrow().getDiscordChannelId()).isNull();
	}

	@Test
	void givenFetchChannelReturnsFourRoleOverwritesNoBotMember_whenAudit_thenAuthExceptionAndCleanupDeleteAndDbRollback() {
		// given — 4 type=0 (role) overwrites with the 4th having allow=0 + deny=0 (noise entry).
		// Size check passes (4 == 4). Role-set check passes ({100, 200} matches the team roles —
		// the noise entry's allow=0 means it contributes no VIEW bit and is excluded from the set).
		// The new member-set check fails because membersWithView is empty, not {botUserId}.
		seedConfig();
		Match match = seedMatch("M");
		stubHappyPathCreate();
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(okJson(
						"{\"id\":\"c1\",\"name\":\"md1-h-vs-a\",\"type\":0,\"parent_id\":\"cat1\","
								+ "\"permission_overwrites\":["
								+ "{\"id\":\"g1\",\"type\":0,\"allow\":\"0\",\"deny\":\"1024\"},"
								+ "{\"id\":\"100\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
								+ "{\"id\":\"200\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
								+ "{\"id\":\"noise\",\"type\":0,\"allow\":\"0\",\"deny\":\"0\"}"
								+ "]}")));

		// when / then
		assertThatThrownBy(() -> service.createMatchChannel(match))
				.isInstanceOf(DiscordAuthException.class)
				.hasMessage(DiscordApiExceptionMapper.AUDIT_FAIL_MESSAGE);

		wm.verify(deleteRequestedFor(urlPathEqualTo("/api/v10/channels/c1")));
		assertThat(matchRepository.findById(match.getId()).orElseThrow().getDiscordChannelId()).isNull();
	}
}
