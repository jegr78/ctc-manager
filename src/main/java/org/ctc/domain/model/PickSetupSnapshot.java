package org.ctc.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

public record PickSetupSnapshot(UUID trackId, UUID carId, UUID entryId, RaceSettingsValues settings) {
    public static PickSetupSnapshot from(Race race) {
        return new PickSetupSnapshot(race.getTrack() == null ? null : race.getTrack().getId(),
                race.getCar() == null ? null : race.getCar().getId(),
                race.getCarPoolEntry() == null ? null : race.getCarPoolEntry().getId(), RaceSettingsValues.from(race.getSettings()));
    }

    public boolean configured() {
        return trackId != null || carId != null || entryId != null || !settings.equals(RaceSettingsValues.from((RaceSettings) null));
    }

    public boolean matches(PickPayload payload) {
        return java.util.Objects.equals(trackId, payload.trackId()) && java.util.Objects.equals(carId, payload.carId())
                && java.util.Objects.equals(entryId, payload.carPoolEntryId()) && settings.equals(RaceSettingsValues.from(payload));
    }

    public String fingerprint() {
        Object[] fields = {trackId, carId, entryId, settings.laps(), settings.tyreWear(), settings.fuelConsumption(),
                settings.refuelingSpeed(), settings.initialFuel(), settings.requiredPitStops(), settings.timeProgressionMode(),
                settings.timeProgressionMultiplier(), settings.weather(), settings.timeOfDay(), settings.availableTyres(), settings.mandatoryTyres()};
        var canonical = new StringBuilder();
        Arrays.stream(fields).forEach(field -> {
            if (field == null) canonical.append("-1:");
            else { String value = field.toString(); canonical.append(value.length()).append(':').append(value); }
        });
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Required SHA-256 support is unavailable", unavailable);
        }
    }

    @Override public String toString() { return "PickSetupSnapshot[internal]"; }
}
