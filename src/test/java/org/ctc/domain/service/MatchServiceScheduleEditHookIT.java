package org.ctc.domain.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.LocalDateTime;
import org.ctc.TestHelper;
import org.ctc.admin.dto.MatchForm;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class MatchServiceScheduleEditHookIT {

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
	MatchService matchService;

	@Autowired
	TestHelper helper;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	RaceRepository raceRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@BeforeEach
	void resetState() {
		wm.resetAll();
		discordPostRepository.deleteAll();
	}

	@AfterEach
	void cleanup() {
		discordPostRepository.deleteAll();
	}

	private Match seedMatch(String suffix, String webhookUrl) {
		Season season = helper.createSeason("Hook Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-Hook-" + suffix, 0);
		Team home = helper.createTeam("Hook Home " + suffix, "hk-h" + suffix);
		Team away = helper.createTeam("Hook Away " + suffix, "hk-a" + suffix);
		Match match = helper.createMatch(md, home, away);
		Race race = helper.createRace(md, match);
		race.setDateTime(LocalDateTime.of(2026, 7, 1, 20, 0));
		raceRepository.save(race);
		match.getRaces().add(race);
		match.setDiscordChannelId("chan-hk-" + suffix);
		match.setDiscordChannelWebhookUrl(webhookUrl);
		match.setLobbyHost("InitialHost");
		match.setRaceDirector("InitialRD");
		match.setStreamer("InitialStr");
		return matchRepository.save(match);
	}

	private void seedSchedulePost(Match match) {
		DiscordPost p = new DiscordPost();
		p.setChannelId(match.getDiscordChannelId());
		p.setMessageId("msg-sched-" + match.getId().toString().substring(0, 8));
		p.setWebhookId("100");
		p.setWebhookToken("tok");
		p.setPostType(DiscordPostType.SCHEDULE);
		p.setMatchId(match.getId());
		p.setPostedAt(LocalDateTime.now());
		discordPostRepository.save(p);
	}

	private MatchForm formFrom(Match match, String lobbyHost, String raceDirector, String streamer) {
		MatchForm form = new MatchForm();
		form.setId(match.getId());
		form.setDiscordTeaser(match.getDiscordTeaser());
		form.setStreamLink(match.getStreamLink());
		form.setLobbyHost(lobbyHost);
		form.setRaceDirector(raceDirector);
		form.setStreamer(streamer);
		return form;
	}

	@Test
	void givenScheduleFieldsChangedAndSchedulePostExists_whenUpdateDiscordFields_thenWebhookPatchFiresOnce() throws Exception {
		String webhookPath = "/webhooks/920/tok-hk1";
		Match match = seedMatch("H1", wm.baseUrl() + webhookPath);
		seedSchedulePost(match);
		String messageId = "msg-sched-" + match.getId().toString().substring(0, 8);
		wm.stubFor(WireMock.patch(urlPathEqualTo(webhookPath + "/messages/" + messageId))
				.willReturn(okJson("{\"id\":\"" + messageId + "\",\"channel_id\":\"" + match.getDiscordChannelId() + "\"}")));

		MatchForm form = formFrom(match, "ChangedHost", match.getRaceDirector(), match.getStreamer());

		matchService.updateDiscordFields(match.getId(), form);

		wm.verify(patchRequestedFor(urlPathEqualTo(webhookPath + "/messages/" + messageId)));
	}

	@Test
	void givenScheduleFieldsUnchanged_whenUpdateDiscordFields_thenNoPatchFires() throws Exception {
		String webhookPath = "/webhooks/921/tok-hk2";
		Match match = seedMatch("H2", wm.baseUrl() + webhookPath);
		seedSchedulePost(match);

		MatchForm form = formFrom(match, match.getLobbyHost(), match.getRaceDirector(), match.getStreamer());

		matchService.updateDiscordFields(match.getId(), form);

		wm.verify(exactly(0), patchRequestedFor(urlPathMatching("/webhooks/.*/messages/.*")));
	}

	@Test
	void givenScheduleFieldsChangedAndNoSchedulePost_whenUpdateDiscordFields_thenNoPatchFires() throws Exception {
		String webhookPath = "/webhooks/922/tok-hk3";
		Match match = seedMatch("H3", wm.baseUrl() + webhookPath);

		MatchForm form = formFrom(match, "ChangedHost", match.getRaceDirector(), match.getStreamer());

		matchService.updateDiscordFields(match.getId(), form);

		wm.verify(exactly(0), patchRequestedFor(urlPathMatching("/webhooks/.*/messages/.*")));
	}

	@Test
	void givenWebhook5xx_whenUpdateDiscordFields_thenMatchSaveStillCommitsAndHookSwallows() throws Exception {
		String webhookPath = "/webhooks/923/tok-hk4";
		Match match = seedMatch("H4", wm.baseUrl() + webhookPath);
		seedSchedulePost(match);
		String messageId = "msg-sched-" + match.getId().toString().substring(0, 8);
		wm.stubFor(WireMock.patch(urlPathEqualTo(webhookPath + "/messages/" + messageId))
				.willReturn(aResponse().withStatus(500).withBody("server error")));

		MatchForm form = formFrom(match, "ChangedHost", match.getRaceDirector(), match.getStreamer());

		matchService.updateDiscordFields(match.getId(), form);

		Match reloaded = matchRepository.findById(match.getId()).orElseThrow();
		assertThat(reloaded.getLobbyHost()).isEqualTo("ChangedHost");
	}
}
