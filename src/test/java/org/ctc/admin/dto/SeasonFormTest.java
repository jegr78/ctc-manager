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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"http://example.org/rules", "javascript:alert(1)", "//example.org/rules", "https://user:pass@example.org/rules", "https:///missing-host", "https://example.org/\\rules", "https://example.org/%0aevil"})
    void unsafeDocumentLinksHaveFieldErrors(String url) {
        var form = newForm("Test Rules");
        form.setRulebookUrl(url);
        form.setRacingEtiquetteUrl(url);
        assertThat(validator.validate(form)).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("rulebookUrl", "racingEtiquetteUrl");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.ValueSource(strings = {"  ", "https://docs.google.com/document/d/e/Test-Rules/pub?embedded=true#rules", "https://example.org/rules.pdf"})
    void optionalOrPublishedHttpsDocumentsAreAccepted(String url) {
        var form = newForm("Test Rules");
        form.setRulebookUrl(url);
        form.setRacingEtiquetteUrl(url);
        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void documentUrlsHaveBoundedLength() {
        var form = newForm("Test Rules");
        form.setRulebookUrl("https://example.org/" + "a".repeat(2048));
        assertThat(validator.validate(form)).extracting(v -> v.getPropertyPath().toString()).contains("rulebookUrl");
    }

	private static SeasonForm newForm(String name) {
		SeasonForm form = new SeasonForm();
		form.setName(name);
		form.setYear(2026);
		form.setNumber(1);
		return form;
	}
}
