package org.ctc.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import org.ctc.discord.dto.WebhookMessage;
import org.ctc.discord.dto.WebhookPayload;
import org.ctc.discord.exception.DiscordAuthException;
import org.ctc.discord.exception.DiscordNotFoundException;
import org.ctc.discord.exception.DiscordTransientException;
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
class DiscordWebhookClientIT {

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
	void given200_whenExecute_thenReturnsWebhookMessage() throws Exception {
		String webhookPath = "/api/v10/webhooks/100/abc";
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-1\",\"channel_id\":\"chan-1\"}")));

		var payload = new WebhookPayload("Game On!", List.of());
		WebhookMessage out = client.execute(wm.baseUrl() + webhookPath, payload);

		assertThat(out.id()).isEqualTo("msg-1");
		assertThat(out.channelId()).isEqualTo("chan-1");
	}

	@Test
	void given401_whenExecute_thenThrowsDiscordAuthException() {
		String webhookPath = "/api/v10/webhooks/100/abc";
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(401)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"message\":\"Unauthorized\",\"code\":0}")));

		assertThatThrownBy(() -> client.execute(wm.baseUrl() + webhookPath, new WebhookPayload("hi", List.of())))
				.isInstanceOf(DiscordAuthException.class);
	}

	@Test
	void given404_whenExecute_thenThrowsDiscordNotFoundException() {
		String webhookPath = "/api/v10/webhooks/100/abc";
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(404)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"message\":\"Unknown Webhook\",\"code\":10015}")));

		assertThatThrownBy(() -> client.execute(wm.baseUrl() + webhookPath, new WebhookPayload("hi", List.of())))
				.isInstanceOf(DiscordNotFoundException.class);
	}

	@Test
	void given5xxExhausted_whenExecute_thenThrowsDiscordTransientException() {
		String webhookPath = "/api/v10/webhooks/100/abc";
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(aResponse().withStatus(503)));

		assertThatThrownBy(() -> client.execute(wm.baseUrl() + webhookPath, new WebhookPayload("hi", List.of())))
				.isInstanceOf(DiscordTransientException.class);
	}

	@Test
	void whenExecute_thenSendsWaitTrueQueryParam() throws Exception {
		String webhookPath = "/api/v10/webhooks/100/abc";
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withQueryParam("wait", equalTo("true"))
				.willReturn(okJson("{\"id\":\"msg-1\",\"channel_id\":\"chan-1\"}")));

		WebhookMessage out = client.execute(
				wm.baseUrl() + webhookPath, new WebhookPayload("Game On!", List.of()));

		assertThat(out).as("Discord returns 204 without ?wait=true — body must be non-null").isNotNull();
		assertThat(out.id()).isEqualTo("msg-1");
		wm.verify(postRequestedFor(urlPathEqualTo(webhookPath))
				.withQueryParam("wait", equalTo("true")));
	}

	@Test
	void given200_whenEditMessage_thenReturnsUpdatedMessage() throws Exception {
		String webhookPath = "/api/v10/webhooks/100/abc";
		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-1"))
				.willReturn(okJson("{\"id\":\"msg-1\",\"channel_id\":\"chan-1\"}")));

		WebhookMessage out = client.editMessage(
				wm.baseUrl() + webhookPath, "msg-1", new WebhookPayload("edited", List.of()));

		assertThat(out.id()).isEqualTo("msg-1");
	}
}
