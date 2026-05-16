package org.ctc.backup.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackupImportConfirmFormValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setupValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void givenAcknowledgedNull_whenValidate_thenViolatesNotNull() {
        // given
        BackupImportConfirmForm form = new BackupImportConfirmForm();
        form.setStagingId(UUID.randomUUID());
        // acknowledged is null (not set)

        // when
        Set<ConstraintViolation<BackupImportConfirmForm>> violations = validator.validate(form);

        // then
        Set<ConstraintViolation<BackupImportConfirmForm>> acknowledgedViolations = violationsFor("acknowledged", violations);
        assertThat(acknowledgedViolations)
                .as("Jakarta §6.1.1 — @AssertTrue passes on null; only @NotNull fires here")
                .hasSize(1);
        assertThat(acknowledgedViolations.iterator().next().getConstraintDescriptor().getAnnotation().annotationType())
                .isEqualTo(NotNull.class);
    }

    @Test
    void givenAcknowledgedFalse_whenValidate_thenViolatesAssertTrue() {
        // given
        BackupImportConfirmForm form = new BackupImportConfirmForm();
        form.setStagingId(UUID.randomUUID());
        form.setAcknowledged(Boolean.FALSE);

        // when
        Set<ConstraintViolation<BackupImportConfirmForm>> violations = validator.validate(form);

        // then
        Set<ConstraintViolation<BackupImportConfirmForm>> acknowledgedViolations = violationsFor("acknowledged", violations);
        assertThat(acknowledgedViolations).hasSize(1);
        ConstraintViolation<BackupImportConfirmForm> violation = acknowledgedViolations.iterator().next();
        assertThat(violation.getConstraintDescriptor().getAnnotation().annotationType())
                .isEqualTo(AssertTrue.class);
        assertThat(violation.getMessage())
                .as("UI-SPEC §Copywriting Contract locks this string verbatim")
                .isEqualTo("You must acknowledge the deletion warning to continue.");
    }

    @Test
    void givenAcknowledgedTrue_whenValidate_thenNoViolations() {
        // given
        BackupImportConfirmForm form = new BackupImportConfirmForm();
        form.setStagingId(UUID.randomUUID());
        form.setAcknowledged(Boolean.TRUE);

        // when
        Set<ConstraintViolation<BackupImportConfirmForm>> violations = validator.validate(form);

        // then
        assertThat(violations).isEmpty();
    }

    @Test
    void givenStagingIdNull_whenValidate_thenViolatesNotNullOnStagingId() {
        // given
        BackupImportConfirmForm form = new BackupImportConfirmForm();
        form.setAcknowledged(Boolean.TRUE);
        // stagingId is null (not set)

        // when
        Set<ConstraintViolation<BackupImportConfirmForm>> violations = validator.validate(form);

        // then
        Set<ConstraintViolation<BackupImportConfirmForm>> stagingIdViolations = violationsFor("stagingId", violations);
        assertThat(stagingIdViolations)
                .as("the hidden input on the confirm form is required — the controller cannot resolve the staged file without it")
                .hasSize(1);
        assertThat(stagingIdViolations.iterator().next().getConstraintDescriptor().getAnnotation().annotationType())
                .isEqualTo(NotNull.class);
    }

    private Set<ConstraintViolation<BackupImportConfirmForm>> violationsFor(
            String path, Set<ConstraintViolation<BackupImportConfirmForm>> all) {
        return all.stream()
                .filter(v -> v.getPropertyPath().toString().equals(path))
                .collect(java.util.stream.Collectors.toSet());
    }
}
