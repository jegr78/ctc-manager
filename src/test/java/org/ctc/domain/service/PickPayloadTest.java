package org.ctc.domain.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PickInitialFuel;
import org.ctc.domain.model.PickPayload;
import org.ctc.domain.model.PickTyre;
import org.ctc.domain.model.TimeProgressionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PickPayloadTest {
    @ParameterizedTest
    @EnumSource(PickInitialFuel.class)
    void givenEveryInitialFuelOption_whenConfirming_thenRetainTypedChoice(PickInitialFuel fuel) {
        // given / when / then
        var input = new Input();
        input.initialFuel = fuel;
        assertThat(input.create().initialFuel()).isEqualTo(fuel);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6})
    void givenEveryMultiplier_whenConfirming_thenRetainNumericModeIncludingZero(int multiplier) {
        // given / when / then
        var input = new Input();
        input.timeMultiplier = multiplier;
        assertThat(input.create().timeProgressionMultiplier()).isEqualTo(multiplier);
        assertThat(input.create().timeProgressionMode()).isEqualTo(TimeProgressionMode.MULTIPLIER);
    }

    @Test
    void givenEqualConditions_whenConfirming_thenRetainAbsentMultiplierAndWeatherOrder() {
        // given / when / then
        var input = new Input();
        input.mode = TimeProgressionMode.EQUAL_CONDITIONS;
        input.timeMultiplier = null;
        input.weather = "Slot 1: S01; Slot 2: R04; Slot 3: S02";
        var payload = input.create();
        assertThat(payload.timeProgressionMultiplier()).isNull();
        assertThat(payload.weather()).isEqualTo(input.weather);
        assertThat(payload.mandatoryTyres()).isEmpty();
    }

    @Test
    void givenMutableTyreSets_whenConfirming_thenTakeImmutableCopies() {
        // given / when / then
        var input = new Input();
        input.available = new HashSet<>(Set.of(PickTyre.RS, PickTyre.RM));
        input.mandatory = new HashSet<>(Set.of(PickTyre.RM));
        var payload = input.create();
        input.available.clear();
        input.mandatory.clear();
        assertThat(payload.availableTyres()).containsExactlyInAnyOrder(PickTyre.RS, PickTyre.RM);
        assertThat(payload.mandatoryTyres()).containsExactly(PickTyre.RM);
        assertThatThrownBy(() -> payload.availableTyres().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> payload.mandatoryTyres().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"track", "car", "laps-null", "laps-zero", "laps-negative", "wear-null", "wear-zero", "wear-eight",
            "fuel-null", "fuel-zero", "fuel-seven", "refuel-null", "refuel-zero", "refuel-eleven", "initial-fuel", "stops-null",
            "stops-negative", "stops-three", "mode", "multiplier-null", "multiplier-negative", "multiplier-seven", "equal-with-number",
            "weather-null", "weather-blank", "weather-long", "time-null", "time-blank", "time-long", "available-null", "available-empty",
            "mandatory-null", "mandatory-outside-available"})
    void givenInvalidOrIncompletePayload_whenConfirming_thenRejectWithBusinessReason(String invalid) {
        // given / when / then
        var input = new Input();
        Consumer<Input> change = switch (invalid) {
            case "track" -> i -> i.track = null;
            case "car" -> i -> i.car = null;
            case "laps-null" -> i -> i.laps = null;
            case "laps-zero" -> i -> i.laps = 0;
            case "laps-negative" -> i -> i.laps = -1;
            case "wear-null" -> i -> i.wear = null;
            case "wear-zero" -> i -> i.wear = 0;
            case "wear-eight" -> i -> i.wear = 8;
            case "fuel-null" -> i -> i.fuel = null;
            case "fuel-zero" -> i -> i.fuel = 0;
            case "fuel-seven" -> i -> i.fuel = 7;
            case "refuel-null" -> i -> i.refuel = null;
            case "refuel-zero" -> i -> i.refuel = 0;
            case "refuel-eleven" -> i -> i.refuel = 11;
            case "initial-fuel" -> i -> i.initialFuel = null;
            case "stops-null" -> i -> i.stops = null;
            case "stops-negative" -> i -> i.stops = -1;
            case "stops-three" -> i -> i.stops = 3;
            case "mode" -> i -> i.mode = null;
            case "multiplier-null" -> i -> i.timeMultiplier = null;
            case "multiplier-negative" -> i -> i.timeMultiplier = -1;
            case "multiplier-seven" -> i -> i.timeMultiplier = 7;
            case "equal-with-number" -> i -> i.mode = TimeProgressionMode.EQUAL_CONDITIONS;
            case "weather-null" -> i -> i.weather = null;
            case "weather-blank" -> i -> i.weather = "  ";
            case "weather-long" -> i -> i.weather = "W".repeat(256);
            case "time-null" -> i -> i.time = null;
            case "time-blank" -> i -> i.time = "\t";
            case "time-long" -> i -> i.time = "T".repeat(256);
            case "available-null" -> i -> i.available = null;
            case "available-empty" -> i -> i.available = Set.of();
            case "mandatory-null" -> i -> i.mandatory = null;
            case "mandatory-outside-available" -> i -> i.mandatory = Set.of(PickTyre.RH);
            default -> throw new AssertionError(invalid);
        };
        change.accept(input);
        assertThatThrownBy(input::create).isInstanceOf(BusinessRuleException.class).hasMessageContaining("Pick");
    }

    @Test
    void givenMaximumAndMinimumOptions_whenConfirming_thenAcceptAllBoundaries() {
        // given / when / then
        for (int wear = 1; wear <= 7; wear++) {
            for (int fuel = 1; fuel <= 6; fuel++) {
                for (int refuel = 1; refuel <= 10; refuel++) {
                    for (int stops = 0; stops <= 2; stops++) {
                        var input = new Input();
                        input.wear = wear;
                        input.fuel = fuel;
                        input.refuel = refuel;
                        input.stops = stops;
                        input.weather = "W".repeat(255);
                        input.time = "T".repeat(255);
                        assertThat(input.create().requiredPitStops()).isEqualTo(stops);
                    }
                }
            }
        }
    }

    private static class Input {
        UUID track = UUID.randomUUID();
        UUID car = UUID.randomUUID();
        Integer laps = 1;
        Integer wear = 1;
        Integer fuel = 1;
        Integer refuel = 1;
        PickInitialFuel initialFuel = PickInitialFuel.DEFAULT;
        Integer stops = 0;
        TimeProgressionMode mode = TimeProgressionMode.MULTIPLIER;
        Integer timeMultiplier = 0;
        String weather = "S01";
        String time = "Afternoon";
        Set<PickTyre> available = Set.of(PickTyre.RS, PickTyre.RM);
        Set<PickTyre> mandatory = Set.of();

        PickPayload create() {
            return new PickPayload(track, car, null, laps, wear, fuel, refuel, initialFuel, stops, mode,
                    timeMultiplier, weather, time, available, mandatory);
        }
    }
}
