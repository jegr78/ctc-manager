package org.ctc.admin.dto;

import jakarta.validation.constraints.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.ctc.domain.model.*;

@Getter
@Setter
public class PickCorrectionForm {
    @NotNull @Min(0) private Long expectedVersion;
    @NotBlank @Pattern(regexp = "[0-9a-f-]{36}:[0-9]+") private String expectedGeneration;
    @NotBlank @Size(max = 1000) private String reason;
    @NotBlank @Pattern(regexp = "[0-9a-f]{64}") private String settingsFingerprint;
    @NotNull(message = "Reload the correction to capture its season rules.") private LobbyRulesProfile lobbyRulesProfile;
    @NotNull(message = "Choose an allowed start type.") private RaceStartType startType;
    @NotNull(message = "Choose an allowed race slipstream.") private RaceSlipstream slipstream;
    @NotNull private UUID trackId;
    @NotNull private UUID carId;
    private UUID carPoolEntryId;
    @NotNull @Min(1) private Integer laps;
    @NotNull @Min(1) @Max(7) private Integer tyreWear;
    @NotNull @Min(1) @Max(6) private Integer fuelConsumption;
    @NotNull @Min(1) @Max(10) private Integer refuelingSpeed;
    @NotNull private PickInitialFuel initialFuel;
    @NotNull @Min(0) @Max(2) private Integer requiredPitStops;
    @NotNull private TimeProgressionMode timeProgressionMode;
    @Min(0) @Max(6) private Integer timeProgressionMultiplier;
    @NotBlank @Size(max = 255) private String weather;
    @NotBlank @Size(max = 255) private String timeOfDay;
    @NotEmpty private Set<PickTyre> availableTyres = new LinkedHashSet<>();
    @NotNull private Set<PickTyre> mandatoryTyres = new LinkedHashSet<>();

    public PickPayload payload() {
        return new PickPayload(trackId, carId, carPoolEntryId, laps, tyreWear, fuelConsumption, refuelingSpeed,
                initialFuel, requiredPitStops, timeProgressionMode, timeProgressionMultiplier, weather, timeOfDay, availableTyres, mandatoryTyres, lobbyRulesProfile, startType, slipstream);
    }
}
