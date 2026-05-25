package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.LocalDateTime;
import org.ctc.TestHelper;
import org.ctc.admin.service.MatchdayScheduleGraphicService;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
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
class DiscordPostServiceMatchdayScheduleIT {

	private static final byte[] PNG_BYTES;

	static {
		byte[] header = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
		byte[] payload = new byte[2048];
		System.arraycopy(header, 0, payload, 0, header.length);
		PNG_BYTES = payload;
	}

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
	MatchdayRepository matchdayRepository;

	@Autowired
	RaceRepository raceRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@Autowired
	DiscordGlobalConfigRepository globalConfigRepository;

	@Autowired
	DiscordGlobalConfigService globalConfigService;

	@MockitoBean
	MatchdayScheduleGraphicService matchdayScheduleGraphicService;

	@BeforeEach
	void resetState() throws Exception {
		wm.resetAll();
		discordPostRepository.deleteAll();
		when(matchdayScheduleGraphicService.generateSchedule(any(Matchday.class))).thenReturn(PNG_BYTES);
	}

	private Matchday seedMatchday(String suffix, boolean withRaceTimes) {
		Season season = helper.createSeason("Test-Schedule Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "Match Day " + suffix, 0);
		Team home = helper.createTeam("T-Schedule-Home-" + suffix, "tsh" + suffix);
		Team away = helper.createTeam("T-Schedule-Away-" + suffix, "tsa" + suffix);
		Match match = helper.createMatch(md, home, away);
		Race race = helper.createRace(md, match);
		if (withRaceTimes) {
			race.setDateTime(LocalDateTime.of(2026, 5, 30, 19, 0));
			raceRepository.save(race);
		}
		match.getRaces().add(race);
		matchRepository.save(match);
		md.getMatches().add(match);
		matchdayRepository.save(md);
		return md;
	}

	private void setAnnouncementWebhook(String webhookUrl) {
		DiscordGlobalConfig cfg = globalConfigService.getOrInitialize();
		cfg.setAnnouncementWebhookUrl(webhookUrl);
		globalConfigRepository.save(cfg);
	}

	@Test
	void givenAllPreFlightOk_whenPostMatchdaySchedule_thenPureMultipartPngPosted() throws Exception {
		String webhookPath = "/webhooks/995/tok-ms1";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Matchday md = seedMatchday("S1", true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-ms1\",\"channel_id\":\"995\"}")));

		DiscordPost saved = service.postMatchdaySchedule(md);

		assertThat(saved.getMessageId()).isEqualTo("msg-ms1");
		assertThat(saved.getPostType()).isEqualTo(DiscordPostType.MATCHDAY_SCHEDULE);
		assertThat(saved.getChannelId()).isEqualTo("995");
		assertThat(saved.getMatchdayId()).isEqualTo(md.getId());

		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
				.withRequestBodyPart(aMultipart("files[0]").build()));

		String body = wm.findAll(postRequestedFor(urlPathEqualTo(webhookPath))).get(0).getBodyAsString();
		assertThat(body).contains("matchday-schedule-match-day-s1.png");
		assertThat(body.length()).isGreaterThan(1024);
		assertThat(body).doesNotContain("\"content\":");
		assertThat(body).doesNotContain("\"embeds\":[{");
	}

	@Test
	void givenExistingPost_whenPostMatchdaySchedule_thenPatchesSameMessage() throws Exception {
		String webhookPath = "/webhooks/996/tok-ms2";
		String webhookUrl = wm.baseUrl() + webhookPath;
		setAnnouncementWebhook(webhookUrl);
		Matchday md = seedMatchday("S2", true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-ms2\",\"channel_id\":\"996\"}")));
		DiscordPost first = service.postMatchdaySchedule(md);

		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-ms2"))
				.willReturn(okJson("{\"id\":\"msg-ms2\",\"channel_id\":\"996\"}")));

		DiscordPost second = service.postMatchdaySchedule(md);

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(second.getAttachmentsReplacedAt()).isNotNull();
		wm.verify(patchRequestedFor(urlPathEqualTo(webhookPath + "/messages/msg-ms2")));
	}

	@Test
	void givenMatchWithoutRaceDateTime_whenPostMatchdaySchedule_thenThrowsBusinessRuleException() {
		String webhookPath = "/webhooks/997/tok-ms3";
		setAnnouncementWebhook(wm.baseUrl() + webhookPath);
		Matchday md = seedMatchday("S3", false);

		assertThatThrownBy(() -> service.postMatchdaySchedule(md))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("Set Race date+time for all matches first");
	}

	@Test
	void givenBlankAnnouncementWebhook_whenPostMatchdaySchedule_thenThrowsBusinessRuleException() {
		setAnnouncementWebhook("");
		Matchday md = seedMatchday("S4", true);

		assertThatThrownBy(() -> service.postMatchdaySchedule(md))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("Configure announcement-webhook in Discord settings");
	}
}
