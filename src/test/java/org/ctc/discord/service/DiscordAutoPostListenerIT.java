package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.admin.service.TeamCardService;
import org.ctc.discord.DiscordPermissions;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class DiscordAutoPostListenerIT {

	private static final String BOT_USER_ID = "bot-id-list";
	private static final String AUTO_POST_ERROR_ATTRIBUTE = "discord.autoPostError";

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
	DiscordChannelService channelService;

	@Autowired
	DiscordGlobalConfigRepository configRepo;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	TeamRepository teamRepository;

	@Autowired
	SeasonRepository seasonRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Autowired
	TestHelper helper;

	@MockitoBean
	TeamCardService teamCardService;

	@Value("${app.upload-dir:uploads}")
	String uploadDir;

	private UUID createdMatchId;

	@BeforeEach
	void setUp() throws Exception {
		wm.resetAll();
		stubBotIdentity();
		bindMockRequest();
		discordPostRepository.deleteAll();
		org.mockito.Mockito.when(teamCardService.cardExists(org.mockito.ArgumentMatchers.any(SeasonTeam.class)))
				.thenReturn(true);
		org.mockito.Mockito.when(teamCardService.getCardPath(org.mockito.ArgumentMatchers.any(SeasonTeam.class)))
				.thenReturn("/uploads/team-cards/listener-dummy.png");
		java.nio.file.Path dummy = java.nio.file.Path.of(uploadDir, "team-cards/listener-dummy.png")
				.toAbsolutePath().normalize();
		java.nio.file.Files.createDirectories(dummy.getParent());
		if (!java.nio.file.Files.exists(dummy)) {
			java.nio.file.Files.write(dummy, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
		}
	}

	@AfterEach
	void cleanup() {
		discordPostRepository.deleteAll();
		if (createdMatchId != null) {
			matchRepository.findById(createdMatchId).ifPresent(matchRepository::delete);
			createdMatchId = null;
		}
	}

	private void bindMockRequest() {
		MockHttpServletRequest req = new MockHttpServletRequest();
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
	}

	private void stubBotIdentity() {
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(okJson(
						"{\"id\":\"" + BOT_USER_ID + "\",\"username\":\"CTC-Bot\",\"discriminator\":\"0001\"}")));
	}

	private void seedConfig(String guildId, String categoryId) {
		DiscordGlobalConfig cfg = configRepo.findFirstByOrderByIdAsc();
		if (cfg == null) {
			cfg = new DiscordGlobalConfig();
		}
		cfg.setGuildId(guildId);
		cfg.setCurrentMatchCategoryId(categoryId);
		configRepo.save(cfg);
	}

	private Match seedMatch(String suffix, String homeRoleId, String awayRoleId) {
		Season season = helper.createSeason("Listener " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-LST-" + suffix, 0);
		Team home = helper.createTeam("LST Home " + suffix, "lst-h" + suffix);
		Team away = helper.createTeam("LST Away " + suffix, "lst-a" + suffix);
		home.setDiscordRoleId(homeRoleId);
		away.setDiscordRoleId(awayRoleId);
		teamRepository.save(home);
		teamRepository.save(away);
		season.addTeam(home);
		season.addTeam(away);
		seasonRepository.save(season);
		Match m = helper.createMatch(md, home, away);
		createdMatchId = m.getId();
		return m;
	}

	private void stubChannelCreate(String channelId, String webhookId, String webhookUrl) {
		String botAllow = String.valueOf(DiscordPermissions.BOT_ALLOW_MASK);
		wm.stubFor(post(urlPathEqualTo("/api/v10/guilds/g-lst/channels"))
				.willReturn(okJson(
						"{\"id\":\"" + channelId + "\",\"name\":\"x\",\"type\":0,\"parent_id\":\"cat-lst\"}")));
		wm.stubFor(post(urlPathEqualTo("/api/v10/channels/" + channelId + "/webhooks"))
				.willReturn(okJson(
						"{\"id\":\"" + webhookId + "\",\"token\":\"tok\","
								+ "\"url\":\"" + webhookUrl + "\",\"channel_id\":\"" + channelId + "\"}")));
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + channelId))
				.willReturn(okJson(
						"{\"id\":\"" + channelId + "\",\"name\":\"x\",\"type\":0,\"parent_id\":\"cat-lst\","
								+ "\"permission_overwrites\":["
								+ "{\"id\":\"g-lst\",\"type\":0,\"allow\":\"0\",\"deny\":\"1024\"},"
								+ "{\"id\":\"r-h\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
								+ "{\"id\":\"r-a\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
								+ "{\"id\":\"" + BOT_USER_ID + "\",\"type\":1,\"allow\":\"" + botAllow + "\",\"deny\":\"0\"}"
								+ "]}")));
	}

	private String readAutoPostAttribute() {
		try {
			Object v = RequestContextHolder.currentRequestAttributes()
					.getAttribute(AUTO_POST_ERROR_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
			return v == null ? null : v.toString();
		} catch (IllegalStateException ise) {
			return null;
		}
	}

	@Test
	void givenAutoPost200_whenChannelCreatedEventFires_thenDiscordPostRowCreatedAndNoErrorAttribute() throws Exception {
		seedConfig("g-lst", "cat-lst");
		Match match = seedMatch("OK", "r-h", "r-a");
		String webhookPath = "/webhooks/100/tok-ok";
		stubChannelCreate("c-OK-LST", "w-OK", wm.baseUrl() + webhookPath);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-ok\",\"channel_id\":\"c-OK-LST\"}")));

		channelService.createMatchChannel(match);

		Match reloaded = matchRepository.findById(match.getId()).orElseThrow();
		assertThat(reloaded.getDiscordChannelId()).isEqualTo("c-OK-LST");
		assertThat(discordPostRepository.findByChannelIdAndPostTypeAndMatchId(
				"c-OK-LST", DiscordPostType.TEAM_CARDS, match.getId()))
				.as("Listener creates TEAM_CARDS row after outer Tx commit")
				.isPresent();
		assertThat(readAutoPostAttribute())
				.as("Successful auto-post leaves no error attribute")
				.isNull();
	}

	@Test
	void givenAutoPost5xx_whenChannelCreatedEventFires_thenChannelPersistedAndErrorAttributeSet() throws Exception {
		seedConfig("g-lst", "cat-lst");
		Match match = seedMatch("5XX", "r-h", "r-a");
		String webhookPath = "/webhooks/200/tok-5xx";
		stubChannelCreate("c-5XX-LST", "w-5XX", wm.baseUrl() + webhookPath);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(500).withBody("server error")));

		channelService.createMatchChannel(match);

		Match reloaded = matchRepository.findById(match.getId()).orElseThrow();
		assertThat(reloaded.getDiscordChannelId())
				.as("Channel persists even when listener's auto-post fails")
				.isEqualTo("c-5XX-LST");
		assertThat(reloaded.getDiscordChannelWebhookUrl()).isNotNull();
		assertThat(readAutoPostAttribute())
				.as("Listener catches 5xx and sets error attribute")
				.isNotNull();
	}

	@Test
	void givenRuntimeExceptionInListener_whenChannelCreatedEventFires_thenChannelPersistedAndErrorAttributeSet() throws Exception {
		seedConfig("g-lst", "cat-lst");
		Match match = seedMatch("RT", "r-h", "r-a");
		String webhookPath = "/webhooks/300/tok-rt";
		stubChannelCreate("c-RT-LST", "w-RT", wm.baseUrl() + webhookPath);
		org.mockito.Mockito.when(teamCardService.getCardPath(org.mockito.ArgumentMatchers.any(SeasonTeam.class)))
				.thenReturn("/uploads/../../etc/passwd");

		channelService.createMatchChannel(match);

		Match reloaded = matchRepository.findById(match.getId()).orElseThrow();
		assertThat(reloaded.getDiscordChannelId())
				.as("Channel persists even when listener throws RuntimeException — no Tx poisoning")
				.isEqualTo("c-RT-LST");
		assertThat(readAutoPostAttribute())
				.as("Listener catches RuntimeException and sets transient error attribute")
				.isEqualTo("transient");
	}
}
