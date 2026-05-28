package org.ctc.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
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
class DiscordWebhookClientThreadIdIT {

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
	void givenThreadId_whenExecute_thenUrlContainsThreadIdQueryParam() throws Exception {
		String webhookPath = "/api/v10/webhooks/100/abc";
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withQueryParam("wait", equalTo("true"))
				.withQueryParam("thread_id", equalTo("THREAD123"))
				.willReturn(okJson("{\"id\":\"msg-1\",\"channel_id\":\"chan-1\"}")));

		WebhookMessage out = client.execute(
				wm.baseUrl() + webhookPath, new WebhookPayload("Forum!", List.of()), "THREAD123");

		assertThat(out.id()).isEqualTo("msg-1");
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
				.withQueryParam("wait", equalTo("true"))
				.withQueryParam("thread_id", equalTo("THREAD123")));
	}

	@Test
	void givenNullThreadId_whenExecute_thenUrlOmitsThreadIdQueryParam() throws Exception {
		String webhookPath = "/api/v10/webhooks/200/abc";
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-2\",\"channel_id\":\"chan-2\"}")));

		client.execute(wm.baseUrl() + webhookPath, new WebhookPayload("hi", List.of()), null);

		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
				.withQueryParam("wait", equalTo("true"))
				.withQueryParam("thread_id", absent()));
	}

	@Test
	void givenThreadId_whenExecuteMultipart_thenMultipartUrlContainsThreadId() throws Exception {
		String webhookPath = "/api/v10/webhooks/300/abc";
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withQueryParam("thread_id", equalTo("THREAD123"))
				.willReturn(okJson("{\"id\":\"msg-3\",\"channel_id\":\"chan-3\"}")));

		List<NamedAttachment> atts = List.of(new NamedAttachment("race-result.png", PNG_BYTES));
		WebhookMessage out = client.executeMultipart(
				wm.baseUrl() + webhookPath, new WebhookPayload("Forum!", List.of()), atts, "THREAD123");

		assertThat(out.id()).isEqualTo("msg-3");
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
				.withQueryParam("thread_id", equalTo("THREAD123"))
				.withRequestBodyPart(aMultipart("payload_json").build())
				.withRequestBodyPart(aMultipart("files[0]").build()));
	}

	@Test
	void givenThreadId_whenEditMessage_thenPatchUrlContainsThreadId() throws Exception {
		String webhookPath = "/api/v10/webhooks/400/abc";
		String messageId = "msg-edit-1";
		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/" + messageId))
				.withQueryParam("thread_id", equalTo("THREAD123"))
				.willReturn(okJson("{\"id\":\"" + messageId + "\",\"channel_id\":\"chan-4\"}")));

		client.editMessage(
				wm.baseUrl() + webhookPath, messageId, new WebhookPayload("edited", List.of()), "THREAD123");

		wm.verify(patchRequestedFor(urlPathEqualTo(webhookPath + "/messages/" + messageId))
				.withQueryParam("thread_id", equalTo("THREAD123")));
	}

	@Test
	void givenThreadId_whenEditMessageWithAttachments_thenPatchMultipartContainsThreadIdAndAttachmentsDescriptor()
			throws Exception {
		String webhookPath = "/api/v10/webhooks/500/abc";
		String messageId = "msg-edit-2";
		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/" + messageId))
				.withQueryParam("thread_id", equalTo("THREAD123"))
				.willReturn(okJson("{\"id\":\"" + messageId + "\",\"channel_id\":\"chan-5\"}")));

		List<NamedAttachment> atts = List.of(new NamedAttachment("race-result.png", PNG_BYTES));
		client.editMessageWithAttachments(
				wm.baseUrl() + webhookPath, messageId,
				new WebhookPayload("edited", List.of()), atts, "THREAD123");

		wm.verify(patchRequestedFor(urlPathEqualTo(webhookPath + "/messages/" + messageId))
				.withQueryParam("thread_id", equalTo("THREAD123"))
				.withRequestBodyPart(aMultipart("payload_json")
						.withBody(matchingJsonPath("$.attachments[0].id", equalTo("0")))
						.withBody(matchingJsonPath("$.attachments[0].filename", equalTo("race-result.png")))
						.build())
				.withRequestBodyPart(aMultipart("files[0]").build()));
	}

	@Test
	void givenDisallowedHost_when3ArgOverloadInvoked_thenThrowsBeforeAnyHttpCall() {
		String disallowed = "https://evil.com/webhooks/100/abc";

		org.assertj.core.api.Assertions
				.assertThatThrownBy(() ->
						client.execute(disallowed, new WebhookPayload("hi", List.of()), "THREAD123"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Discord host blocked");

		wm.verify(0, postRequestedFor(urlPathEqualTo("/")).withRequestBody(containing("hi")));
	}
}
