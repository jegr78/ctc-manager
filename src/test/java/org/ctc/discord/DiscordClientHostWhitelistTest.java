package org.ctc.discord;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.ctc.discord.dto.WebhookPayload;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for the SSRF positive-whitelist guard added by Plan 93-02 Task 5.
 *
 * Behaviour contract:
 * - Whitelist is parsed from a CSV string (`app.discord.allowed-hosts`).
 * - Hostname comparison is case-insensitive — `DISCORD.COM` matches `discord.com`.
 * - Hosts not in the whitelist throw {@link IllegalArgumentException} with message
 *   `"Discord host blocked: " + host`.
 * - Malformed URLs (null host) throw the same exception with `<null>` suffix.
 * - Both {@link DiscordHostValidator} (the shared helper) and
 *   {@link DiscordWebhookClient} apply the guard at the call site.
 *
 * RED: {@code DiscordHostValidator} doesn't exist yet and neither client invokes
 * the check, so this test fails to compile. Task 5 lands the validator and wires
 * it into {@link DiscordConfig#discordBotRestClient} and
 * {@link DiscordWebhookClient#forWebhookUrl}.
 */
class DiscordClientHostWhitelistTest {

	@Test
	void givenWhitelistDiscordOnly_whenValidateDiscordUrl_thenSucceeds() {
		// given
		DiscordHostValidator validator = new DiscordHostValidator("discord.com");

		// when / then
		assertThatCode(() -> validator.requireAllowed("https://discord.com/api/v10")).doesNotThrowAnyException();
	}

	@Test
	void givenWhitelistDiscordOnly_whenValidateEvilHost_thenThrowsHostBlocked() {
		// given
		DiscordHostValidator validator = new DiscordHostValidator("discord.com");

		// when / then
		assertThatThrownBy(() -> validator.requireAllowed("https://evil.com/api/v10"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Discord host blocked: evil.com");
	}

	@Test
	void givenMultiHostWhitelist_whenValidateLocalhost_thenSucceeds() {
		// given — test override extends the default whitelist
		DiscordHostValidator validator = new DiscordHostValidator("discord.com,localhost,127.0.0.1");

		// when / then
		assertThatCode(() -> validator.requireAllowed("http://localhost:8080/api/v10")).doesNotThrowAnyException();
		assertThatCode(() -> validator.requireAllowed("http://127.0.0.1:9090/api/v10")).doesNotThrowAnyException();
	}

	@Test
	void givenWhitelistDiscordOnly_whenValidateCaseInsensitiveHost_thenSucceeds() {
		// given
		DiscordHostValidator validator = new DiscordHostValidator("discord.com");

		// when / then — DISCORD.COM should match discord.com (case-insensitive)
		assertThatCode(() -> validator.requireAllowed("https://DISCORD.COM/api/v10")).doesNotThrowAnyException();
	}

	@Test
	void givenMalformedUrl_whenValidate_thenThrowsHostBlockedNull() {
		// given
		DiscordHostValidator validator = new DiscordHostValidator("discord.com");

		// when / then — URI.create("not a url") parses but getHost() returns null
		assertThatThrownBy(() -> validator.requireAllowed("not-a-valid-url"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Discord host blocked: <null>");
	}

	@Test
	void givenWhitelistedDiscord_whenWebhookClientExecuteEvilHost_thenThrowsHostBlocked() {
		// given — webhook client guards each public method against non-whitelisted hosts
		DiscordWebhookClient client = new DiscordWebhookClient(
				Mockito.mock(DiscordRateLimitInterceptor.class),
				new ObjectMapper(),
				new DiscordHostValidator("discord.com"));

		// when / then
		assertThatThrownBy(() -> client.execute(
				"https://evil.com/api/webhooks/100/abc",
				new WebhookPayload("hi", List.of())))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Discord host blocked: evil.com");
	}

	@Test
	void givenWhitelistedLocalhost_whenWebhookClientExecuteLocalhost_thenGuardPasses() {
		// given — webhook client construction with localhost-augmented whitelist accepts loopback;
		// the call still throws once the HTTP layer (mocked interceptor) returns no response,
		// but it does so AFTER the host guard — not because of it.
		DiscordWebhookClient client = new DiscordWebhookClient(
				Mockito.mock(DiscordRateLimitInterceptor.class),
				new ObjectMapper(),
				new DiscordHostValidator("discord.com,localhost"));

		// when / then
		assertThatThrownBy(() -> client.execute(
				"http://localhost:9999/api/webhooks/100/abc",
				new WebhookPayload("hi", List.of())))
				.isNotInstanceOf(IllegalArgumentException.class);
	}
}
