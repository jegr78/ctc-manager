package org.ctc.discord.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscordPostServiceEscapeMarkdownLinkUrlTest {

	@Test
	void givenUrlWithClosingParen_whenEscape_thenParenEscaped() {
		assertThat(DiscordPostService.escapeMarkdownLinkUrl("https://example.com/foo)"))
				.isEqualTo("https://example.com/foo\\)");
	}

	@Test
	void givenUrlWithAngleBrackets_whenEscape_thenBothBracketsEscaped() {
		assertThat(DiscordPostService.escapeMarkdownLinkUrl("https://example.com/<a>b"))
				.isEqualTo("https://example.com/\\<a\\>b");
	}

	@Test
	void givenPlainUrl_whenEscape_thenUnchanged() {
		assertThat(DiscordPostService.escapeMarkdownLinkUrl("https://twitch.tv/foo"))
				.isEqualTo("https://twitch.tv/foo");
	}
}
