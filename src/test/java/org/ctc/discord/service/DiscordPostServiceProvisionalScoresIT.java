package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.TestHelper;
import org.ctc.admin.service.ProvisionalScoresGraphicService;
import org.ctc.discord.model.DiscordPost;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceResultRepository;
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
class DiscordPostServiceProvisionalScoresIT {

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
	RaceResultRepository raceResultRepository;

	@MockitoBean
	ProvisionalScoresGraphicService provisionalScoresGraphicService;

	@BeforeEach
	void resetState() throws Exception {
		wm.resetAll();
		when(provisionalScoresGraphicService.generateProvisional(any(Race.class), anyInt())).thenReturn(PNG_BYTES);
	}

	private Match seedMatchWith3Races(String suffix, String webhookUrl, int racesWithResults) {
		Season season = helper.createSeason("PS Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-PS-" + suffix, 0);
		Team home = helper.createTeam("PS Home " + suffix, "ps-h" + suffix);
		Team away = helper.createTeam("PS Away " + suffix, "ps-a" + suffix);
		season.addTeam(home);
		season.addTeam(away);
		Match match = helper.createMatch(md, home, away);
		Driver driver = helper.createDriver("PSN-PS-" + suffix, "Driver PS-" + suffix);
		for (int i = 0; i < 3; i++) {
			Race race = helper.createRace(md, match);
			if (i < racesWithResults) {
				RaceResult result = new RaceResult(race, driver, 1, 1, false);
				raceResultRepository.save(result);
				race.getResults().add(result);
			}
			match.getRaces().add(race);
		}
		match.setDiscordChannelId("chan-ps-" + suffix);
		match.setDiscordChannelWebhookUrl(webhookUrl);
		return matchRepository.save(match);
	}

	@Test
	void matchHasProvisionalData_returnsFalseWhenNoRaces() {
		Match empty = new Match();
		assertThat(service.matchHasProvisionalData(empty)).isFalse();
	}

	@Test
	void matchHasProvisionalData_returnsFalseWhenAllRacesEmpty() {
		Match match = seedMatchWith3Races("HP-NoData", wm.baseUrl() + "/webhooks/910/tok-hp1", 0);
		assertThat(service.matchHasProvisionalData(match)).isFalse();
	}

	@Test
	void matchHasProvisionalData_returnsTrueWhenAtLeastOneRaceHasResults() {
		Match match = seedMatchWith3Races("HP-Some", wm.baseUrl() + "/webhooks/910/tok-hp2", 1);
		assertThat(service.matchHasProvisionalData(match)).isTrue();
	}

	@Test
	void given2of3RacesHaveResults_whenPostProvisionalScores_thenSingleMultipartPostWithTwoNamedAttachments()
			throws Exception {
		String webhookPath = "/webhooks/911/tok-ps1";
		Match match = seedMatchWith3Races("PS1", wm.baseUrl() + webhookPath, 2);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withHeader("Content-Type", containing("multipart/form-data"))
				.withMultipartRequestBody(aMultipart("files[0]")
						.withHeader("Content-Type", equalTo("image/png"))
						.withHeader("Content-Disposition", containing("provisional-race-1.png")))
				.withMultipartRequestBody(aMultipart("files[1]")
						.withHeader("Content-Type", equalTo("image/png"))
						.withHeader("Content-Disposition", containing("provisional-race-2.png")))
				.willReturn(okJson("{\"id\":\"msg-ps1\",\"channel_id\":\"chan-ps-PS1\"}")));

		DiscordPost saved = service.postProvisionalScores(match);

		assertThat(saved.getMessageId()).isEqualTo("msg-ps1");
		assertThat(saved.getAttachmentsReplacedAt()).isNotNull();
		verify(provisionalScoresGraphicService, org.mockito.Mockito.times(2))
				.generateProvisional(any(Race.class), anyInt());
	}

	@Test
	void givenExistingPostRow_whenPostProvisionalScores_thenMultipartPatchAndStampsAttachmentsReplacedAt()
			throws Exception {
		String webhookPath = "/webhooks/912/tok-ps2";
		Match match = seedMatchWith3Races("PS2", wm.baseUrl() + webhookPath, 2);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-ps2\",\"channel_id\":\"chan-ps-PS2\"}")));
		DiscordPost first = service.postProvisionalScores(match);

		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-ps2"))
				.willReturn(okJson("{\"id\":\"msg-ps2\",\"channel_id\":\"chan-ps-PS2\"}")));
		DiscordPost second = service.postProvisionalScores(match);

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(second.getAttachmentsReplacedAt()).isNotNull();
	}

	@Test
	void givenAllRacesEmpty_whenPostProvisionalScores_thenBusinessRuleException() {
		Match match = seedMatchWith3Races("PS3", wm.baseUrl() + "/webhooks/913/tok-ps3", 0);

		assertThatThrownBy(() -> service.postProvisionalScores(match))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("Provisional needs at least one completed race");
	}

	@Test
	void noThreadIdEverAppended() throws Exception {
		// D-96-GRX-1c: PROVISIONAL_SCORES targets the match-channel only, never a forum-thread.
		String webhookPath = "/webhooks/914/tok-ps4";
		Match match = seedMatchWith3Races("PS4", wm.baseUrl() + webhookPath, 2);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-ps4\",\"channel_id\":\"chan-ps-PS4\"}")));

		service.postProvisionalScores(match);

		long withThreadId = wm.findAll(anyRequestedFor(urlMatching(".*thread_id=.*"))).size();
		assertThat(withThreadId)
				.as("PROVISIONAL_SCORES must never include ?thread_id= (D-96-GRX-1c)")
				.isZero();
	}

	@Test
	void givenSameRacesInSameOrder_whenRePost_thenSameFilenames() throws Exception {
		// Filename-stability lock: iterator-counter is the single source of truth, so re-post
		// of the same match (no data change) MUST produce the same filename list as the initial post.
		String webhookPath = "/webhooks/915/tok-ps5";
		Match match = seedMatchWith3Races("PS5", wm.baseUrl() + webhookPath, 2);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-ps5\",\"channel_id\":\"chan-ps-PS5\"}")));
		service.postProvisionalScores(match);

		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-ps5"))
				.willReturn(okJson("{\"id\":\"msg-ps5\",\"channel_id\":\"chan-ps-PS5\"}")));
		service.postProvisionalScores(match);

		long postWithRace1 = wm.findAll(postRequestedFor(urlPathEqualTo(webhookPath))
				.withRequestBodyPart(aMultipart("files[0]")
						.withHeader("Content-Disposition", containing("provisional-race-1.png")).build())).size();
		long postWithRace2 = wm.findAll(postRequestedFor(urlPathEqualTo(webhookPath))
				.withRequestBodyPart(aMultipart("files[1]")
						.withHeader("Content-Disposition", containing("provisional-race-2.png")).build())).size();
		long patchWithRace1 = wm.findAll(patchRequestedFor(urlPathEqualTo(webhookPath + "/messages/msg-ps5"))
				.withRequestBodyPart(aMultipart("files[0]")
						.withHeader("Content-Disposition", containing("provisional-race-1.png")).build())).size();
		long patchWithRace2 = wm.findAll(patchRequestedFor(urlPathEqualTo(webhookPath + "/messages/msg-ps5"))
				.withRequestBodyPart(aMultipart("files[1]")
						.withHeader("Content-Disposition", containing("provisional-race-2.png")).build())).size();
		assertThat(postWithRace1).isEqualTo(1);
		assertThat(postWithRace2).isEqualTo(1);
		assertThat(patchWithRace1).isEqualTo(1);
		assertThat(patchWithRace2).isEqualTo(1);
	}
}
