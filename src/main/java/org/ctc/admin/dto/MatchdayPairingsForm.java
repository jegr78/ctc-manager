package org.ctc.admin.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

@Getter
@Setter
public class MatchdayPairingsForm {

	private UUID id;

	@DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
	private LocalDateTime pickDeadline;

	@Size(max = 64)
	private String scheduledWeekend;
}
