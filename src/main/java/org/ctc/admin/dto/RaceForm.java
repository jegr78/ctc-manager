package org.ctc.admin.dto;

import org.ctc.domain.model.TimeProgressionMode;
import org.ctc.domain.model.LobbyRulesProfile;
import org.ctc.domain.model.RaceStartType;
import org.ctc.domain.model.RaceSlipstream;
import jakarta.validation.constraints.NotNull;

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
    private UUID carPoolEntryId;
	private LocalDateTime dateTime;
	private List<RaceResultForm> results = new ArrayList<>();

    @NotNull(message = "Reload the race form to capture the season rules.")
    private LobbyRulesProfile lobbyRulesProfile;
    @NotNull(message = "Choose a start type from the season options.")
    private RaceStartType startType;
    @NotNull(message = "Choose a race slipstream from the season options.")
    private RaceSlipstream slipstream;

	// Race Settings
	private Integer numberOfLaps;
	private Integer tyreWearMultiplier;
	private Integer fuelConsumptionMultiplier;
	private Integer refuelingSpeed;
	private String initialFuel;
	private Integer numberOfRequiredPitStops;
	private TimeProgressionMode timeProgressionMode = TimeProgressionMode.MULTIPLIER;
    private Integer timeProgressionMultiplier;
	private String weather;
	private String timeOfDay;
	private String availableTyres;
	private String mandatoryTyres;
}
