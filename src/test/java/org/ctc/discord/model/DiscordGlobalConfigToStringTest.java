package org.ctc.discord.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifies T-93-01 mitigation surface (d): the entity's @ToString(exclude=...) keeps the
 * announcement_webhook_url out of logged toString() representations.
 */
class DiscordGlobalConfigToStringTest {

	private static final String SECRET = "secret-token-xyz-12345";

	@Test
	void givenWebhookUrlPopulated_whenToString_thenDoesNotEchoTokenFragment() {
		// given
		DiscordGlobalConfig config = new DiscordGlobalConfig();
		config.setGuildId("123456789012345678");
		config.setAnnouncementWebhookUrl("https://discord.com/api/webhooks/100/" + SECRET);

		// when
		String rendered = config.toString();

		// then
		assertThat(rendered).doesNotContain(SECRET);
	}

	@Test
	void givenEntity_whenToString_thenContainsGuildIdForOperatorVisibility() {
		// given — operators need a non-secret field in the toString output
		DiscordGlobalConfig config = new DiscordGlobalConfig();
		config.setGuildId("123456789012345678");

		// when
		String rendered = config.toString();

		// then
		assertThat(rendered).contains("123456789012345678");
	}
}
