package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateMatchdayRequest(@NotBlank String seasonName, @NotBlank String label) {
}
