package org.ctc.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamCardServiceColorRobustnessTest {

	@Mock
	private TemplateEngine templateEngine;

	@Mock
	private StandingsService standingsService;

	@Mock
	private SeasonPhaseService seasonPhaseService;

	@InjectMocks
	private TeamCardService teamCardService;

	private TeamCardService service() {
		return new TeamCardService(templateEngine, standingsService, seasonPhaseService, "uploads");
	}

	@Test
	void givenCssKeywordPrimary_whenContrastColor_thenReturnsFallbackWithoutThrowing() {
		// given
		TeamCardService service = service();

		// when / then
		assertThatCode(() -> service.contrastColor("transparent")).doesNotThrowAnyException();
		assertThat(service.contrastColor("transparent")).isIn("#0b0b10", "#ffffff");
	}

	@Test
	void givenRgbFunctionPrimary_whenContrastColor_thenReturnsFallbackWithoutThrowing() {
		// given
		TeamCardService service = service();

		// when / then
		assertThatCode(() -> service.contrastColor("rgb(0,0,0)")).doesNotThrowAnyException();
		assertThat(service.contrastColor("rgb(0,0,0)")).isIn("#0b0b10", "#ffffff");
	}

	@Test
	void givenInvalidHexDigitsPrimary_whenContrastColor_thenReturnsFallbackWithoutThrowing() {
		// given
		TeamCardService service = service();

		// when / then
		assertThatCode(() -> service.contrastColor("#GGGGGG")).doesNotThrowAnyException();
		assertThat(service.contrastColor("#GGGGGG")).isIn("#0b0b10", "#ffffff");
	}

	@Test
	void givenShorthandHexPrimary_whenContrastColor_thenReturnsFallbackWithoutThrowing() {
		// given
		TeamCardService service = service();

		// when / then
		assertThatCode(() -> service.contrastColor("#abc")).doesNotThrowAnyException();
		assertThat(service.contrastColor("#abc")).isIn("#0b0b10", "#ffffff");
	}

	@Test
	void givenNullPrimary_whenContrastColor_thenReturnsFallbackWithoutThrowing() {
		// given
		TeamCardService service = service();

		// when / then
		assertThatCode(() -> service.contrastColor(null)).doesNotThrowAnyException();
		assertThat(service.contrastColor(null)).isIn("#0b0b10", "#ffffff");
	}

	@Test
	void givenEmptyPrimary_whenContrastColor_thenReturnsFallbackWithoutThrowing() {
		// given
		TeamCardService service = service();

		// when / then
		assertThatCode(() -> service.contrastColor("")).doesNotThrowAnyException();
		assertThat(service.contrastColor("")).isIn("#0b0b10", "#ffffff");
	}

	@Test
	void givenInvalidHexAccent_whenComputeAccentVisColor_thenFallsBackToPrimaryWithoutThrowing() {
		// given
		TeamCardService service = service();

		// when / then
		assertThatCode(() -> service.computeAccentVisColor("#zzzzzz", "#336699")).doesNotThrowAnyException();
		assertThat(service.computeAccentVisColor("#zzzzzz", "#336699")).isEqualTo("#336699");
	}

	@Test
	void givenCssKeywordAccent_whenComputeAccentVisColor_thenFallsBackToPrimaryWithoutThrowing() {
		// given
		TeamCardService service = service();

		// when / then
		assertThatCode(() -> service.computeAccentVisColor("transparent", "#336699")).doesNotThrowAnyException();
		assertThat(service.computeAccentVisColor("transparent", "#336699")).isEqualTo("#336699");
	}

	@Test
	void givenInvalidHexInputs_whenComputeGradientColor_thenReturnsAColorWithoutThrowing() {
		// given
		TeamCardService service = service();

		// when / then
		assertThatCode(() -> service.computeGradientColor("transparent", "rgb(0,0,0)", "#zzzzzz"))
				.doesNotThrowAnyException();
		assertThat(service.computeGradientColor("transparent", "rgb(0,0,0)", "#zzzzzz")).isNotNull();
	}

	@Test
	void givenValidHexPrimary_whenContrastColor_thenBehaviourUnchanged() {
		// given
		TeamCardService service = service();

		// when / then
		assertThat(service.contrastColor("#ffffff")).isEqualTo("#0b0b10");
		assertThat(service.contrastColor("#000000")).isEqualTo("#ffffff");
	}
}
