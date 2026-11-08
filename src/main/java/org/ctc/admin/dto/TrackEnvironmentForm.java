package org.ctc.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.ctc.domain.model.TrackRainCapability;

@Getter
@Setter
public class TrackEnvironmentForm {
    @Size(max = 3000) private String timeOfDayText;
    @NotNull private TrackRainCapability rainCapability = TrackRainCapability.UNKNOWN;
    @NotNull @Min(0) private Long expectedVersion;
    @NotBlank @Size(max = 128) private String expectedGeneration;
}
