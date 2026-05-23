package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
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
import java.util.List;
import org.ctc.TestHelper;
import org.ctc.admin.service.ResultsGraphicService;
import org.ctc.discord.dto.DiscordPostRef;
import org.ctc.discord.dto.NamedAttachment;
import org.ctc.discord.dto.WebhookPayload;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.SeasonRepository;
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
class DiscordPostServiceForumThreadIT {

	private static final byte[] PNG_BYTES = new byte[] {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};
	private static final String THREAD_ID = "444444444444444444";

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
	SeasonRepository seasonRepository;

	@Autowired
	DiscordGlobalConfigRepository configRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@MockitoBean
	ResultsGraphicService resultsGraphicService;

	@BeforeEach
	void resetState() throws Exception {
		wm.resetAll();
		discordPostRepository.deleteAll();
		when(resultsGraphicService.generateResultsBytes(any(Race.class))).thenReturn(PNG_BYTES);
	}

	private Race seedRaceWithResults(String suffix, String webhookPath) {
		Season season = helper.createSeason("Forum " + suffix);
		season.setDiscordRaceResultsThreadId(THREAD_ID);
		seasonRepository.save(season);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-F-" + suffix, 0);
		Team home = helper.createTeam("Forum Home " + suffix, "fh" + suffix);
		Team away = helper.createTeam("Forum Away " + suffix, "fa" + suffix);
		season.addTeam(home);
		season.addTeam(away);
		Match match = helper.createMatch(md, home, away);
		Driver driver = helper.createDriver("PSN-F-" + suffix, "Driver F-" + suffix);
		Race race = helper.createRace(md, match);
		RaceResult result = new RaceResult(race, driver, 1, 1, false);
		race.getResults().add(result);
		match.getRaces().add(race);

		DiscordGlobalConfig config = configRepository.findFirstByOrderByIdAsc();
		if (config == null) {
			config = new DiscordGlobalConfig();
		}
		config.setRaceResultsForumWebhookUrl(wm.baseUrl() + webhookPath);
		configRepository.save(config);
		return race;
	}

	@Test
	void givenAllPreFlightGreenAndThreadNotArchived_whenPostRaceResultToForumThread_thenWebhookPostWithThreadId()
			throws Exception {
		String webhookPath = "/webhooks/700/abc";
		Race race = seedRaceWithResults("A", webhookPath);
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + THREAD_ID))
				.willReturn(okJson("{\"id\":\"" + THREAD_ID + "\",\"name\":\"t\",\"type\":11,"
						+ "\"thread_metadata\":{\"archived\":false}}")));
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-forum-1\",\"channel_id\":\"700\"}")));

		DiscordPost saved = service.postRaceResultToForumThread(race);

		assertThat(saved.getMessageId()).isEqualTo("msg-forum-1");
		assertThat(saved.getRaceId()).isEqualTo(race.getId());
		wm.verify(getRequestedFor(urlPathEqualTo("/api/v10/channels/" + THREAD_ID)));
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
				.withQueryParam("thread_id", equalTo(THREAD_ID))
				.withRequestBodyPart(aMultipart("files[0]").build()));
	}

	@Test
	void givenArchivedThread_whenPostRaceResultToForumThread_thenUnarchivesBeforePost() throws Exception {
		String webhookPath = "/webhooks/701/abc";
		Race race = seedRaceWithResults("B", webhookPath);
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + THREAD_ID))
				.willReturn(okJson("{\"id\":\"" + THREAD_ID + "\",\"name\":\"t\",\"type\":11,"
						+ "\"thread_metadata\":{\"archived\":true}}")));
		wm.stubFor(patch(urlPathEqualTo("/api/v10/channels/" + THREAD_ID))
				.willReturn(okJson("{\"id\":\"" + THREAD_ID + "\",\"name\":\"t\",\"type\":11}")));
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-forum-2\",\"channel_id\":\"701\"}")));

		service.postRaceResultToForumThread(race);

		wm.verify(patchRequestedFor(urlPathEqualTo("/api/v10/channels/" + THREAD_ID)));
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
				.withQueryParam("thread_id", equalTo(THREAD_ID)));
	}

	@Test
	void givenNotArchivedThread_whenPostRaceResultToForumThread_thenNoPatchIssued() throws Exception {
		String webhookPath = "/webhooks/702/abc";
		Race race = seedRaceWithResults("C", webhookPath);
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + THREAD_ID))
				.willReturn(okJson("{\"id\":\"" + THREAD_ID + "\",\"name\":\"t\",\"type\":11,"
						+ "\"thread_metadata\":{\"archived\":false}}")));
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-forum-3\",\"channel_id\":\"702\"}")));

		service.postRaceResultToForumThread(race);

		wm.verify(0, patchRequestedFor(urlPathEqualTo("/api/v10/channels/" + THREAD_ID)));
	}

	@Test
	void givenExistingRaceResultsPost_whenPostRaceResultToForumThread_thenPatchWithThreadId() throws Exception {
		String webhookPath = "/webhooks/703/abc";
		Race race = seedRaceWithResults("D", webhookPath);
		wm.stubFor(get(urlPathEqualTo("/api/v10/channels/" + THREAD_ID))
				.willReturn(okJson("{\"id\":\"" + THREAD_ID + "\",\"name\":\"t\",\"type\":11,"
						+ "\"thread_metadata\":{\"archived\":false}}")));
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-forum-4\",\"channel_id\":\"703\"}")));
		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-forum-4"))
				.willReturn(okJson("{\"id\":\"msg-forum-4\",\"channel_id\":\"703\"}")));

		service.postRaceResultToForumThread(race);
		service.postRaceResultToForumThread(race);

		wm.verify(patchRequestedFor(urlPathEqualTo(webhookPath + "/messages/msg-forum-4"))
				.withQueryParam("thread_id", equalTo(THREAD_ID))
				.withRequestBodyPart(aMultipart("files[0]").build()));
	}

	@Test
	void givenNoRaceResults_whenPostRaceResultToForumThread_thenBusinessRuleException() {
		Season season = helper.createSeason("Forum E");
		season.setDiscordRaceResultsThreadId(THREAD_ID);
		seasonRepository.save(season);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-F-E", 0);
		Team home = helper.createTeam("Forum Home E", "fhE");
		Team away = helper.createTeam("Forum Away E", "faE");
		season.addTeam(home);
		season.addTeam(away);
		Match match = helper.createMatch(md, home, away);
		Race race = helper.createRace(md, match);
		match.getRaces().add(race);

		DiscordGlobalConfig config = configRepository.findFirstByOrderByIdAsc();
		if (config == null) {
			config = new DiscordGlobalConfig();
		}
		config.setRaceResultsForumWebhookUrl("https://discord.com/api/webhooks/704/abc");
		configRepository.save(config);

		assertThatThrownBy(() -> service.postRaceResultToForumThread(race))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("Race result cannot be posted");
	}

	@Test
	void givenNullThreadId_whenPostOrEditViaSixArg_thenNoThreadIdAppendedAndNoGetChannelCall() throws Exception {
		String webhookPath = "/webhooks/705/abc";
		Race race = seedRaceWithResults("F", webhookPath);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-no-thread\",\"channel_id\":\"705\"}")));

		service.postOrEdit(
				"705",
				wm.baseUrl() + webhookPath,
				DiscordPostType.RACE_RESULTS,
				WebhookPayload.empty(),
				List.of(new NamedAttachment("file.png", PNG_BYTES)),
				DiscordPostRef.race(race));

		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
				.withQueryParam("thread_id", absent()));
		wm.verify(0, getRequestedFor(urlPathEqualTo("/api/v10/channels/" + THREAD_ID)));
	}
}
