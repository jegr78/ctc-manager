package org.ctc.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.domain.service.StandingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;

class TeamCardServiceColorRobustnessTest {

	private TeamCardService teamCardService;

	@BeforeEach
	void setUp() {
		teamCardService = new TeamCardService(
				mock(TemplateEngine.class),
				mock(StandingsService.class),
				mock(SeasonPhaseService.class),
				"uploads");
	}

	@Test
	void givenCssKeywordPrimary_whenContrastColor_thenReturnsFallbackWithoutThrowing() {
		// when / then
		assertThatCode(() -> teamCardService.contrastColor("transparent")).doesNotThrowAnyException();
		assertThat(teamCardService.contrastColor("transparent")).isIn("#0b0b10", "#ffffff");
	}

	@Test
	void givenRgbFunctionPrimary_whenContrastColor_thenReturnsFallbackWithoutThrowing() {
		// when / then
		assertThatCode(() -> teamCardService.contrastColor("rgb(0,0,0)")).doesNotThrowAnyException();
		assertThat(teamCardService.contrastColor("rgb(0,0,0)")).isIn("#0b0b10", "#ffffff");
	}

	@Test
	void givenInvalidHexDigitsPrimary_whenContrastColor_thenReturnsFallbackWithoutThrowing() {
		// when / then
		assertThatCode(() -> teamCardService.contrastColor("#GGGGGG")).doesNotThrowAnyException();
		assertThat(teamCardService.contrastColor("#GGGGGG")).isIn("#0b0b10", "#ffffff");
	}

	@Test
	void givenShorthandHexPrimary_whenContrastColor_thenReturnsFallbackWithoutThrowing() {
		// when / then
		assertThatCode(() -> teamCardService.contrastColor("#abc")).doesNotThrowAnyException();
		assertThat(teamCardService.contrastColor("#abc")).isIn("#0b0b10", "#ffffff");
	}

	@Test
	void givenNullPrimary_whenContrastColor_thenReturnsFallbackWithoutThrowing() {
		// when / then
		assertThatCode(() -> teamCardService.contrastColor(null)).doesNotThrowAnyException();
		assertThat(teamCardService.contrastColor(null)).isIn("#0b0b10", "#ffffff");
	}

	@Test
	void givenEmptyPrimary_whenContrastColor_thenReturnsFallbackWithoutThrowing() {
		// when / then
		assertThatCode(() -> teamCardService.contrastColor("")).doesNotThrowAnyException();
		assertThat(teamCardService.contrastColor("")).isIn("#0b0b10", "#ffffff");
	}

	@Test
	void givenValidHexAccent_whenComputeAccentVisColor_thenReturnsAccentWithoutThrowing() {
		// when / then
		assertThatCode(() -> teamCardService.computeAccentVisColor("#e0e0e0", "#336699")).doesNotThrowAnyException();
		assertThat(teamCardService.computeAccentVisColor("#e0e0e0", "#336699")).isEqualTo("#e0e0e0");
	}

	@Test
	void givenDarkAccent_whenComputeAccentVisColor_thenFallsBackToPrimary() {
		// when / then
		assertThat(teamCardService.computeAccentVisColor("#000000", "#336699")).isEqualTo("#336699");
	}

	@Test
	void givenInvalidHexAccent_whenComputeAccentVisColor_thenDoesNotThrowAndReturnsNonBlank() {
		// when / then
		assertThatCode(() -> teamCardService.computeAccentVisColor("#zzzzzz", "#336699")).doesNotThrowAnyException();
		assertThat(teamCardService.computeAccentVisColor("#zzzzzz", "#336699")).isNotBlank();
	}

	@Test
	void givenCssKeywordAccent_whenComputeAccentVisColor_thenDoesNotThrow() {
		// when / then
		assertThatCode(() -> teamCardService.computeAccentVisColor("transparent", "#336699")).doesNotThrowAnyException();
	}

	@Test
	void givenInvalidHexInputs_whenComputeGradientColor_thenReturnsAColorWithoutThrowing() {
		// when / then
		assertThatCode(() -> teamCardService.computeGradientColor("transparent", "rgb(0,0,0)", "#zzzzzz"))
				.doesNotThrowAnyException();
		assertThat(teamCardService.computeGradientColor("transparent", "rgb(0,0,0)", "#zzzzzz")).isNotNull();
	}

	@Test
	void givenValidHexPrimary_whenContrastColor_thenBehaviourUnchanged() {
		// when / then
		assertThat(teamCardService.contrastColor("#ffffff")).isEqualTo("#0b0b10");
		assertThat(teamCardService.contrastColor("#000000")).isEqualTo("#ffffff");
	}
}
