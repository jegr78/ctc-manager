package org.ctc.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class MatchdayPairingsForm {

	@NotNull
	private UUID id;

    private boolean picksOpen;
    @NotNull @jakarta.validation.constraints.PositiveOrZero
    private Long expectedVersion;
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp = "[0-9a-f]{64}")
    private String expectedScope;
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp = "[0-9a-f-]{36}:[0-9]+")
    private String expectedGeneration;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private LocalDateTime pickDeadline;

	@Size(max = 64)
	private String scheduledWeekend;
}
