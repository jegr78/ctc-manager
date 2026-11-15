package org.ctc.admin.dto;

import jakarta.validation.constraints.*;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LineupApplyForm {
    @NotNull @Min(0) private Long expectedVersion;
    @NotNull @Positive private Integer revisionNumber;
    @NotBlank @Pattern(regexp = "[0-9a-f-]{36}:[0-9]+") private String expectedGeneration;
    private boolean resultsConsent;
    /** Proposed PSN ID (lower case) to an existing driver ID, or {@code create} for a new driver. */
    private Map<String, String> links = new LinkedHashMap<>();
}
