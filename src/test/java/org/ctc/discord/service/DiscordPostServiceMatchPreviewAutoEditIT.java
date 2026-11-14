package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.ctc.TestHelper;
import org.ctc.admin.service.LineupGraphicService;
import org.ctc.admin.service.SettingsGraphicService;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class DiscordPostServiceMatchPreviewAutoEditIT {

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
	DiscordPostService service;

	@Autowired
	TestHelper helper;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	RaceRepository raceRepository;

	@Autowired
	RaceLineupRepository raceLineupRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Autowired
	DiscordGlobalConfigRepository globalConfigRepository;

	@Autowired
	DiscordGlobalConfigService globalConfigService;

	@MockitoBean
	SettingsGraphicService settingsGraphicService;

	@MockitoBean
	LineupGraphicService lineupGraphicService;

	@Value("${app.upload-dir:uploads}")
	String uploadDir;

	@BeforeEach
	void resetState() throws Exception {
		wm.resetAll();
		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setAnnouncementWebhookUrl("");
		cfg.setRaceResultsForumWebhookUrl("");
		cfg.setStandingsForumWebhookUrl("");
		cfg.setVsEmojiName("CTC");
		globalConfigRepository.save(cfg);
		discordPostRepository.deleteAll();
		Path dummy = Path.of(uploadDir, "preview-dummy.png").toAbsolutePath().normalize();
		Files.createDirectories(dummy.getParent());
		Files.write(dummy, PNG_BYTES);
		when(settingsGraphicService.generateSettings(any(Race.class))).thenReturn("/uploads/preview-dummy.png");
		when(lineupGraphicService.generateLineup(any(Race.class))).thenReturn("/uploads/preview-dummy.png");
	}

	private Match seedMatchWithRace(String suffix, String teaser, String streamLink) {
		Season season = helper.createSeason("AE Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-AE-" + suffix, 0);
		Team home = helper.createTeam("AE Home " + suffix, "AEH" + suffix);
		Team away = helper.createTeam("AE Away " + suffix, "AEA" + suffix);
		Match match = helper.createMatch(md, home, away);

		org.ctc.domain.model.Driver driver = helper.createDriver("psn-" + suffix, "Drv " + suffix);

		Race race1 = helper.createRace(md, match);
		race1.setDateTime(LocalDateTime.of(2026, 6, 1, 20, 30));
		race1.setSettings(helper.completeRaceSettings(race1));
		raceRepository.save(race1);
		RaceLineup lu = new RaceLineup();
		lu.setRace(race1);
		lu.setTeam(home);
		lu.setDriver(driver);
		raceLineupRepository.save(lu);

		match.getRaces().add(race1);
		match.setDiscordTeaser(teaser);
		match.setStreamLink(streamLink);
		return matchRepository.save(match);
	}

	private void setAnnouncementWebhook(String webhookUrl) {
		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setAnnouncementWebhookUrl(webhookUrl);
		cfg.setVsEmojiName("CTC");
		globalConfigRepository.save(cfg);
	}

	private DiscordPost seedExistingPreviewPost(Match match, String channelId, String messageId) {
		DiscordPost p = new DiscordPost();
		p.setChannelId(channelId);
		p.setMessageId(messageId);
		p.setWebhookId("940");
		p.setWebhookToken("tok-ae");
		p.setPostType(DiscordPostType.MATCH_PREVIEW);
		p.setMatchId(match.getId());
		p.setPostedAt(LocalDateTime.of(2026, 5, 30, 12, 0));
		return discordPostRepository.save(p);
	}

    @Test
    void givenUnpublishedChoices_whenAutoEditing_thenReplaceOnlySafeAttachments() throws Exception {
        // given
        String webhookPath = "/api/v10/webhooks/123456789012345678/Test_preview-token";
        setAnnouncementWebhook(wm.baseUrl() + webhookPath);
        Match match = seedMatchWithRace("Priv", "Test changed teaser", null);
        seedExistingPreviewPost(match, "123456789012345678", "234567890123456789");
        Path privateGraphic = Path.of(uploadDir, "preview-private-test.png").toAbsolutePath().normalize();
        Files.writeString(privateGraphic, "TEST-PRIVATE-CAR-TRACK-WEATHER-7ca1");
        when(settingsGraphicService.generateSettings(any(Race.class))).thenReturn("/uploads/preview-private-test.png");
        wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/234567890123456789"))
                .willReturn(okJson("{\"id\":\"234567890123456789\",\"channel_id\":\"123456789012345678\"}")));
        String request;
        // when
        try {
            service.autoEditMatchPreviewIfNeeded(match);
            request = wm.getAllServeEvents().getFirst().getRequest().getBodyAsString();
        } finally { Files.deleteIfExists(privateGraphic); }
        // then
        assertThat(request).contains("Test changed teaser", "lineups-md1.png")
                .doesNotContain("TEST-PRIVATE-CAR-TRACK-WEATHER-7ca1", "settings-md1.png");
    }

    @Test
    void givenWholeMatchQuickScore_whenAutoEditing_thenAttachTheNoLongerConfidentialSettings() throws Exception {
        // given
        String webhookPath = "/api/v10/webhooks/123456789012345677/Test_finished-preview-token";
        setAnnouncementWebhook(wm.baseUrl() + webhookPath);
        Match match = seedMatchWithRace("Done", "Test finished teaser", null);
        match.setHomeScore(10); match.setAwayScore(8); match = matchRepository.save(match);
        seedExistingPreviewPost(match, "123456789012345677", "234567890123456778");
        Path finishedGraphic = Path.of(uploadDir, "preview-finished-test.png").toAbsolutePath().normalize();
        Files.writeString(finishedGraphic, "TEST-FINISHED-SETTINGS-5b2e");
        when(settingsGraphicService.generateSettings(any(Race.class))).thenReturn("/uploads/preview-finished-test.png");
        wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/234567890123456778"))
                .willReturn(okJson("{\"id\":\"234567890123456778\",\"channel_id\":\"123456789012345677\"}")));
        String request;
        // when
        try {
            service.autoEditMatchPreviewIfNeeded(match);
            request = wm.getAllServeEvents().getFirst().getRequest().getBodyAsString();
        } finally { Files.deleteIfExists(finishedGraphic); }
        // then
        assertThat(request).as("a decided match no longer withholds its setup").contains("settings-md1.png", "lineups-md1.png");
    }

    @Test
    void givenStaleCallerWithUnpublishedChoices_whenAutoEditing_thenReadCurrentPublicationEvidence() throws Exception {
        // given
        String webhookPath = "/api/v10/webhooks/123456789012345679/Test_stale-preview-token";
        setAnnouncementWebhook(wm.baseUrl() + webhookPath);
        Match current = seedMatchWithRace("Stal", "Test stale caller teaser", null);
        current.getRaces().forEach(race -> {  raceRepository.saveAndFlush(race); });
        seedExistingPreviewPost(current, "123456789012345679", "234567890123456780");
        var stale = new Match(current.getMatchday(), current.getHomeTeam(), current.getAwayTeam());
        stale.setId(current.getId()); stale.setDiscordTeaser(current.getDiscordTeaser());
        var oldRace = new Race(); oldRace.setId(current.getRaces().getFirst().getId()); oldRace.setMatch(stale);
        oldRace.setMatchday(current.getMatchday()); oldRace.setDateTime(current.getRaces().getFirst().getDateTime());
        stale.getRaces().add(oldRace);
        Path privateGraphic = Path.of(uploadDir, "preview-private-stale-test.png").toAbsolutePath().normalize();
        Files.writeString(privateGraphic, "TEST-PRIVATE-STALE-SETTINGS-713f");
        when(settingsGraphicService.generateSettings(any(Race.class))).thenReturn("/uploads/preview-private-stale-test.png");
        wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/234567890123456780"))
                .willReturn(okJson("{\"id\":\"234567890123456780\",\"channel_id\":\"123456789012345679\"}")));
        String request;
        // when
        try { service.autoEditMatchPreviewIfNeeded(stale); request = wm.getAllServeEvents().getFirst().getRequest().getBodyAsString(); }
        finally { Files.deleteIfExists(privateGraphic); }
        // then
        assertThat(request).contains("Test stale caller teaser", "lineups-md1.png").doesNotContain("TEST-PRIVATE-STALE-SETTINGS-713f", "settings-md1.png");
    }

	@Test
	void givenExistingMatchPreviewRow_whenAutoEdit_thenWebhookPatchFiresOnce() throws Exception {
		String webhookPath = "/webhooks/940/tok-ae1";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Match match = seedMatchWithRace("A1", "Updated teaser", "https://twitch.tv/new");
		seedExistingPreviewPost(match, "940", "msg-ae1");
		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-ae1"))
				.willReturn(okJson("{\"id\":\"msg-ae1\",\"channel_id\":\"940\"}")));

		service.autoEditMatchPreviewIfNeeded(match);

		wm.verify(exactly(1), patchRequestedFor(urlPathEqualTo(webhookPath + "/messages/msg-ae1")));
	}

	@Test
	void givenNoExistingPreviewRow_whenAutoEdit_thenNoHttpRequest() throws Exception {
		String webhookPath = "/webhooks/941/tok-ae2";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Match match = seedMatchWithRace("A2", "Teaser", "https://twitch.tv/x");

		service.autoEditMatchPreviewIfNeeded(match);

		wm.verify(exactly(0), patchRequestedFor(urlPathMatching("/webhooks/.*/messages/.*")));
	}

	@Test
	void givenMissingWebhookConfig_whenAutoEdit_thenNoOpReturnsSilently() throws Exception {
		Match match = seedMatchWithRace("A3", "Teaser", null);

		service.autoEditMatchPreviewIfNeeded(match);

		wm.verify(exactly(0), patchRequestedFor(urlPathMatching("/webhooks/.*/messages/.*")));
	}

	@Test
	void givenNoRaceDateTime_whenAutoEdit_thenNoOpReturnsSilently() throws Exception {
		String webhookPath = "/webhooks/942/tok-ae4";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Match match = seedMatchWithRace("A4", "Teaser", null);
		seedExistingPreviewPost(match, "942", "msg-ae4");
		for (Race r : match.getRaces()) {
			r.setDateTime(null);
			raceRepository.save(r);
		}

		service.autoEditMatchPreviewIfNeeded(match);

		wm.verify(exactly(0), patchRequestedFor(urlPathMatching("/webhooks/.*/messages/.*")));
	}

	@Test
	void givenExistingRow_whenAutoEdit_thenAttachmentsReplacedAtAdvances() throws Exception {
		String webhookPath = "/webhooks/943/tok-ae5";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Match match = seedMatchWithRace("A5", "Updated", "https://twitch.tv/now");
		DiscordPost before = seedExistingPreviewPost(match, "943", "msg-ae5");
		LocalDateTime initialAttachments = before.getAttachmentsReplacedAt();
		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-ae5"))
				.willReturn(okJson("{\"id\":\"msg-ae5\",\"channel_id\":\"943\"}")));

		service.autoEditMatchPreviewIfNeeded(match);

		DiscordPost reloaded = discordPostRepository.findById(before.getId()).orElseThrow();
		assertThat(reloaded.getAttachmentsReplacedAt()).isNotNull();
		if (initialAttachments != null) {
			assertThat(reloaded.getAttachmentsReplacedAt()).isAfterOrEqualTo(initialAttachments);
		}
	}
}
