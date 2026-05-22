package org.ctc.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MatchToStringTest {

	private static final String SECRET = "secret-token-xyz-12345";

	@Test
	void givenWebhookUrlPopulated_whenToString_thenDoesNotEchoTokenFragment() {
		// given
		Match match = new Match();
		match.setDiscordChannelId("123456789012345678");
		match.setDiscordChannelWebhookUrl("https://discord.com/api/webhooks/100/" + SECRET);

		// when
		String rendered = match.toString();

		// then
		assertThat(rendered).doesNotContain(SECRET);
	}
}
