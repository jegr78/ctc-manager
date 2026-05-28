package org.ctc.domain.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HexColorTest {

	@Test
	void givenNull_whenSanitize_thenReturnsNull() {
		assertThat(HexColor.sanitize(null)).isNull();
	}

	@Test
	void givenBlank_whenSanitize_thenReturnsNull() {
		assertThat(HexColor.sanitize("")).isNull();
		assertThat(HexColor.sanitize("   ")).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = {"#fff", "#FFF", "#ffffff", "#FFFFFF", "#ff00aa80", "#1A2B3C4D"})
	void givenValidHex_whenSanitize_thenReturnsTrimmedValue(String input) {
		assertThat(HexColor.sanitize(input)).isEqualTo(input);
	}

	@Test
	void givenUntrimmedValidHex_whenSanitize_thenReturnsTrimmedValue() {
		assertThat(HexColor.sanitize("  #abcdef  ")).isEqualTo("#abcdef");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"fff",
			"#xyz",
			"#ff",
			"#fffffffff",
			"#fff;color:red",
			"red; } body{display:none} .x {",
			"javascript:alert(1)"
	})
	void givenInvalidOrInjectionPayload_whenSanitize_thenReturnsNull(String input) {
		assertThat(HexColor.sanitize(input)).isNull();
	}
}
