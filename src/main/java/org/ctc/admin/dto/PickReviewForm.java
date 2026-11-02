package org.ctc.admin.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PickReviewForm {
    @NotNull @Min(0) private Long expectedVersion;
    @NotNull @Min(0) private Long expectedGeneration;
    @Positive private Integer revisionNumber;
    @NotNull private Action action;
    @NotBlank @Size(max = 1000) private String reason;
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") private String settingsFingerprint;
    private boolean replaceExisting;

    public enum Action {
        OPEN_MISSING("Open missing submission"), RETURN("Return for correction"), CLOSE("Close team editing"), APPROVE("Approve selected revision");
        private final String label;
        Action(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
}
