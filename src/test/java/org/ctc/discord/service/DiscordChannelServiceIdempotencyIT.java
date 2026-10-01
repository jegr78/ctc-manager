package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.ctc.TestHelper;
import org.ctc.admin.service.TeamCardService;
import org.ctc.discord.DiscordPermissions;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
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
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Creates the Discord channel of one match twice, one after the other and at the same time,
 * against stubbed Discord endpoints, and checks that only one channel is created.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class DiscordChannelServiceIdempotencyIT {

	private static final String BOT_USER_ID = "bot-id-idem";
	private static final String GUILD_CHANNELS = "/api/v10/guilds/g-idem/channels";

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

	@Autowired DiscordChannelService channelService;
	@Autowired DiscordGlobalConfigRepository configRepo;
	@Autowired MatchRepository matchRepository;
	@Autowired TeamRepository teamRepository;
	@Autowired SeasonRepository seasonRepository;
	@Autowired DiscordPostRepository discordPostRepository;
	@Autowired TestHelper helper;

	@MockitoBean
	TeamCardService teamCardService;

	@Value("${app.upload-dir:uploads}")
	String uploadDir;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private UUID matchId;

	@BeforeEach
	void setUp() throws Exception {
		wm.resetAll();
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(okJson("{\"id\":\"" + BOT_USER_ID + "\",\"username\":\"CTC-Bot\",\"discriminator\":\"0001\"}")));
		discordPostRepository.deleteAll();
		Mockito.when(teamCardService.cardExists(ArgumentMatchers.any(SeasonTeam.class))).thenReturn(true);
		Mockito.when(teamCardService.getCardPath(ArgumentMatchers.any(SeasonTeam.class)))
				.thenReturn("/uploads/team-cards/idempotency-dummy.png");
		Path dummy = Path.of(uploadDir, "team-cards/idempotency-dummy.png").toAbsolutePath().normalize();
		Files.createDirectories(dummy.getParent());
		if (!Files.exists(dummy)) {
			Files.write(dummy, new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47});
		}
		seedConfig();
		matchId = seedMatch().getId();
		stubDiscord();
	}

	@AfterEach
	void cleanup() {
		discordPostRepository.deleteAll();
		matchRepository.findById(matchId).ifPresent(matchRepository::delete);
	}

	@Test
	void givenLinkedMatch_whenChannelCreatedAgain_thenTheFirstChannelStaysAndNoSecondIsCreated() throws Exception {
		// given
		assertThat(channelService.createMatchChannel(stored())).as("first request creates the channel").isTrue();
		long postsAfterFirst = discordPostRepository.count();

		// when
		boolean createdAgain = channelService.createMatchChannel(stored());

		// then
		assertThat(createdAgain).as("second request creates nothing").isFalse();
		wm.verify(exactly(1), postRequestedFor(urlPathEqualTo(GUILD_CHANNELS)));
		assertThat(stored().getDiscordChannelId()).as("channel id").isEqualTo("c-idem");
		assertThat(stored().getDiscordChannelWebhookUrl()).as("webhook url").isEqualTo(wm.baseUrl() + "/webhooks/1/tok-idem");
		assertThat(discordPostRepository.count()).as("posts stay with the first channel").isEqualTo(postsAfterFirst);
	}

	@Test
	void givenTwoConcurrentRequests_whenChannelCreated_thenOnlyOneChannelIsCreated() throws Exception {
		// given
		var pool = Executors.newFixedThreadPool(2);
		var start = new CountDownLatch(1);

		// when
		var results = pool.invokeAll(List.of(
				() -> { start.await(); return channelService.createMatchChannel(stored()); },
				() -> { start.countDown(); return channelService.createMatchChannel(stored()); }));
		pool.shutdown();
		assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).as("both requests finish").isTrue();

		// then
		assertThat(List.of(results.get(0).get(), results.get(1).get())).as("exactly one request creates")
				.containsExactlyInAnyOrder(true, false);
		wm.verify(exactly(1), postRequestedFor(urlPathEqualTo(GUILD_CHANNELS)));
		assertThat(stored().getDiscordChannelId()).as("channel id").isEqualTo("c-idem");
	}

	@Test
	void givenChannelCreationInFlight_whenAnotherChannelIsLinked_thenTheLinkIsRejectedAndTheCreatedChannelStays() throws Exception {
		// given
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/c-link"))
				.willReturn(okJson("{\"id\":\"c-link\",\"name\":\"prepared\",\"type\":0,\"parent_id\":\"cat-idem\"}")));
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/c-link/webhooks"))
				.willReturn(okJson("[{\"id\":\"w-link\",\"name\":\"CTC Manager\",\"channel_id\":\"c-link\","
						+ "\"token\":\"tok-link\",\"url\":\"" + wm.baseUrl() + "/webhooks/w-link/tok-link\"}]")));
		wm.stubFor(post(urlPathEqualTo("/api/v10/channels/c-idem/webhooks"))
				.willReturn(okJson("{\"id\":\"1\",\"token\":\"tok-idem\",\"url\":\"" + wm.baseUrl()
						+ "/webhooks/1/tok-idem\",\"channel_id\":\"c-idem\"}").withFixedDelay(1500)));
		var pool = Executors.newSingleThreadExecutor();
		var creation = pool.submit(() -> channelService.createMatchChannel(stored()));
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (wm.findAll(postRequestedFor(urlPathEqualTo(GUILD_CHANNELS))).isEmpty() && System.nanoTime() < deadline) {
			Thread.sleep(10);
		}

		// when
		Throwable linkFailure = catchThrowable(() -> channelService.linkExistingChannel(stored(), "c-link"));

		// then
		assertThat(creation.get(30, TimeUnit.SECONDS)).as("the creation stores its channel").isTrue();
		pool.shutdown();
		assertThat(linkFailure).as("link while the match gets a channel")
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("already has a Discord channel");
		assertThat(stored().getDiscordChannelId()).as("channel id").isEqualTo("c-idem");
		assertThat(stored().getDiscordChannelWebhookUrl()).as("webhook url").isEqualTo(wm.baseUrl() + "/webhooks/1/tok-idem");
	}

	@Test
	void givenDeletedMatch_whenChannelCreated_thenNotFoundAndNothingCreated() throws Exception {
		// given
		Match detached = stored();
		matchRepository.delete(detached);

		// when
		Throwable failure = catchThrowable(() -> channelService.createMatchChannel(detached));

		// then
		assertThat(failure).as("create for a deleted match").isInstanceOf(EntityNotFoundException.class)
				.hasMessageContaining("Match not found");
		wm.verify(exactly(0), postRequestedFor(urlPathEqualTo(GUILD_CHANNELS)));
	}

	private Match stored() {
		return matchRepository.findById(matchId).orElseThrow();
	}

	private void seedConfig() {
		DiscordGlobalConfig cfg = configRepo.findFirstByOrderByIdAsc();
		if (cfg == null) {
			cfg = new DiscordGlobalConfig();
		}
		cfg.setGuildId("g-idem");
		cfg.setCurrentMatchCategoryId("cat-idem");
		configRepo.save(cfg);
	}

	private Match seedMatch() {
		Season season = helper.createSeason("Test_Idempotency_" + id);
		Matchday md = helper.createMatchdayInRegularPhase(season, "Test_Idempotency MD " + id, 0);
		Team home = helper.createTeam("Test Idempotency Home " + id, "Test_IDH_" + id);
		Team away = helper.createTeam("Test Idempotency Away " + id, "Test_IDA_" + id);
		home.setDiscordRoleId("r-h");
		away.setDiscordRoleId("r-a");
		teamRepository.save(home);
		teamRepository.save(away);
		season.addTeam(home);
		season.addTeam(away);
		seasonRepository.save(season);
		return helper.createMatch(md, home, away);
	}

	private void stubDiscord() {
		String botAllow = String.valueOf(DiscordPermissions.BOT_ALLOW_MASK);
		wm.stubFor(post(urlPathEqualTo(GUILD_CHANNELS))
				.willReturn(okJson("{\"id\":\"c-idem\",\"name\":\"x\",\"type\":0,\"parent_id\":\"cat-idem\"}")
						.withFixedDelay(300)));
		wm.stubFor(post(urlPathEqualTo("/api/v10/channels/c-idem/webhooks"))
				.willReturn(okJson("{\"id\":\"1\",\"token\":\"tok-idem\",\"url\":\"" + wm.baseUrl()
						+ "/webhooks/1/tok-idem\",\"channel_id\":\"c-idem\"}")));
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/c-idem"))
				.willReturn(okJson("{\"id\":\"c-idem\",\"name\":\"x\",\"type\":0,\"parent_id\":\"cat-idem\","
						+ "\"permission_overwrites\":["
						+ "{\"id\":\"g-idem\",\"type\":0,\"allow\":\"0\",\"deny\":\"1024\"},"
						+ "{\"id\":\"r-h\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
						+ "{\"id\":\"r-a\",\"type\":0,\"allow\":\"1024\",\"deny\":\"0\"},"
						+ "{\"id\":\"" + BOT_USER_ID + "\",\"type\":1,\"allow\":\"" + botAllow + "\",\"deny\":\"0\"}"
						+ "]}")));
		wm.stubFor(post(urlPathEqualTo("/webhooks/1/tok-idem"))
				.willReturn(okJson("{\"id\":\"msg-idem\",\"channel_id\":\"c-idem\"}")));
	}
}
