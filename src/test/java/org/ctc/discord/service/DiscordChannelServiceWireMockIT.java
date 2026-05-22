package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.TestHelper;
import org.ctc.discord.DiscordPermissions;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.exception.BusinessRuleException;
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
class DiscordChannelServiceWireMockIT {

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

	private DiscordGlobalConfig seedConfig(String guildId, String categoryId) {
		DiscordGlobalConfig cfg = configRepo.findFirstByOrderByIdAsc();
		if (cfg == null) {
			cfg = new DiscordGlobalConfig();
		}
		cfg.setGuildId(guildId);
		cfg.setCurrentMatchCategoryId(categoryId);
		return configRepo.save(cfg);
	}

	private Match seedMatch(String suffix, String homeRoleId, String awayRoleId) {
		Season season = helper.createSeason("CS Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-1-" + suffix, 0);
		Team home = helper.createTeam("Home " + suffix, "home" + suffix);
		Team away = helper.createTeam("Away " + suffix, "away" + suffix);
		home.setDiscordRoleId(homeRoleId);
		away.setDiscordRoleId(awayRoleId);
		teamRepository.save(home);
		teamRepository.save(away);
		return helper.createMatch(md, home, away);
	}

	@Test
	void givenValidMatchAndConfig_whenCreateMatchChannel_thenDbWriteAnd4OverwritesIncludingBotMember() throws Exception {
		// given
		seedConfig("g1", "cat1");
		Match match = seedMatch("H", "100", "200");
		String botAllow = String.valueOf(DiscordPermissions.BOT_ALLOW_MASK);

		wm.stubFor(post(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.willReturn(okJson(
						"{\"id\":\"c1\",\"name\":\"md1-home-vs-away\",\"type\":0,\"parent_id\":\"cat1\"}")));
		wm.stubFor(post(urlPathEqualTo("/api/v10/channels/c1/webhooks"))
				.willReturn(okJson(
						"{\"id\":\"w1\",\"token\":\"tok-abc\","
								+ "\"url\":\"https://discord.com/api/webhooks/w1/tok-abc\",\"channel_id\":\"c1\"}")));
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(okJson(
						"{\"id\":\"c1\",\"name\":\"md1-home-vs-away\",\"type\":0,\"parent_id\":\"cat1\","
								+ "\"permission_overwrites\":["
								+ "{\"id\":\"g1\",\"type\":0,\"allow\":\"0\",\"deny\":\"1024\"},"
								+ "{\"id\":\"100\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
								+ "{\"id\":\"200\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
								+ "{\"id\":\"" + BOT_USER_ID + "\",\"type\":1,\"allow\":\"" + botAllow + "\",\"deny\":\"0\"}"
								+ "]}")));

		// when
		service.createMatchChannel(match);

		// then — DB write
		Match reloaded = matchRepository.findById(match.getId()).orElseThrow();
		assertThat(reloaded.getDiscordChannelId()).isEqualTo("c1");
		assertThat(reloaded.getDiscordChannelWebhookUrl())
				.isEqualTo("https://discord.com/api/webhooks/w1/tok-abc");

		// and — 4th permission_overwrite shape verified (type=member, bot-user-id, BOT_ALLOW_MASK)
		wm.verify(postRequestedFor(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.withRequestBody(matchingJsonPath("$.permission_overwrites[3].type", equalTo("1")))
				.withRequestBody(matchingJsonPath("$.permission_overwrites[3].id", equalTo(BOT_USER_ID)))
				.withRequestBody(matchingJsonPath("$.permission_overwrites[3].allow", equalTo(botAllow))));
		wm.verify(postRequestedFor(urlPathEqualTo("/api/v10/channels/c1/webhooks")));
		wm.verify(getRequestedFor(urlPathEqualTo("/api/v10/channels/c1")));
		wm.verify(exactly(0), deleteRequestedFor(urlPathMatching("/api/v10/channels/.*")));
	}

	@Test
	void givenMissingHomeRole_whenCreateMatchChannel_thenBusinessRuleExceptionNoOutboundCalls() {
		// given — config set but home team has no discordRoleId
		seedConfig("g1", "cat1");
		Match match = seedMatch("NR", null, "200");

		// when / then
		assertThatThrownBy(() -> service.createMatchChannel(match))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("both team Discord roles");

		wm.verify(exactly(0), postRequestedFor(urlPathMatching("/api/v10/.*")));
		assertThat(matchRepository.findById(match.getId()).orElseThrow().getDiscordChannelId()).isNull();
	}

	@Test
	void givenIntraTeamMatchWithSameEffectiveRole_whenCreateMatchChannel_thenThreeOverwritePayloadAndAuditPasses() throws Exception {
		// given — both teams resolve to the same effective Discord role (intra-team match,
		// e.g. VRX A vs VRX B both inheriting from parent VRX role)
		seedConfig("g1", "cat1");
		Match match = seedMatch("IT", "100", "100");
		String botAllow = String.valueOf(DiscordPermissions.BOT_ALLOW_MASK);

		wm.stubFor(post(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.willReturn(okJson(
						"{\"id\":\"c1\",\"name\":\"md1-home-vs-away\",\"type\":0,\"parent_id\":\"cat1\"}")));
		wm.stubFor(post(urlPathEqualTo("/api/v10/channels/c1/webhooks"))
				.willReturn(okJson(
						"{\"id\":\"w1\",\"token\":\"tok-abc\","
								+ "\"url\":\"https://discord.com/api/webhooks/w1/tok-abc\",\"channel_id\":\"c1\"}")));
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(okJson(
						"{\"id\":\"c1\",\"name\":\"md1-home-vs-away\",\"type\":0,\"parent_id\":\"cat1\","
								+ "\"permission_overwrites\":["
								+ "{\"id\":\"g1\",\"type\":0,\"allow\":\"0\",\"deny\":\"1024\"},"
								+ "{\"id\":\"100\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
								+ "{\"id\":\"" + BOT_USER_ID + "\",\"type\":1,\"allow\":\"" + botAllow + "\",\"deny\":\"0\"}"
								+ "]}")));

		// when
		service.createMatchChannel(match);

		// then — DB write
		Match reloaded = matchRepository.findById(match.getId()).orElseThrow();
		assertThat(reloaded.getDiscordChannelId()).isEqualTo("c1");

		// and — 3-overwrite payload (everyone-deny + 1 deduped team-role + 1 bot-member)
		wm.verify(postRequestedFor(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.withRequestBody(matchingJsonPath("$.permission_overwrites[1].id", equalTo("100")))
				.withRequestBody(matchingJsonPath("$.permission_overwrites[2].type", equalTo("1")))
				.withRequestBody(matchingJsonPath("$.permission_overwrites[2].id", equalTo(BOT_USER_ID))));
		wm.verify(exactly(0), deleteRequestedFor(urlPathMatching("/api/v10/channels/.*")));
	}

	@Test
	void givenWebhookCreationFails_whenCreateMatchChannel_thenServiceFailsDbUnchanged() {
		// given — channel created OK, but createWebhook returns 500 transient
		seedConfig("g1", "cat1");
		Match match = seedMatch("WF", "100", "200");

		wm.stubFor(post(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.willReturn(okJson(
						"{\"id\":\"c1\",\"name\":\"md1-home-vs-away\",\"type\":0,\"parent_id\":\"cat1\"}")));
		wm.stubFor(post(urlPathEqualTo("/api/v10/channels/c1/webhooks"))
				.willReturn(aResponse().withStatus(500)));

		// when / then
		assertThatThrownBy(() -> service.createMatchChannel(match))
				.isInstanceOf(DiscordTransientException.class);

		// and — DB has no channelId (no save reached step 9)
		assertThat(matchRepository.findById(match.getId()).orElseThrow().getDiscordChannelId()).isNull();
	}
}
