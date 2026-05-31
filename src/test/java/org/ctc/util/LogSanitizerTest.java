package org.ctc.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LogSanitizerTest {

	@Test
	void givenNull_whenSanitize_thenReturnsLiteralNull() {
		assertThat(LogSanitizer.sanitize(null)).isEqualTo("null");
	}

	@ParameterizedTest
	@ValueSource(strings = {"Hello World", "PSN_ID-42"})
	void givenPlainText_whenSanitize_thenPassesThrough(String input) {
		assertThat(LogSanitizer.sanitize(input)).isEqualTo(input);
	}

	@Test
	void givenUnicodeText_whenSanitize_thenPassesThrough() {
		assertThat(LogSanitizer.sanitize("Ümlauts Ñ 日本語")).isEqualTo("Ümlauts Ñ 日本語");
	}

	@ParameterizedTest
	@ValueSource(strings = {"\n", "\r", "\t"})
	void givenNewlineOrTab_whenSanitize_thenReplacedWithUnderscore(String input) {
		assertThat(LogSanitizer.sanitize(input)).isEqualTo("_");
	}

	@Test
	void givenEmbeddedControlChars_whenSanitize_thenEachReplacedWithUnderscore() {
		assertThat(LogSanitizer.sanitize("abc\ndef\rghi")).isEqualTo("abc_def_ghi");
	}

	@Test
	void givenCrlfPayload_whenSanitize_thenCrlfBecomesSingleUnderscore() {
		assertThat(LogSanitizer.sanitize("user\r\nINFO")).isEqualTo("user_INFO");
	}

	@ParameterizedTest
	@ValueSource(ints = {0x00, 0x07, 0x1B, 0x7F})
	void givenC0OrDelControlChar_whenSanitize_thenReplacedWithUnderscore(int codePoint) {
		assertThat(LogSanitizer.sanitize(String.valueOf((char) codePoint))).isEqualTo("_");
	}

	@Test
	void givenNonStringObject_whenSanitize_thenToStringUsed() {
		UUID uuid = UUID.randomUUID();
		assertThat(LogSanitizer.sanitize(42)).isEqualTo("42");
		assertThat(LogSanitizer.sanitize(uuid)).isEqualTo(uuid.toString());
	}
}
