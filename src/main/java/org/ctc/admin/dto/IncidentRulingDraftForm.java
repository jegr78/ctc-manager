package org.ctc.admin.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IncidentRulingDraftForm {
    @NotNull @Min(0) private Long expectedVersion;
    @NotBlank @Size(max = 1800) private String text = "";
    @Size(max = 1000) private String reason = "";
    @NotBlank @Size(max = 200) private String generation = "";
}
