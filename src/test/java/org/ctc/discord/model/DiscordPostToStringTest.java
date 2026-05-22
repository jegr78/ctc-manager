package org.ctc.discord.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscordPostToStringTest {

	private static final String SECRET = "secret-token-xyz-12345";

	@Test
	void givenWebhookTokenPopulated_whenToString_thenDoesNotEchoToken() {
		// given
		DiscordPost post = new DiscordPost();
		post.setChannelId("100");
		post.setMessageId("200");
		post.setWebhookId("300");
		post.setWebhookToken(SECRET);
		post.setPostType(DiscordPostType.TEAM_CARDS);

		// when
		String rendered = post.toString();

		// then
		assertThat(rendered).doesNotContain(SECRET);
	}
}
