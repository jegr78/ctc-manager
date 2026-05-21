package org.ctc.discord.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DiscordSnowflakeTest {

	@Test
	void givenPatternConstant_whenComparedToInlineRegex_thenIdentical() {
		assertThat(DiscordSnowflake.PATTERN).isEqualTo("^$|^\\d{17,20}$");
	}

	@Test
	void givenMessageConstant_whenComparedToExpected_thenIdentical() {
		assertThat(DiscordSnowflake.MESSAGE).isEqualTo("Must be a Discord snowflake (17-20 digits) or empty");
	}
}
