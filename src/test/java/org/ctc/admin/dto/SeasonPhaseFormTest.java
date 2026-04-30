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
        form.setSeasonId(UUID.randomUUID());
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
        form.setSeasonId(UUID.randomUUID());
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
        form.setSeasonId(UUID.randomUUID());
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
        // given
        var form = new SeasonPhaseForm();
        form.setPhaseType(PhaseType.REGULAR);
        form.setLayout(PhaseLayout.LEAGUE);
        form.setFormat(SeasonFormat.LEAGUE);
        // seasonId is null — intentionally not set

        // when
        var violations = validator.validate(form);

        // then
        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("seasonId");
    }

    @Test
    void givenAllRequiredFields_whenValidate_thenNoViolation() {
        // given
        var form = new SeasonPhaseForm();
        form.setSeasonId(UUID.randomUUID());
        form.setPhaseType(PhaseType.REGULAR);
        form.setLayout(PhaseLayout.LEAGUE);
        form.setFormat(SeasonFormat.LEAGUE);

        // when
        var violations = validator.validate(form);

        // then
        assertThat(violations).isEmpty();
    }
}
