package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TrackForm {

	private UUID id;

	@NotBlank
	private String name;

	private String country;

    @jakarta.validation.constraints.Size(max = 3000)
    private String timeOfDayText;
    @jakarta.validation.constraints.NotNull
    private org.ctc.domain.model.TrackRainCapability rainCapability = org.ctc.domain.model.TrackRainCapability.UNKNOWN;
}
