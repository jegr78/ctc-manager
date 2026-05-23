package org.ctc.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.ArrayList;
import java.util.List;
import org.ctc.discord.dto.NamedAttachment;
import org.ctc.discord.dto.WebhookMessage;
import org.ctc.discord.dto.WebhookPayload;
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
class DiscordWebhookClientMultipartEditIT {

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
	private DiscordWebhookClient client;

	@BeforeEach
	void resetWireMock() {
		wm.resetAll();
	}

	@Test
	void givenEmptyAttachments_whenEditMessageWithAttachments_thenDelegatesToJsonEditMessage() throws Exception {
		String webhookPath = "/api/v10/webhooks/100/abc";
		String messageId = "msg-edit-1";
		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/" + messageId))
				.withHeader("Content-Type", containing("application/json"))
				.willReturn(okJson("{\"id\":\"msg-edit-1\",\"channel_id\":\"chan-1\"}")));

		WebhookMessage out = client.editMessageWithAttachments(
				wm.baseUrl() + webhookPath,
				messageId,
				new WebhookPayload("edit, no files", List.of()),
				List.of());

		assertThat(out.id()).isEqualTo("msg-edit-1");
	}

	@Test
	void givenOneAttachment_whenEditMessageWithAttachments_thenSendsMultipartPatch() throws Exception {
		String webhookPath = "/api/v10/webhooks/100/abc";
		String messageId = "msg-edit-2";
		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/" + messageId))
				.withHeader("Content-Type", containing("multipart/form-data"))
				.withMultipartRequestBody(aMultipart("payload_json")
						.withHeader("Content-Type", containing("application/json"))
						.withBody(matchingJsonPath("$.content", containing("hello"))))
				.withMultipartRequestBody(aMultipart("files[0]")
						.withHeader("Content-Type", equalTo("image/png")))
				.willReturn(okJson("{\"id\":\"msg-edit-2\",\"channel_id\":\"chan-1\"}")));

		WebhookMessage out = client.editMessageWithAttachments(
				wm.baseUrl() + webhookPath,
				messageId,
				new WebhookPayload("hello", List.of()),
				List.of(new NamedAttachment("a.png", PNG_BYTES)));

		assertThat(out.id()).isEqualTo("msg-edit-2");
		wm.verify(patchRequestedFor(urlPathEqualTo(webhookPath + "/messages/" + messageId)));
	}

	@Test
	void givenTwoAttachments_whenEditMessageWithAttachments_thenIndexesFilesPartsCorrectly() throws Exception {
		String webhookPath = "/api/v10/webhooks/100/abc";
		String messageId = "msg-edit-3";
		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/" + messageId))
				.withHeader("Content-Type", containing("multipart/form-data"))
				.withMultipartRequestBody(aMultipart("files[0]")
						.withHeader("Content-Type", equalTo("image/png")))
				.withMultipartRequestBody(aMultipart("files[1]")
						.withHeader("Content-Type", equalTo("image/png")))
				.willReturn(okJson("{\"id\":\"msg-edit-3\",\"channel_id\":\"chan-1\"}")));

		WebhookMessage out = client.editMessageWithAttachments(
				wm.baseUrl() + webhookPath,
				messageId,
				new WebhookPayload("two", List.of()),
				List.of(
						new NamedAttachment("a.png", PNG_BYTES),
						new NamedAttachment("b.png", PNG_BYTES)));

		assertThat(out.id()).isEqualTo("msg-edit-3");
	}

	@Test
	void givenTwoAttachments_whenEditMessageWithAttachments_thenPayloadJsonDeclaresAttachmentsArrayToDropOldFiles() throws Exception {
		String webhookPath = "/api/v10/webhooks/100/abc";
		String messageId = "msg-edit-drop";
		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/" + messageId))
				.withMultipartRequestBody(aMultipart("payload_json")
						.withBody(matchingJsonPath("$.attachments[0].id", equalTo("0")))
						.withBody(matchingJsonPath("$.attachments[0].filename", equalTo("home.png")))
						.withBody(matchingJsonPath("$.attachments[1].id", equalTo("1")))
						.withBody(matchingJsonPath("$.attachments[1].filename", equalTo("away.png"))))
				.willReturn(okJson("{\"id\":\"msg-edit-drop\",\"channel_id\":\"chan-1\"}")));

		WebhookMessage out = client.editMessageWithAttachments(
				wm.baseUrl() + webhookPath,
				messageId,
				new WebhookPayload(null, List.of()),
				List.of(
						new NamedAttachment("home.png", PNG_BYTES),
						new NamedAttachment("away.png", PNG_BYTES)));

		assertThat(out).as("Discord drops previous attachments only when payload_json declares the new ones").isNotNull();
		assertThat(out.id()).isEqualTo("msg-edit-drop");
	}

	@Test
	void givenTenAttachments_whenEditMessageWithAttachments_thenAcceptsAtBoundary() throws Exception {
		String webhookPath = "/api/v10/webhooks/100/abc";
		String messageId = "msg-edit-4";
		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/" + messageId))
				.willReturn(okJson("{\"id\":\"msg-edit-4\",\"channel_id\":\"chan-1\"}")));

		List<NamedAttachment> attachments = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			attachments.add(new NamedAttachment("a" + i + ".png", PNG_BYTES));
		}

		WebhookMessage out = client.editMessageWithAttachments(
				wm.baseUrl() + webhookPath,
				messageId,
				new WebhookPayload("ten", List.of()),
				attachments);

		assertThat(out.id()).isEqualTo("msg-edit-4");
	}

	@Test
	void givenElevenAttachments_whenEditMessageWithAttachments_thenThrowsIllegalArgumentException() {
		String webhookPath = "/api/v10/webhooks/100/abc";
		List<NamedAttachment> attachments = new ArrayList<>();
		for (int i = 0; i < 11; i++) {
			attachments.add(new NamedAttachment("a" + i + ".png", PNG_BYTES));
		}

		assertThatThrownBy(() -> client.editMessageWithAttachments(
				wm.baseUrl() + webhookPath,
				"msg-edit-5",
				new WebhookPayload("eleven", List.of()),
				attachments))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("10 attachments");
	}
}
