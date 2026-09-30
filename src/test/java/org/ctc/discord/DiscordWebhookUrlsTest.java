package org.ctc.discord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordWebhookUrlsTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
			"https://discord.com/api/webhooks/123/SyntheticToken_abc-123 | https://discord.com/api/webhooks/123/***",
			"https://discord.com/api/v10/webhooks/123/SyntheticToken_abc-123 | https://discord.com/api/v10/webhooks/123/***",
			"https://discordapp.com/webhooks/123/SyntheticToken_abc-123?wait=true | https://discordapp.com/webhooks/123/***?wait=true",
			"not a webhook url | not a webhook url"})
	void givenWebhookUrl_whenRedacted_thenOnlyTheTokenIsHidden(String url, String expected) {
		// when / then
		assertThat(DiscordWebhookUrls.redact(url)).isEqualTo(expected);
	}

	@Test
	void givenNoUrl_whenRedacted_thenNull() {
		// when / then
		assertThat(DiscordWebhookUrls.redact(null)).isNull();
	}
}
