package org.ctc.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.UUID;

public record PickSetupSnapshot(UUID trackId, UUID carId, UUID entryId, RaceSettingsValues settings,
                                LobbyRulesProfile lobbyRulesProfile) {
    public PickSetupSnapshot(UUID trackId, UUID carId, UUID entryId, RaceSettingsValues settings) {
        this(trackId, carId, entryId, settings, LobbyRulesProfile.LEGACY);
    }
    public static PickSetupSnapshot from(Race race) {
        return new PickSetupSnapshot(race.getTrack() == null ? null : race.getTrack().getId(),
                race.getCar() == null ? null : race.getCar().getId(),
                race.getCarPoolEntry() == null ? null : race.getCarPoolEntry().getId(), RaceSettingsValues.from(race.getSettings()),
                LobbyRulesProfile.from(race));
    }

    public boolean configured() {
        return trackId != null || carId != null || entryId != null || !settings.equals(RaceSettingsValues.from((RaceSettings) null));
    }

    public boolean matches(PickPayload payload) {
        return lobbyRulesProfile == payload.lobbyRulesProfile()
                && java.util.Objects.equals(trackId, payload.trackId()) && java.util.Objects.equals(carId, payload.carId())
                && java.util.Objects.equals(entryId, payload.carPoolEntryId()) && settings.equals(RaceSettingsValues.from(payload));
    }

    public String fingerprint() {
        Object[] fields = {trackId, carId, entryId, settings.laps(), settings.tyreWear(), settings.fuelConsumption(),
                settings.refuelingSpeed(), settings.initialFuel(), settings.requiredPitStops(), settings.timeProgressionMode(),
                settings.timeProgressionMultiplier(), settings.weather(), settings.timeOfDay(), settings.availableTyres(), settings.mandatoryTyres()};
        var canonical = new StringBuilder();
        if (lobbyRulesProfile != LobbyRulesProfile.LEGACY || settings.startType() != RaceStartType.GRID_FALSE_START
                || settings.slipstream() != RaceSlipstream.REAL) {
            append(canonical, "ctc-lobby-rules-v1"); append(canonical, lobbyRulesProfile);
            append(canonical, settings.startType()); append(canonical, settings.slipstream());
        }
        Arrays.stream(fields).forEach(field -> append(canonical, field));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Required SHA-256 support is unavailable", unavailable);
        }
    }

    private static void append(StringBuilder canonical, Object field) {
        if (field == null) canonical.append("-1:");
        else { String value = field.toString(); canonical.append(value.length()).append(':').append(value); }
    }

    @Override public String toString() { return "PickSetupSnapshot[internal]"; }
}
