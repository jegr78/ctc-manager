package org.ctc.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class PlayoffForm {

	private UUID id;
	private UUID seasonId;
	private String name;
	private int numberOfTeams = 8;
	private LocalDate startDate;
	private LocalDate endDate;
	private Integer eventDurationMinutes;
}
