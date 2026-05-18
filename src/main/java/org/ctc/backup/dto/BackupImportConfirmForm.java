package org.ctc.backup.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Form DTO bound by Spring's {@code WebDataBinder} for the confirm-import POST.
 *
 * <p>Uses {@code Boolean} (wrapper) instead of primitive {@code boolean}: a primitive defaults
 * to {@code false} after missing-input binding, making it impossible to distinguish "user
 * submitted false" from "checkbox was absent entirely". The wrapper keeps the {@code null} vs
 * {@code false} distinction, so {@code @NotNull} fires for the absent-checkbox case and
 * {@code @AssertTrue} fires for the explicit {@code false} case.
 */
@Getter
@Setter
@NoArgsConstructor
public class BackupImportConfirmForm {

    @NotNull
    private UUID stagingId;

    @NotNull
    @AssertTrue(message = "You must acknowledge the deletion warning to continue.")
    private Boolean acknowledged;
}
