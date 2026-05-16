package org.ctc.admin.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MatchScoringForm {

	private UUID id;

	@NotBlank
	private String name;

	private int pointsWin;

	private int pointsDraw;

	private int pointsLoss;
}
