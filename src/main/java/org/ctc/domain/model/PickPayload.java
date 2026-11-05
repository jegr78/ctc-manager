package org.ctc.domain.model;

import jakarta.persistence.*;
import java.util.Set;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;

@Embeddable
public record PickPayload(
        @Column(name = "track_id") UUID trackId,
        @Column(name = "car_id") UUID carId,
        @Column(name = "car_pool_entry_id") UUID carPoolEntryId,
        @Column(name = "laps") Integer laps,
        @Column(name = "tyre_wear") Integer tyreWear,
        @Column(name = "fuel_consumption") Integer fuelConsumption,
        @Column(name = "refueling_speed") Integer refuelingSpeed,
        @Enumerated(EnumType.STRING) @Column(name = "initial_fuel", length = 16) PickInitialFuel initialFuel,
        @Column(name = "required_pit_stops") Integer requiredPitStops,
        @Enumerated(EnumType.STRING) @Column(name = "time_progression_mode", length = 24) TimeProgressionMode timeProgressionMode,
        @Column(name = "time_progression_multiplier") Integer timeProgressionMultiplier,
        @Column(name = "weather", length = 255) String weather,
        @Column(name = "time_of_day", length = 255) String timeOfDay,
        @Convert(converter = PickTyreSetConverter.class) @Column(name = "available_tyres", length = 8) Set<PickTyre> availableTyres,
        @Convert(converter = PickTyreSetConverter.class) @Column(name = "mandatory_tyres", length = 8) Set<PickTyre> mandatoryTyres,
        @Enumerated(EnumType.STRING) @Column(name = "lobby_rules_profile", length = 16) LobbyRulesProfile lobbyRulesProfile,
        @Enumerated(EnumType.STRING) @Column(name = "start_type", length = 24) RaceStartType startType,
        @Enumerated(EnumType.STRING) @Column(name = "slipstream", length = 16) RaceSlipstream slipstream) {
    public PickPayload(UUID trackId, UUID carId, UUID carPoolEntryId, Integer laps, Integer tyreWear,
                       Integer fuelConsumption, Integer refuelingSpeed, PickInitialFuel initialFuel,
                       Integer requiredPitStops, TimeProgressionMode timeProgressionMode, Integer timeProgressionMultiplier,
                       String weather, String timeOfDay, Set<PickTyre> availableTyres, Set<PickTyre> mandatoryTyres) {
        this(trackId, carId, carPoolEntryId, laps, tyreWear, fuelConsumption, refuelingSpeed, initialFuel, requiredPitStops,
                timeProgressionMode, timeProgressionMultiplier, weather, timeOfDay, availableTyres, mandatoryTyres,
                LobbyRulesProfile.LEGACY, RaceStartType.GRID_FALSE_START, RaceSlipstream.REAL);
    }

    public PickPayload {
        require(lobbyRulesProfile != null, "requires a season rules profile");
        lobbyRulesProfile.requireChoices(startType, slipstream);
        require(trackId != null && carId != null, "requires a track and concrete car");
        require(laps != null && laps > 0, "laps must be positive");
        bounded(tyreWear, 1, 7, "tyre wear");
        bounded(fuelConsumption, 1, 6, "fuel consumption");
        bounded(refuelingSpeed, 1, 10, "refuelling speed");
        require(initialFuel != null, "requires initial fuel");
        bounded(requiredPitStops, 0, 2, "required pit stops");
        require(timeProgressionMode != null, "requires a time progression mode");
        if (timeProgressionMode == TimeProgressionMode.EQUAL_CONDITIONS) {
            require(timeProgressionMultiplier == null, "Equal Conditions must not have a numeric multiplier");
        } else {
            bounded(timeProgressionMultiplier, 0, 6, "time progression multiplier");
        }
        text(weather, "weather");
        text(timeOfDay, "time of day");
        require(availableTyres != null && !availableTyres.isEmpty() && availableTyres.stream().noneMatch(java.util.Objects::isNull),
                "requires a non-empty available tyre set");
        require(mandatoryTyres != null && mandatoryTyres.stream().noneMatch(java.util.Objects::isNull)
                && availableTyres.containsAll(mandatoryTyres), "mandatory tyres must be a subset of available tyres");
        availableTyres = Set.copyOf(availableTyres);
        mandatoryTyres = Set.copyOf(mandatoryTyres);
    }

    private static void bounded(Integer value, int min, int max, String field) {
        require(value != null && value >= min && value <= max, field + " must be between " + min + " and " + max);
    }

    private static void text(String value, String field) {
        require(value != null && !value.isBlank() && value.length() <= 255, field + " is required and limited to 255 characters");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new BusinessRuleException("Pick " + message);
    }
}
