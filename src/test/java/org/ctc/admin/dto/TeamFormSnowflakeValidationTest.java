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

class TeamFormSnowflakeValidationTest {

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

	@Test
	void givenEmptyDiscordRoleId_whenValidate_thenNoViolation() {
		// given
		TeamForm form = new TeamForm();
		form.setName("Team A");
		form.setShortName("TA");

		// when
		Set<ConstraintViolation<TeamForm>> violations = validator.validate(form);

		// then — empty/null discordRoleId is legal (NULLABLE column + DiscordSnowflake.PATTERN allows ^$)
		assertThat(violations).isEmpty();
	}

	@Test
	void givenValidSnowflake_whenValidate_thenNoViolation() {
		// given — 18-digit snowflake matches ^\d{17,20}$
		TeamForm form = new TeamForm();
		form.setName("Team A");
		form.setShortName("TA");
		form.setDiscordRoleId("123456789012345678");

		// when
		Set<ConstraintViolation<TeamForm>> violations = validator.validate(form);

		// then
		assertThat(violations).isEmpty();
	}

	@Test
	void givenInvalidDiscordRoleId_whenValidate_thenPatternViolation() {
		// given
		TeamForm form = new TeamForm();
		form.setName("Team A");
		form.setShortName("TA");
		form.setDiscordRoleId("abc");

		// when
		Set<ConstraintViolation<TeamForm>> violations = validator.validate(form);

		// then
		assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("discordRoleId"));
	}

	@Test
	void givenName_shortName_required_whenValidate_thenViolation() {
		// given — name + shortName remain @NotBlank under the new field set
		TeamForm form = new TeamForm();

		// when
		Set<ConstraintViolation<TeamForm>> violations = validator.validate(form);

		// then
		assertThat(violations)
				.anyMatch(v -> v.getPropertyPath().toString().equals("name"))
				.anyMatch(v -> v.getPropertyPath().toString().equals("shortName"));
	}
}
