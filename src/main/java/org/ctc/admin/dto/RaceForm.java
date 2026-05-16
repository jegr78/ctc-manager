package org.ctc.admin.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RaceForm {

	private UUID id;
	private UUID matchdayId;
	private UUID homeTeamId;
	private UUID awayTeamId;
	private UUID trackId;
	private UUID carId;
	private LocalDateTime dateTime;
	private List<RaceResultForm> results = new ArrayList<>();

	// Race Settings
	private Integer numberOfLaps;
	private Integer tyreWearMultiplier;
	private Integer fuelConsumptionMultiplier;
	private Integer refuelingSpeed;
	private String initialFuel;
	private Integer numberOfRequiredPitStops;
	private Integer timeProgressionMultiplier;
	private String weather;
	private String timeOfDay;
	private String availableTyres;
	private String mandatoryTyres;
}
