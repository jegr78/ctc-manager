package org.ctc.admin.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IncidentRulingReviewForm {
    @NotNull @Min(0) private Long expectedVersion;
    @NotNull private UUID publicationId;
    @NotNull @Min(0) private Long publicationVersion;
    @NotBlank @Pattern(regexp = "RETRY_MISSING|CONFIRM_SENT") private String decision = "";
    @Pattern(regexp = "^$|^[0-9]{1,20}$") private String messageId = "";
    @NotBlank @Size(max = 1000) private String reason = "";
    @AssertTrue private boolean reviewed;
    @NotBlank @Size(max = 200) private String generation = "";
}
