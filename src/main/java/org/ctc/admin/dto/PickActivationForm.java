package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.ctc.domain.model.LegacyPickClassification;

@Getter
@Setter
public class PickActivationForm {
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") private String expectedFingerprint;
    @NotBlank @Pattern(regexp = "[0-9a-f-]{36}:[0-9]+") private String expectedGeneration;
    private Map<UUID, LegacyPickClassification> classifications = new LinkedHashMap<>();
}
