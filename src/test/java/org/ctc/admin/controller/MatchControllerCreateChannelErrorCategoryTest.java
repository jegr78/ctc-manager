package org.ctc.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.ctc.discord.exception.DiscordApiException.Category;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MatchControllerCreateChannelErrorCategoryTest {

	@ParameterizedTest
	@CsvSource({
			"TRANSIENT,transient",
			"AUTH,auth",
			"NOT_FOUND,not-found",
			"CATEGORY_FULL,category-full"
	})
	void givenCategoryEnum_whenLowercaseAndHyphenated_thenMatchesBemClassSuffix(
			Category category, String expected) {
		String actual = category.name().toLowerCase().replace('_', '-');
		assertThat(actual).isEqualTo(expected);
	}
}
