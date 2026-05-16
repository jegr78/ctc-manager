package org.ctc.admin.dto;

import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RaceResultForm {

	private UUID driverId;
	private String driverPsnId;
	private String teamShortName;
	private int position;
	private int qualiPosition;
	private boolean fastestLap;
}
