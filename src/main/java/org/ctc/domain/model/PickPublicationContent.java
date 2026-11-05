package org.ctc.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import org.ctc.domain.picks.PublicPickProjection;
import org.ctc.domain.service.RaceCarPresentation;

@Embeddable
public record PickPublicationContent(
        @Column(name = "track_id", updatable = false) UUID trackId,
        @Column(name = "car_id", updatable = false) UUID carId,
        @Column(name = "car_pool_entry_id", updatable = false) UUID carPoolEntryId,
        @Column(name = "track_name", length = 255, updatable = false) String trackName,
        @Column(name = "car_name", length = 1024, updatable = false) String carName,
        @Column(name = "pool_entry_label", length = 255, updatable = false) String poolEntryLabel,
        @Column(name = "laps", updatable = false) Integer laps,
        @Column(name = "tyre_wear", updatable = false) Integer tyreWear,
        @Column(name = "fuel_consumption", updatable = false) Integer fuelConsumption,
        @Column(name = "refueling_speed", updatable = false) Integer refuelingSpeed,
        @Column(name = "initial_fuel", updatable = false) String initialFuel,
        @Column(name = "required_pit_stops", updatable = false) Integer requiredPitStops,
        @Enumerated(EnumType.STRING) @Column(name = "time_progression_mode", length = 24, updatable = false) TimeProgressionMode timeProgressionMode,
        @Column(name = "time_progression_multiplier", updatable = false) Integer timeProgressionMultiplier,
        @Column(name = "weather", length = 255, updatable = false) String weather,
        @Column(name = "time_of_day", length = 255, updatable = false) String timeOfDay,
        @Column(name = "available_tyres", updatable = false) String availableTyres,
        @Column(name = "mandatory_tyres", updatable = false) String mandatoryTyres,
        @Enumerated(EnumType.STRING) @Column(name = "lobby_rules_profile", length = 16, updatable = false) LobbyRulesProfile lobbyRulesProfile,
        @Enumerated(EnumType.STRING) @Column(name = "start_type", length = 24, updatable = false) RaceStartType startType,
        @Enumerated(EnumType.STRING) @Column(name = "slipstream", length = 16, updatable = false) RaceSlipstream slipstream) {

    public PickPublicationContent(UUID trackId, UUID carId, UUID carPoolEntryId, String trackName, String carName,
                                  String poolEntryLabel, Integer laps, Integer tyreWear, Integer fuelConsumption,
                                  Integer refuelingSpeed, String initialFuel, Integer requiredPitStops,
                                  TimeProgressionMode timeProgressionMode, Integer timeProgressionMultiplier,
                                  String weather, String timeOfDay, String availableTyres, String mandatoryTyres) {
        this(trackId, carId, carPoolEntryId, trackName, carName, poolEntryLabel, laps, tyreWear, fuelConsumption,
                refuelingSpeed, initialFuel, requiredPitStops, timeProgressionMode, timeProgressionMultiplier,
                weather, timeOfDay, availableTyres, mandatoryTyres, LobbyRulesProfile.LEGACY,
                RaceStartType.GRID_FALSE_START, RaceSlipstream.REAL);
    }

    public static PickPublicationContent from(Race race) {
        var values = RaceSettingsValues.from(race.getSettings()); var car = RaceCarPresentation.from(race);
        return new PickPublicationContent(race.getTrack() == null ? null : race.getTrack().getId(),
                race.getCar() == null ? null : race.getCar().getId(), race.getCarPoolEntry() == null ? null : race.getCarPoolEntry().getId(),
                race.getTrack() == null ? null : race.getTrack().getName(), car.carName(), car.poolEntryLabel(),
                values.laps(), values.tyreWear(), values.fuelConsumption(), values.refuelingSpeed(), values.initialFuel(), values.requiredPitStops(),
                values.timeProgressionMode(), values.timeProgressionMultiplier(), values.weather(), values.timeOfDay(), values.availableTyres(), values.mandatoryTyres(),
                LobbyRulesProfile.from(race), values.startType(), values.slipstream());
    }

    public RaceSettingsValues settings() {
        return new RaceSettingsValues(laps, tyreWear, fuelConsumption, refuelingSpeed, initialFuel, requiredPitStops,
                timeProgressionMode, timeProgressionMultiplier, weather, timeOfDay, availableTyres, mandatoryTyres, startType, slipstream);
    }

    public String fingerprint() {
        Object[] fields = {new PickSetupSnapshot(trackId, carId, carPoolEntryId, settings(), lobbyRulesProfile).fingerprint(), trackName, carName, poolEntryLabel};
        var canonical = new StringBuilder();
        for (var field : fields) {
            if (field == null) canonical.append("-1:");
            else { var value = field.toString(); canonical.append(value.length()).append(':').append(value); }
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Required SHA-256 support is unavailable", unavailable);
        }
    }

    public PublicPickProjection project(UUID raceId, Set<PickDisclosureField> fields) {
        boolean hasSettings = fields.stream().anyMatch(field -> field != PickDisclosureField.CAR && field != PickDisclosureField.TRACK);
        var visible = !hasSettings ? null : new RaceSettingsValues(
                fields.contains(PickDisclosureField.LAPS) ? laps : null,
                fields.contains(PickDisclosureField.TYRE_WEAR) ? tyreWear : null,
                fields.contains(PickDisclosureField.FUEL_CONSUMPTION) ? fuelConsumption : null,
                fields.contains(PickDisclosureField.REFUELING_SPEED) ? refuelingSpeed : null,
                fields.contains(PickDisclosureField.INITIAL_FUEL) ? initialFuel : null,
                fields.contains(PickDisclosureField.PIT_STOPS) ? requiredPitStops : null,
                fields.contains(PickDisclosureField.TIME_PROGRESSION) ? timeProgressionMode : null,
                fields.contains(PickDisclosureField.TIME_PROGRESSION) ? timeProgressionMultiplier : null,
                fields.contains(PickDisclosureField.WEATHER) ? weather : null,
                fields.contains(PickDisclosureField.TIME_OF_DAY) ? timeOfDay : null,
                fields.contains(PickDisclosureField.AVAILABLE_TYRES) ? availableTyres : null,
                fields.contains(PickDisclosureField.MANDATORY_TYRES) ? mandatoryTyres : null,
                fields.contains(PickDisclosureField.START_TYPE) ? startType : null,
                fields.contains(PickDisclosureField.SLIPSTREAM) ? slipstream : null);
        return new PublicPickProjection(raceId, fields.contains(PickDisclosureField.CAR) ? carName : null,
                fields.contains(PickDisclosureField.CAR) ? poolEntryLabel : null,
                fields.contains(PickDisclosureField.TRACK) ? trackName : null, visible);
    }

    @Override public String toString() { return "PickPublicationContent[internal]"; }
}
