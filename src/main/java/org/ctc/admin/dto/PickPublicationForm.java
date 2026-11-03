package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PickPublicationForm {
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") private String expectedFingerprint;
    @NotBlank @Pattern(regexp = "[0-9a-f-]{36}:[0-9]+") private String expectedGeneration;
    @Pattern(regexp = "[0-9]{0,20}") private String messageId;
    @NotBlank @Size(max = 1000) private String reason;
    private boolean remoteHandled;
}
