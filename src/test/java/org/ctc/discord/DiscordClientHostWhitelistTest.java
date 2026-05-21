package org.ctc.discord;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.ctc.discord.dto.WebhookPayload;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
		DiscordWebhookClient client = new DiscordWebhookClient(
				Mockito.mock(DiscordRateLimitInterceptor.class),
				new ObjectMapper(),
				new DiscordHostValidator("discord.com,localhost"));

		assertThatThrownBy(() -> client.execute(
				"http://localhost:9999/api/webhooks/100/abc",
				new WebhookPayload("hi", List.of())))
				.isNotInstanceOf(IllegalArgumentException.class);
	}
}
