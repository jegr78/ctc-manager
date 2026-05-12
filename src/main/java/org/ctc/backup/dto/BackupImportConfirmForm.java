package org.ctc.backup.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Form DTO bound by Spring's {@code WebDataBinder} for the confirm-import POST.
 *
 * <p>Phase 74 — CONTEXT D-10 + D-21. Bound by
 * {@code BackupController.importExecute(@Valid @ModelAttribute("confirmForm") ...)}
 * (Plan 06) and rendered by {@code admin/backup-confirm.html} (Plan 07).
 *
 * <p>Why {@code Boolean} (wrapper) and not primitive {@code boolean}:
 * A primitive {@code boolean} defaults to {@code false} after missing-input binding,
 * making it impossible to distinguish "user submitted false" from "checkbox was absent
 * entirely". Using the {@code Boolean} wrapper keeps the {@code null} vs {@code false}
 * distinction, so {@code @NotNull} fires for the absent-checkbox case and
 * {@code @AssertTrue} fires for the explicit {@code false} case — per
 * RESEARCH §Pitfall 3 + Jakarta Bean Validation §6.1.1.
 *
 * <p>The {@code @AssertTrue} message is the UI-SPEC-locked inline-error string (locked in
 * CONTEXT D-02 + UI-SPEC §Copywriting Contract). i18n bundle keys are deferred per
 * CONTEXT "Deferred Ideas" — if/when {@code messages.properties} is introduced in a
 * future phase, this attribute swaps to a bundle key in a one-line change.
 */
@Getter @Setter @NoArgsConstructor
public class BackupImportConfirmForm {

    @NotNull
    private UUID stagingId;

    @NotNull
    @AssertTrue(message = "You must acknowledge the deletion warning to continue.")
    private Boolean acknowledged;
}
