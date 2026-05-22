package org.ctc.discord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DiscordPostServiceWebhookUrlPatternTest {

	@Test
	void givenRealDiscordWebhookUrl_whenParse_thenExtractsIdAndToken() {
		DiscordPostService.WebhookCredentials creds = DiscordPostService.parseWebhookUrl(
				"https://discord.com/api/webhooks/1234567890/abcDEF_123-XYZ--token");

		assertThat(creds.id()).isEqualTo("1234567890");
		assertThat(creds.token()).isEqualTo("abcDEF_123-XYZ--token");
	}

	@Test
	void givenVersionedApiWebhookUrl_whenParse_thenExtractsIdAndToken() {
		DiscordPostService.WebhookCredentials creds = DiscordPostService.parseWebhookUrl(
				"https://discord.com/api/v10/webhooks/9876/some-token");

		assertThat(creds.id()).isEqualTo("9876");
		assertThat(creds.token()).isEqualTo("some-token");
	}

	@Test
	void givenWireMockStyleUrlWithoutApiSegment_whenParse_thenExtractsIdAndToken() {
		DiscordPostService.WebhookCredentials creds = DiscordPostService.parseWebhookUrl(
				"http://localhost:12345/webhooks/42/mock-token");

		assertThat(creds.id()).isEqualTo("42");
		assertThat(creds.token()).isEqualTo("mock-token");
	}

	@Test
	void givenDiscordappLegacyHost_whenParse_thenExtractsIdAndToken() {
		DiscordPostService.WebhookCredentials creds = DiscordPostService.parseWebhookUrl(
				"https://discordapp.com/api/webhooks/555/legacy-token");

		assertThat(creds.id()).isEqualTo("555");
		assertThat(creds.token()).isEqualTo("legacy-token");
	}

	@Test
	void givenUrlWithTrailingQueryString_whenParse_thenIgnoresQuery() {
		DiscordPostService.WebhookCredentials creds = DiscordPostService.parseWebhookUrl(
				"https://discord.com/api/webhooks/123/token?wait=true");

		assertThat(creds.id()).isEqualTo("123");
		assertThat(creds.token()).isEqualTo("token");
	}

	@Test
	void givenGarbageUrl_whenParse_thenThrows() {
		assertThatThrownBy(() -> DiscordPostService.parseWebhookUrl(
				"https://example.com/not-a-webhook"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("does not match expected shape");
	}

	@Test
	void givenNonNumericId_whenParse_thenThrows() {
		assertThatThrownBy(() -> DiscordPostService.parseWebhookUrl(
				"https://discord.com/api/webhooks/abc/token"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
