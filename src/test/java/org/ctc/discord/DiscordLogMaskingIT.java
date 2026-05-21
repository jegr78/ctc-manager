package org.ctc.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import org.ctc.discord.dto.WebhookPayload;
import org.ctc.discord.exception.DiscordTransientException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Regression fence for T-93-02 (Webhook-URL leak via logs).
 *
 * <p>Provokes a 500-exhausted retry path inside {@link DiscordWebhookClient}; the
 * resulting {@link DiscordTransientException} stacktrace flows through Logback
 * and the captured output must show the {@code %replace} converter swapping the
 * webhook URL for the three-asterisks placeholder pair — the secret token
 * fragment MUST NOT appear in the captured log.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@ExtendWith(OutputCaptureExtension.class)
class DiscordLogMaskingIT {

	private static final String WEBHOOK_URL_TOKEN_FRAGMENT = "secret-token-xyz-12345";

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
	private DiscordWebhookClient webhookClient;

	@Test
	void givenTransientException_whenLogContainsWebhookUrl_thenUrlIsMasked(CapturedOutput out) {
		// given — WireMock returns 500 four times → DiscordRateLimitInterceptor exhausts
		// retries → DiscordTransientException is thrown by DiscordWebhookClient.execute.
		wm.stubFor(post(urlPathMatching("/api/webhooks/.*"))
				.willReturn(aResponse().withStatus(500)));
		String webhookUrl = wm.baseUrl() + "/api/webhooks/999/" + WEBHOOK_URL_TOKEN_FRAGMENT;

		// when
		assertThatThrownBy(() -> webhookClient.execute(webhookUrl, new WebhookPayload("hi", List.of())))
				.isInstanceOf(DiscordTransientException.class);

		// then — Logback %replace mask hides the secret token fragment everywhere
		// in the captured output AND emits the ***/*** sentinel in its place.
		assertThat(out.getAll())
				.doesNotContain(WEBHOOK_URL_TOKEN_FRAGMENT)
				.contains("***/***");
	}
}
