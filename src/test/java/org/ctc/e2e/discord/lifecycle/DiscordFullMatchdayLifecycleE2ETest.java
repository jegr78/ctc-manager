package org.ctc.e2e.discord.lifecycle;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.microsoft.playwright.options.RequestOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.ctc.admin.TestDataService;
import org.ctc.admin.TestDataService.LifecycleFixture;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.discord.service.DiscordPostService;
import org.ctc.discord.wiremock.WireMockDiscordStubs;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.e2e.PlaywrightConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Tag("e2e")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DiscordFullMatchdayLifecycleE2ETest extends PlaywrightConfig {

	private static final long CHANNEL_SNOWFLAKE = 900000000000000001L;
	private static final long WEBHOOK_SNOWFLAKE = 900000000000000002L;
	private static final long FORUM_THREAD_SNOWFLAKE = 900000000000000099L;
	private static final long FORUM_WEBHOOK_SNOWFLAKE = 900000000000000050L;
	private static final long ARCHIVE_CATEGORY_SNOWFLAKE = 900000000000000777L;
	private static final long MSG_TEAM_CARDS = 900000000000000010L;
	private static final long MSG_SETTINGS = 900000000000000011L;
	private static final long MSG_LINEUPS = 900000000000000012L;
	private static final long MSG_SCHEDULE = 900000000000000013L;
	private static final long MSG_PROVISIONAL = 900000000000000014L;
	private static final long MSG_MATCH_RESULTS = 900000000000000015L;
	private static final long MSG_FORUM_RACE_RESULT = 900000000000000016L;
	private static final String WEBHOOK_TOKEN = "test-token-lifecycle";
	private static final String FORUM_WEBHOOK_TOKEN = "test-token-forum";
	private static final String TEST_GUILD_ID = "100000000000099999";

	private static final byte[] DUMMY_PNG_HEADER = new byte[] {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};

	@RegisterExtension
	static WireMockExtension wm = WireMockExtension.newInstance()
			.options(WireMockConfiguration.options().dynamicPort())
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
	TestDataService testDataService;

	@Autowired
	DiscordGlobalConfigRepository globalConfigRepository;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	RaceRepository raceRepository;

	@Autowired
	RaceResultRepository raceResultRepository;

	@Autowired
	DriverRepository driverRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Autowired
	DiscordPostService discordPostService;

	@Autowired
	SeasonRepository seasonRepository;

	@Autowired
	PlatformTransactionManager transactionManager;

	@Value("${app.upload-dir:data/dev/uploads}")
	String uploadDir;

	private LifecycleFixture fixture;

	@BeforeEach
	void setUp() throws IOException {
		setupPage();
		wm.resetAll();
		discordPostRepository.deleteAll();
		WireMockDiscordStubs.stubListGuildChannels(wm, TEST_GUILD_ID);
		WireMockDiscordStubs.stubListGuildRoles(wm, TEST_GUILD_ID);
		WireMockDiscordStubs.stubFetchBotUser(wm, "100000000000077777");
		seedGlobalConfig();
		fixture = testDataService.seedFullMatchdayLifecycle();
		Match match = fixture.match();
		match.setDiscordChannelId(String.valueOf(CHANNEL_SNOWFLAKE));
		match.setDiscordChannelWebhookUrl(wmWebhookUrl(WEBHOOK_SNOWFLAKE, WEBHOOK_TOKEN));
		matchRepository.save(match);
		preStageTeamCards();
	}

	@AfterEach
	void tearDown() {
		teardownPage();
		discordPostRepository.deleteAll();
	}

	@Test
	void fullMatchdayLifecycle() throws Exception {
		step1_createChannel_thenChannelIdStored();
		step2_postTeamCards_thenMultipartPosted();
		step3_postSettings_thenMultipartPosted();
		step4_postLineups_thenMultipartPosted();
		step5_postSchedule_thenEmbedPosted();
		step6_postProvisionalScores_thenMultipartPosted();
		step7_postMatchResults_thenMultipartPostedAndForumThreadIdQueryParamSent();
		step8_moveToArchive_thenChannelArchived();
	}

	private void step1_createChannel_thenChannelIdStored() {
		Match reloaded = matchRepository.findById(fixture.match().getId()).orElseThrow();
		Assertions.assertThat(reloaded.getDiscordChannelId())
				.isEqualTo(String.valueOf(CHANNEL_SNOWFLAKE));
		Assertions.assertThat(reloaded.getDiscordChannelWebhookUrl())
				.contains(String.valueOf(WEBHOOK_SNOWFLAKE));
	}

	private void step2_postTeamCards_thenMultipartPosted() {
		WireMockDiscordStubs.stubExecuteWebhook(wm, WEBHOOK_SNOWFLAKE, WEBHOOK_TOKEN, MSG_TEAM_CARDS);
		postAction("post-team-cards");
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath(WEBHOOK_SNOWFLAKE, WEBHOOK_TOKEN)))
				.withQueryParam("wait", equalTo("true"))
				.withRequestBodyPart(aMultipart("files[0]").build())
				.withRequestBodyPart(aMultipart("files[1]").build()));
		assertMultipartBodyLargerThan(WEBHOOK_SNOWFLAKE, WEBHOOK_TOKEN);
		assertDiscordPostPersisted(DiscordPostType.TEAM_CARDS, MSG_TEAM_CARDS);
	}

	private void step3_postSettings_thenMultipartPosted() {
		WireMockDiscordStubs.stubExecuteWebhook(wm, WEBHOOK_SNOWFLAKE, WEBHOOK_TOKEN, MSG_SETTINGS);
		postAction("post-settings");
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath(WEBHOOK_SNOWFLAKE, WEBHOOK_TOKEN)))
				.withQueryParam("wait", equalTo("true"))
				.withRequestBodyPart(aMultipart("files[0]").build()));
		assertDiscordPostPersisted(DiscordPostType.SETTINGS, MSG_SETTINGS);
	}

	private void step4_postLineups_thenMultipartPosted() {
		WireMockDiscordStubs.stubExecuteWebhook(wm, WEBHOOK_SNOWFLAKE, WEBHOOK_TOKEN, MSG_LINEUPS);
		postAction("post-lineups");
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath(WEBHOOK_SNOWFLAKE, WEBHOOK_TOKEN)))
				.withQueryParam("wait", equalTo("true"))
				.withRequestBodyPart(aMultipart("files[0]").build()));
		assertDiscordPostPersisted(DiscordPostType.LINEUPS, MSG_LINEUPS);
	}

	private void step5_postSchedule_thenEmbedPosted() {
		WireMockDiscordStubs.stubExecuteWebhook(wm, WEBHOOK_SNOWFLAKE, WEBHOOK_TOKEN, MSG_SCHEDULE);
		postAction("post-schedule");
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath(WEBHOOK_SNOWFLAKE, WEBHOOK_TOKEN)))
				.withQueryParam("wait", equalTo("true"))
				.withRequestBody(matchingJsonPath("$.embeds[0].fields[?(@.name == 'Date')]")));
		assertDiscordPostPersisted(DiscordPostType.SCHEDULE, MSG_SCHEDULE);
	}

	private void step6_postProvisionalScores_thenMultipartPosted() {
		seedRaceResults(false);
		WireMockDiscordStubs.stubExecuteWebhook(wm, WEBHOOK_SNOWFLAKE, WEBHOOK_TOKEN, MSG_PROVISIONAL);
		postAction("post-provisional");
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath(WEBHOOK_SNOWFLAKE, WEBHOOK_TOKEN)))
				.withQueryParam("wait", equalTo("true"))
				.withRequestBodyPart(aMultipart("files[0]").build()));
		assertDiscordPostPersisted(DiscordPostType.PROVISIONAL_SCORES, MSG_PROVISIONAL);
	}

	private void step7_postMatchResults_thenMultipartPostedAndForumThreadIdQueryParamSent() throws Exception {
		seedRaceResults(true);
		seedForumThread();
		WireMockDiscordStubs.stubExecuteWebhook(wm, WEBHOOK_SNOWFLAKE, WEBHOOK_TOKEN, MSG_MATCH_RESULTS);
		WireMockDiscordStubs.stubExecuteWebhookForumThread(wm, FORUM_WEBHOOK_SNOWFLAKE, FORUM_WEBHOOK_TOKEN,
				FORUM_THREAD_SNOWFLAKE, MSG_FORUM_RACE_RESULT);
		WireMockDiscordStubs.stubFetchChannelNotArchived(wm, FORUM_THREAD_SNOWFLAKE);

		postAction("post-match-results");
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath(WEBHOOK_SNOWFLAKE, WEBHOOK_TOKEN)))
				.withQueryParam("wait", equalTo("true"))
				.withRequestBodyPart(aMultipart("files[0]").build()));
		assertDiscordPostPersisted(DiscordPostType.MATCH_RESULTS, MSG_MATCH_RESULTS);

		java.util.UUID raceId = raceRepository.findAll().stream()
				.filter(r -> r.getMatch() != null && r.getMatch().getId().equals(fixture.match().getId()))
				.findFirst().orElseThrow().getId();
		new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
			Race race = raceRepository.findById(raceId).orElseThrow();
			try {
				discordPostService.postRaceResultToForumThread(race);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		});
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath(FORUM_WEBHOOK_SNOWFLAKE, FORUM_WEBHOOK_TOKEN)))
				.withQueryParam("wait", equalTo("true"))
				.withQueryParam("thread_id", equalTo(String.valueOf(FORUM_THREAD_SNOWFLAKE)))
				.withRequestBodyPart(aMultipart("files[0]").build()));
	}

	private void step8_moveToArchive_thenChannelArchived() {
		WireMockDiscordStubs.stubArchiveChannel(wm, CHANNEL_SNOWFLAKE, ARCHIVE_CATEGORY_SNOWFLAKE);
		page.request().post(url("/admin/matches/" + fixture.match().getId() + "/move-to-archive"),
				RequestOptions.create()
						.setHeader("Content-Type", "application/x-www-form-urlencoded")
						.setData("categoryId=" + ARCHIVE_CATEGORY_SNOWFLAKE));
		wm.verify(patchRequestedFor(urlPathEqualTo("/api/v10/channels/" + CHANNEL_SNOWFLAKE))
				.withRequestBody(matchingJsonPath("$.parent_id",
						equalTo(String.valueOf(ARCHIVE_CATEGORY_SNOWFLAKE)))));
		Match reloaded = matchRepository.findById(fixture.match().getId()).orElseThrow();
		Assertions.assertThat(reloaded.getDiscordChannelArchivedAt()).isNotNull();
	}

	private void postAction(String pathSegment) {
		page.request().post(url("/admin/matches/" + fixture.match().getId() + "/" + pathSegment),
				RequestOptions.create()
						.setHeader("Content-Type", "application/x-www-form-urlencoded")
						.setData(""));
	}

	private void seedGlobalConfig() {
		DiscordGlobalConfig cfg = globalConfigRepository.findAll().stream().findFirst()
				.orElseGet(DiscordGlobalConfig::new);
		cfg.setGuildId(TEST_GUILD_ID);
		cfg.setCurrentMatchCategoryId("100000000000088888");
		cfg.setAnnouncementWebhookUrl(wmWebhookUrl(WEBHOOK_SNOWFLAKE, WEBHOOK_TOKEN));
		cfg.setRaceResultsForumWebhookUrl(wmWebhookUrl(FORUM_WEBHOOK_SNOWFLAKE, FORUM_WEBHOOK_TOKEN));
		cfg.setRaceResultsForumChannelId(String.valueOf(FORUM_THREAD_SNOWFLAKE - 1));
		cfg.setStandingsForumWebhookUrl(wmWebhookUrl(FORUM_WEBHOOK_SNOWFLAKE + 1, FORUM_WEBHOOK_TOKEN));
		cfg.setStandingsForumChannelId(String.valueOf(FORUM_THREAD_SNOWFLAKE - 2));
		cfg.setBotApplicationId("100000000000077777");
		cfg.setVsEmojiName("CTC");
		globalConfigRepository.save(cfg);
	}

	private void seedRaceResults(boolean allRaces) {
		List<Race> races = raceRepository.findAll().stream()
				.filter(r -> r.getMatch() != null && r.getMatch().getId().equals(fixture.match().getId()))
				.toList();
		List<Driver> drivers = driverRepository.findAll().stream()
				.filter(d -> d.getPsnId().startsWith("T-PSN-"))
				.toList();
		if (drivers.isEmpty() || races.isEmpty()) {
			return;
		}
		Driver driver = drivers.get(0);
		for (int i = 0; i < races.size(); i++) {
			if (!allRaces && i > 0) {
				break;
			}
			Race race = races.get(i);
			if (!raceResultRepository.findByRaceId(race.getId()).isEmpty()) {
				continue;
			}
			RaceResult result = new RaceResult(race, driver, 1, 1, false);
			raceResultRepository.save(result);
		}
	}

	private void seedForumThread() {
		Season season = seasonRepository.findById(fixture.season().getId()).orElseThrow();
		season.setDiscordRaceResultsThreadId(String.valueOf(FORUM_THREAD_SNOWFLAKE));
		seasonRepository.save(season);
	}

	private void preStageTeamCards() throws IOException {
		Path seasonDir = Path.of(uploadDir, "team-cards", fixture.season().getId().toString());
		Files.createDirectories(seasonDir);
		byte[] payload = new byte[2048];
		System.arraycopy(DUMMY_PNG_HEADER, 0, payload, 0, DUMMY_PNG_HEADER.length);
		for (String shortName : List.of(
				fixture.homeTeam().getShortName(), fixture.awayTeam().getShortName())) {
			String fileName = shortName.replaceAll("[^a-zA-Z0-9._-]", "_") + ".png";
			Path filePath = seasonDir.resolve(fileName);
			Files.write(filePath, payload);
		}
	}

	private void assertMultipartBodyLargerThan(long webhookId, String token) {
		String path = webhookPath(webhookId, token);
		List<ServeEvent> events = wm.getAllServeEvents().stream()
				.filter(e -> e.getRequest().getUrl().startsWith(path))
				.toList();
		Assertions.assertThat(events).isNotEmpty();
		Assertions.assertThat(events.get(events.size() - 1).getRequest().getBody().length)
				.as("multipart body must be >1024 bytes (real PNG payload)")
				.isGreaterThan(1024);
	}

	private void assertDiscordPostPersisted(DiscordPostType type, long expectedMessageSnowflake) {
		var posts = discordPostRepository.findAll().stream()
				.filter(p -> p.getPostType() == type)
				.toList();
		Assertions.assertThat(posts)
				.as("DiscordPost row for type=%s", type)
				.isNotEmpty();
		Assertions.assertThat(posts.get(0).getMessageId())
				.isEqualTo(String.valueOf(expectedMessageSnowflake));
	}

	private String wmWebhookUrl(long webhookId, String token) {
		return wm.baseUrl() + "/api/v10/webhooks/" + webhookId + "/" + token;
	}

	private String webhookPath(long webhookId, String token) {
		return "/api/v10/webhooks/" + webhookId + "/" + token;
	}
}
