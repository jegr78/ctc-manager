package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import org.ctc.TestHelper;
import org.ctc.discord.dto.DiscordPostRef;
import org.ctc.discord.dto.NamedAttachment;
import org.ctc.discord.dto.WebhookPayload;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
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
class DiscordPostServiceWireMockIT {

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
	private DiscordPostService service;

	@Autowired
	private TestHelper helper;

	@BeforeEach
	void resetWireMock() {
		wm.resetAll();
	}

	private Match seedMatch(String suffix) {
		Season season = helper.createSeason("DPS Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-1-" + suffix, 0);
		Team home = helper.createTeam("Home " + suffix, "h" + suffix);
		Team away = helper.createTeam("Away " + suffix, "a" + suffix);
		return helper.createMatch(md, home, away);
	}

	@Test
	void givenNoExistingRow_whenPostOrEditWithoutAttachments_thenInsertsRowAndCallsJsonPost() throws Exception {
		Match match = seedMatch("NA");
		String webhookPath = "/webhooks/100/tok-abc";
		String webhookUrl = wm.baseUrl() + webhookPath;
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withHeader("Content-Type", containing("application/json"))
				.willReturn(okJson("{\"id\":\"msg-json\",\"channel_id\":\"chan-x\"}")));

		DiscordPost saved = service.postOrEdit(
				"chan-x",
				webhookUrl,
				DiscordPostType.SCHEDULE,
				new WebhookPayload("schedule embed", List.of()),
				List.of(),
				DiscordPostRef.match(match));

		assertThat(saved.getMessageId()).isEqualTo("msg-json");
		assertThat(saved.getWebhookId()).isEqualTo("100");
		assertThat(saved.getWebhookToken()).isEqualTo("tok-abc");
		assertThat(saved.getChannelId()).isEqualTo("chan-x");
		assertThat(saved.getPostType()).isEqualTo(DiscordPostType.SCHEDULE);
		assertThat(saved.getMatchId()).isEqualTo(match.getId());
		assertThat(saved.getAttachmentsReplacedAt()).isNull();
	}

	@Test
	void givenNoExistingRow_whenPostOrEditWithAttachments_thenInsertsRowAndCallsMultipartPost() throws Exception {
		Match match = seedMatch("MP");
		String webhookPath = "/webhooks/200/tok-def";
		String webhookUrl = wm.baseUrl() + webhookPath;
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withHeader("Content-Type", containing("multipart/form-data"))
				.willReturn(okJson("{\"id\":\"msg-mp\",\"channel_id\":\"chan-y\"}")));

		DiscordPost saved = service.postOrEdit(
				"chan-y",
				webhookUrl,
				DiscordPostType.TEAM_CARDS,
				new WebhookPayload("team cards", List.of()),
				List.of(new NamedAttachment("a.png", PNG_BYTES), new NamedAttachment("b.png", PNG_BYTES)),
				DiscordPostRef.match(match));

		assertThat(saved.getMessageId()).isEqualTo("msg-mp");
		assertThat(saved.getWebhookId()).isEqualTo("200");
		assertThat(saved.getAttachmentsReplacedAt()).isNotNull();
	}

	@Test
	void givenExistingRowWithoutAttachments_whenPostOrEditWithoutAttachments_thenCallsJsonPatchAndPreservesAttachmentsReplacedAt() throws Exception {
		Match match = seedMatch("JE");
		String webhookPath = "/webhooks/300/tok-ghi";
		String webhookUrl = wm.baseUrl() + webhookPath;
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-pre\",\"channel_id\":\"chan-z\"}")));

		DiscordPost initial = service.postOrEdit(
				"chan-z",
				webhookUrl,
				DiscordPostType.SCHEDULE,
				new WebhookPayload("first", List.of()),
				List.of(),
				DiscordPostRef.match(match));
		Long initialId = initial.getId();

		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-pre"))
				.withHeader("Content-Type", containing("application/json"))
				.willReturn(okJson("{\"id\":\"msg-pre\",\"channel_id\":\"chan-z\"}")));

		DiscordPost edited = service.postOrEdit(
				"chan-z",
				webhookUrl,
				DiscordPostType.SCHEDULE,
				new WebhookPayload("edited", List.of()),
				List.of(),
				DiscordPostRef.match(match));

		assertThat(edited.getId()).isEqualTo(initialId);
		assertThat(edited.getMessageId()).isEqualTo("msg-pre");
		assertThat(edited.getAttachmentsReplacedAt()).isNull();
	}

	@Test
	void givenExistingRow_whenPostOrEditWithAttachments_thenCallsMultipartPatchAndStampsAttachmentsReplacedAt() throws Exception {
		Match match = seedMatch("ME");
		String webhookPath = "/webhooks/400/tok-jkl";
		String webhookUrl = wm.baseUrl() + webhookPath;
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(200)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"id\":\"msg-mp2\",\"channel_id\":\"chan-q\"}")));

		DiscordPost initial = service.postOrEdit(
				"chan-q",
				webhookUrl,
				DiscordPostType.TEAM_CARDS,
				new WebhookPayload("first", List.of()),
				List.of(new NamedAttachment("a.png", PNG_BYTES)),
				DiscordPostRef.match(match));

		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-mp2"))
				.withHeader("Content-Type", containing("multipart/form-data"))
				.willReturn(okJson("{\"id\":\"msg-mp2\",\"channel_id\":\"chan-q\"}")));

		DiscordPost edited = service.postOrEdit(
				"chan-q",
				webhookUrl,
				DiscordPostType.TEAM_CARDS,
				new WebhookPayload("re-post", List.of()),
				List.of(new NamedAttachment("a.png", PNG_BYTES), new NamedAttachment("b.png", PNG_BYTES)),
				DiscordPostRef.match(match));

		assertThat(edited.getId()).isEqualTo(initial.getId());
		assertThat(edited.getAttachmentsReplacedAt()).isNotNull();
	}
}
