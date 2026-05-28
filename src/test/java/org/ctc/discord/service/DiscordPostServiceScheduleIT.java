package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.LocalDateTime;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordPost;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class DiscordPostServiceScheduleIT {

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

	@BeforeEach
	void resetWireMock() {
		wm.resetAll();
	}

	private Match seedMatchWithRace(
			String suffix, String webhookUrl, LocalDateTime raceTime,
			String lobbyHost, String raceDirector, String streamer) {
		Season season = helper.createSeason("Sched Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-Sched-" + suffix, 0);
		Team home = helper.createTeam("Sched Home " + suffix, "sc-h" + suffix);
		Team away = helper.createTeam("Sched Away " + suffix, "sc-a" + suffix);
		Match match = helper.createMatch(md, home, away);
		Race race = helper.createRace(md, match);
		race.setDateTime(raceTime);
		raceRepository.save(race);
		match.getRaces().add(race);
		match.setDiscordChannelId("chan-sc-" + suffix);
		match.setDiscordChannelWebhookUrl(webhookUrl);
		match.setLobbyHost(lobbyHost);
		match.setRaceDirector(raceDirector);
		match.setStreamer(streamer);
		return matchRepository.save(match);
	}

	@Test
	void givenRaceWithDateTime_whenPostSchedule_thenJsonPostWithEmbedContaining4Fields() throws Exception {
		String webhookPath = "/webhooks/910/tok-sc1";
		Match match = seedMatchWithRace("S1", wm.baseUrl() + webhookPath,
				LocalDateTime.of(2026, 6, 1, 20, 30), "Alice", "Bob", "Charlie");
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withHeader("Content-Type", containing("application/json"))
				.withRequestBody(matchingJsonPath("$.embeds[0].title", containing("Match Schedule")))
				.withRequestBody(matchingJsonPath("$.embeds[0].fields[0].name", containing("Date")))
				.withRequestBody(matchingJsonPath("$.embeds[0].fields[0].inline", equalTo("false")))
				.withRequestBody(matchingJsonPath("$.embeds[0].fields[1].value", containing("Alice")))
				.withRequestBody(matchingJsonPath("$.embeds[0].fields[1].inline", equalTo("false")))
				.withRequestBody(matchingJsonPath("$.embeds[0].fields[2].value", containing("Bob")))
				.withRequestBody(matchingJsonPath("$.embeds[0].fields[2].inline", equalTo("false")))
				.withRequestBody(matchingJsonPath("$.embeds[0].fields[3].value", containing("Charlie")))
				.withRequestBody(matchingJsonPath("$.embeds[0].fields[3].inline", equalTo("false")))
				.willReturn(okJson("{\"id\":\"msg-sc1\",\"channel_id\":\"chan-sc-S1\"}")));

		DiscordPost saved = service.postSchedule(match);

		assertThat(saved.getMessageId()).isEqualTo("msg-sc1");
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath)));
	}

	@Test
	void givenNullLobbyHost_whenPostSchedule_thenRendersTbdPlaceholder() throws Exception {
		String webhookPath = "/webhooks/911/tok-sc2";
		Match match = seedMatchWithRace("S2", wm.baseUrl() + webhookPath,
				LocalDateTime.of(2026, 6, 1, 20, 30), null, "Bob", "Charlie");
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withRequestBody(matchingJsonPath("$.embeds[0].fields[1].value", containing("_TBD_")))
				.willReturn(okJson("{\"id\":\"msg-sc2\",\"channel_id\":\"chan-sc-S2\"}")));

		DiscordPost saved = service.postSchedule(match);

		assertThat(saved.getMessageId()).isEqualTo("msg-sc2");
	}

	@Test
	void givenStreamerAndStreamLink_whenPostSchedule_thenStreamerFieldIsMarkdownLink() throws Exception {
		String webhookPath = "/webhooks/915/tok-sc-link";
		Match match = seedMatchWithRace("SL1", wm.baseUrl() + webhookPath,
				LocalDateTime.of(2026, 6, 1, 20, 30), "Alice", "Bob", "JeGr");
		match.setStreamLink("https://youtu.be/Zc4BTe274Ig");
		matchRepository.save(match);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withRequestBody(matchingJsonPath("$.embeds[0].fields[3].value",
						containing("[JeGr](https://youtu.be/Zc4BTe274Ig)")))
				.willReturn(okJson("{\"id\":\"msg-sc-link\",\"channel_id\":\"chan-sc-SL1\"}")));

		DiscordPost saved = service.postSchedule(match);

		assertThat(saved.getMessageId()).isEqualTo("msg-sc-link");
	}

	@Test
	void givenStreamLinkOnly_whenPostSchedule_thenStreamerFieldIsWatchStreamLink() throws Exception {
		String webhookPath = "/webhooks/916/tok-sc-link2";
		Match match = seedMatchWithRace("SL2", wm.baseUrl() + webhookPath,
				LocalDateTime.of(2026, 6, 1, 20, 30), "Alice", "Bob", null);
		match.setStreamLink("https://twitch.tv/foo");
		matchRepository.save(match);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withRequestBody(matchingJsonPath("$.embeds[0].fields[3].value",
						containing("[Watch Stream](https://twitch.tv/foo)")))
				.willReturn(okJson("{\"id\":\"msg-sc-link2\",\"channel_id\":\"chan-sc-SL2\"}")));

		DiscordPost saved = service.postSchedule(match);

		assertThat(saved.getMessageId()).isEqualTo("msg-sc-link2");
	}

	@Test
	void givenBlankStreamer_whenPostSchedule_thenRendersTbdPlaceholder() throws Exception {
		String webhookPath = "/webhooks/912/tok-sc3";
		Match match = seedMatchWithRace("S3", wm.baseUrl() + webhookPath,
				LocalDateTime.of(2026, 6, 1, 20, 30), "Alice", "Bob", "  ");
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withRequestBody(matchingJsonPath("$.embeds[0].fields[3].value", containing("_TBD_")))
				.willReturn(okJson("{\"id\":\"msg-sc3\",\"channel_id\":\"chan-sc-S3\"}")));

		DiscordPost saved = service.postSchedule(match);

		assertThat(saved.getMessageId()).isEqualTo("msg-sc3");
	}

	@Test
	void givenRaceTime_whenPostSchedule_thenDateFieldUsesDiscordTimestampFormat() throws Exception {
		String webhookPath = "/webhooks/913/tok-sc4";
		Match match = seedMatchWithRace("S4", wm.baseUrl() + webhookPath,
				LocalDateTime.of(2026, 6, 1, 20, 30), "Alice", "Bob", "Charlie");
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withRequestBody(matchingJsonPath("$.embeds[0].fields[0].value", containing("<t:")))
				.willReturn(okJson("{\"id\":\"msg-sc4\",\"channel_id\":\"chan-sc-S4\"}")));

		DiscordPost saved = service.postSchedule(match);

		assertThat(saved.getMessageId()).isEqualTo("msg-sc4");
	}

	@Test
	void givenNoRaceDateTime_whenPostSchedule_thenBusinessRuleException() {
		Match match = seedMatchWithRace("S5", wm.baseUrl() + "/webhooks/914/tok-sc5",
				null, "Alice", "Bob", "Charlie");

		assertThatThrownBy(() -> service.postSchedule(match))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("Schedule requires at least one race with a scheduled time.");
	}
}
