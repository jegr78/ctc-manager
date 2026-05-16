package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateMatchdayRequest(@NotNull UUID seasonId, @NotBlank String label) {
}
