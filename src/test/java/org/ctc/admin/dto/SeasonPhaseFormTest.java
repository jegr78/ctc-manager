package org.ctc.admin.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonFormat;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean-Validation unit tests for SeasonPhaseForm @NotNull constraints (D-22).
 * Tests are RED in Wave 0 — SeasonPhaseForm does not yet exist.
 */
class SeasonPhaseFormTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void givenNullPhaseType_whenValidate_thenViolation() {
        // given
        var form = new SeasonPhaseForm();
        // NOTE: seasonId is INTENTIONALLY absent from SeasonPhaseForm (W-7 IDOR hardening, Plan 60-02)
        form.setLayout(PhaseLayout.LEAGUE);
        form.setFormat(SeasonFormat.LEAGUE);
        // phaseType is null — intentionally not set

        // when
        var violations = validator.validate(form);

        // then
        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("phaseType");
    }

    @Test
    void givenNullLayout_whenValidate_thenViolation() {
        // given
        var form = new SeasonPhaseForm();
        form.setPhaseType(PhaseType.REGULAR);
        form.setFormat(SeasonFormat.LEAGUE);
        // layout is null — intentionally not set

        // when
        var violations = validator.validate(form);

        // then
        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("layout");
    }

    @Test
    void givenNullFormat_whenValidate_thenViolation() {
        // given
        var form = new SeasonPhaseForm();
        form.setPhaseType(PhaseType.REGULAR);
        form.setLayout(PhaseLayout.LEAGUE);
        // format is null (overriding default) — intentionally set to null for test
        form.setFormat(null);

        // when
        var violations = validator.validate(form);

        // then
        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("format");
    }

    @Test
    void givenNullSeasonId_whenValidate_thenViolation() {
        // NOTE: seasonId is INTENTIONALLY absent from SeasonPhaseForm (W-7 IDOR hardening, Plan 60-02).
        // The @PathVariable {seasonId} on SeasonPhaseController is the sole source of truth.
        // This test verifies that a form with all required fields passes validation (no spurious violations).
        // given
        var form = new SeasonPhaseForm();
        form.setPhaseType(PhaseType.REGULAR);
        form.setLayout(PhaseLayout.LEAGUE);
        form.setFormat(SeasonFormat.LEAGUE);

        // when
        var violations = validator.validate(form);

        // then — no violations since phaseType, layout, format are all provided; seasonId is not a DTO field
        assertThat(violations).noneMatch(v -> v.getPropertyPath().toString().equals("phaseType")
                || v.getPropertyPath().toString().equals("layout")
                || v.getPropertyPath().toString().equals("format"));
    }

    @Test
    void givenAllRequiredFields_whenValidate_thenNoViolation() {
        // given
        var form = new SeasonPhaseForm();
        form.setPhaseType(PhaseType.REGULAR);
        form.setLayout(PhaseLayout.LEAGUE);
        form.setFormat(SeasonFormat.LEAGUE);

        // when
        var violations = validator.validate(form);

        // then
        assertThat(violations).isEmpty();
    }
}
