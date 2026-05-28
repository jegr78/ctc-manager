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

class SeasonFormTest {

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
	void givenValidName_whenValidate_thenNoViolations() {
		SeasonForm form = newForm("Season 2026");

		Set<ConstraintViolation<SeasonForm>> violations = validator.validate(form);

		assertThat(violations).isEmpty();
	}

	@Test
	void givenBlankName_whenValidate_thenNotBlankViolation() {
		SeasonForm form = newForm("");

		Set<ConstraintViolation<SeasonForm>> violations = validator.validate(form);

		assertThat(violations)
				.anyMatch(v -> v.getPropertyPath().toString().equals("name"));
	}

	private static SeasonForm newForm(String name) {
		SeasonForm form = new SeasonForm();
		form.setName(name);
		form.setYear(2026);
		form.setNumber(1);
		return form;
	}
}
