package org.ctc.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
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
class DiscordWebhookClientMultipartIT {

	// Minimal PNG: 8-byte PNG signature only (valid for Content-Type assertion)
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
	void givenAttachments_whenExecuteMultipart_thenAssertsPerPartHeadersAndPayload() throws Exception {
		String webhookPath = "/api/v10/webhooks/100/abc";
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withHeader("Content-Type", containing("multipart/form-data"))
				.withMultipartRequestBody(aMultipart("payload_json")
						.withHeader("Content-Type", containing("application/json"))
						.withBody(matchingJsonPath("$.content", containing("Game On!"))))
				.withMultipartRequestBody(aMultipart("files[0]")
						.withHeader("Content-Type", equalTo("image/png")))
				.withMultipartRequestBody(aMultipart("files[1]")
						.withHeader("Content-Type", equalTo("image/png")))
				.willReturn(okJson("{\"id\":\"msg-2\",\"channel_id\":\"chan-1\"}")));

		var payload = new WebhookPayload("Game On!", List.of());
		var attachments = List.of(
				new NamedAttachment("a.png", PNG_BYTES),
				new NamedAttachment("b.png", PNG_BYTES));

		WebhookMessage out = client.executeMultipart(wm.baseUrl() + webhookPath, payload, attachments);

		assertThat(out.id()).isEqualTo("msg-2");
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath)));
	}

	@Test
	void givenEmptyAttachments_whenExecuteMultipart_thenDelegatesToJsonExecute() throws Exception {
		String webhookPath = "/api/v10/webhooks/100/abc";
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withHeader("Content-Type", containing("application/json"))
				.willReturn(okJson("{\"id\":\"msg-3\",\"channel_id\":\"chan-1\"}")));

		var payload = new WebhookPayload("no files", List.of());
		WebhookMessage out = client.executeMultipart(wm.baseUrl() + webhookPath, payload, List.of());

		assertThat(out.id()).isEqualTo("msg-3");
	}

	@Test
	void givenTooManyAttachments_whenExecuteMultipart_thenThrowsIllegalArgumentException() {
		String webhookPath = "/api/v10/webhooks/100/abc";
		List<NamedAttachment> attachments = new ArrayList<>();
		for (int i = 0; i < 11; i++) {
			attachments.add(new NamedAttachment("a" + i + ".png", PNG_BYTES));
		}

		assertThatThrownBy(() -> client.executeMultipart(
				wm.baseUrl() + webhookPath, new WebhookPayload("x", List.of()), attachments))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("10 attachments");
	}
}
