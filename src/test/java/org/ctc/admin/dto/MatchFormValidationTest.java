package org.ctc.admin.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MatchFormValidationTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void setUp() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void tearDown() {
		factory.close();
	}

	private static String repeat(char c, int n) {
		return String.valueOf(c).repeat(n);
	}

	@Test
	void givenBlankForm_whenValidate_thenNoViolations() {
		// given
		MatchForm form = new MatchForm();

		// when
		Set<ConstraintViolation<MatchForm>> violations = validator.validate(form);

		// then
		assertThat(violations).isEmpty();
	}

	@Test
	void given2001CharTeaser_whenValidate_thenOneViolationOnDiscordTeaser() {
		// given
		MatchForm form = new MatchForm();
		form.setDiscordTeaser(repeat('x', 2001));

		// when
		Set<ConstraintViolation<MatchForm>> violations = validator.validate(form);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("discordTeaser"));
	}

	@Test
	void given501CharStreamLink_whenValidate_thenOneViolationOnStreamLink() {
		// given
		MatchForm form = new MatchForm();
		form.setStreamLink(repeat('a', 501));

		// when
		Set<ConstraintViolation<MatchForm>> violations = validator.validate(form);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("streamLink"));
	}

	@Test
	void given101CharLobbyHost_whenValidate_thenOneViolationOnLobbyHost() {
		// given
		MatchForm form = new MatchForm();
		form.setLobbyHost(repeat('a', 101));

		// when
		Set<ConstraintViolation<MatchForm>> violations = validator.validate(form);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("lobbyHost"));
	}

	@Test
	void given101CharRaceDirector_whenValidate_thenOneViolationOnRaceDirector() {
		// given
		MatchForm form = new MatchForm();
		form.setRaceDirector(repeat('a', 101));

		// when
		Set<ConstraintViolation<MatchForm>> violations = validator.validate(form);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("raceDirector"));
	}

	@Test
	void given101CharStreamer_whenValidate_thenOneViolationOnStreamer() {
		// given
		MatchForm form = new MatchForm();
		form.setStreamer(repeat('a', 101));

		// when
		Set<ConstraintViolation<MatchForm>> violations = validator.validate(form);

		// then
		assertThat(violations).hasSize(1);
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("streamer"));
	}
}
