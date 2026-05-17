package org.ctc.admin.dto;

import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MatchdayGeneratorForm {

	@Min(1)
	private int numberOfRounds;

	private boolean homeAndAway;

	private UUID groupId;
}
