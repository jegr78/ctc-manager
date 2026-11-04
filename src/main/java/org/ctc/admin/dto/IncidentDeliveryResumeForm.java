package org.ctc.admin.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IncidentDeliveryResumeForm {
    @NotNull @Min(0) private Long expectedVersion;
    @NotBlank @Size(max=64) private String generation;
    @NotBlank @Size(max=1000) private String reason;
    @AssertTrue(message="Confirm your external review.") private boolean reviewed;
}
