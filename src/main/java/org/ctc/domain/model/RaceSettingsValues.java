package org.ctc.domain.model;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public record RaceSettingsValues(Integer laps, Integer tyreWear, Integer fuelConsumption, Integer refuelingSpeed,
                                 String initialFuel, Integer requiredPitStops, TimeProgressionMode timeProgressionMode,
                                 Integer timeProgressionMultiplier, String weather, String timeOfDay,
                                 String availableTyres, String mandatoryTyres) {
    public static RaceSettingsValues from(PickPayload payload) {
        return new RaceSettingsValues(payload.laps(), payload.tyreWear(), payload.fuelConsumption(), payload.refuelingSpeed(),
                payload.initialFuel().settingsValue(), payload.requiredPitStops(), payload.timeProgressionMode(),
                payload.timeProgressionMultiplier(), payload.weather(), payload.timeOfDay(),
                tyreText(payload.availableTyres()), payload.mandatoryTyres().isEmpty() ? "None" : tyreText(payload.mandatoryTyres()));
    }

    public static RaceSettingsValues from(RaceSettings settings) {
        return settings == null
                ? new RaceSettingsValues(null, null, null, null, null, null, TimeProgressionMode.MULTIPLIER, null, null, null, null, null)
                : new RaceSettingsValues(settings.getNumberOfLaps(), settings.getTyreWearMultiplier(), settings.getFuelConsumptionMultiplier(),
                        settings.getRefuelingSpeed(), settings.getInitialFuel(), settings.getNumberOfRequiredPitStops(),
                        settings.getTimeProgressionMode(), settings.getTimeProgressionMultiplier(), settings.getWeather(), settings.getTimeOfDay(),
                        settings.getAvailableTyres(), settings.getMandatoryTyres());
    }

    private static String tyreText(Set<PickTyre> tyres) {
        return Arrays.stream(PickTyre.values()).filter(tyres::contains).map(Enum::name).collect(Collectors.joining(", "));
    }

    public void applyTo(RaceSettings settings) {
        settings.setNumberOfLaps(laps);
        settings.setTyreWearMultiplier(tyreWear);
        settings.setFuelConsumptionMultiplier(fuelConsumption);
        settings.setRefuelingSpeed(refuelingSpeed);
        settings.setInitialFuel(initialFuel);
        settings.setNumberOfRequiredPitStops(requiredPitStops);
        settings.setTimeProgressionMode(timeProgressionMode);
        settings.setTimeProgressionMultiplier(timeProgressionMultiplier);
        settings.setWeather(weather);
        settings.setTimeOfDay(timeOfDay);
        settings.setAvailableTyres(availableTyres);
        settings.setMandatoryTyres(mandatoryTyres);
    }
}
