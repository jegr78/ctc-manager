package org.ctc.domain.model;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum PickDisclosureField {
    TRACK(1, "Track"), CAR(2, "Car and pool entry"), LAPS(4, "Laps"), TYRE_WEAR(8, "Tyre wear"),
    FUEL_CONSUMPTION(16, "Fuel consumption"), REFUELING_SPEED(32, "Refuelling speed"), INITIAL_FUEL(64, "Initial fuel"),
    PIT_STOPS(128, "Required pit stops"), TIME_PROGRESSION(256, "Time progression"), WEATHER(512, "Weather"),
    TIME_OF_DAY(1024, "Time of day"), AVAILABLE_TYRES(2048, "Available tyres"), MANDATORY_TYRES(4096, "Mandatory tyres"),
    START_TYPE(8192, "Start type"), SLIPSTREAM(16384, "Race slipstream");

    private final int bit;
    private final String label;
    PickDisclosureField(int bit, String label) { this.bit = bit; this.label = label; }
    public String getLabel() { return label; }
    public static Set<PickDisclosureField> all() { return Set.copyOf(EnumSet.allOf(PickDisclosureField.class)); }
    public static Set<PickDisclosureField> lobby() { var fields = EnumSet.allOf(PickDisclosureField.class); fields.remove(CAR); return Set.copyOf(fields); }
    public static int mask(Set<PickDisclosureField> fields) { return fields.stream().mapToInt(field -> field.bit).reduce(0, (left, right) -> left | right); }
    public static Set<PickDisclosureField> fields(int mask) {
        if ((mask & ~mask(all())) != 0) throw new IllegalArgumentException("Unsupported publication fields");
        return Arrays.stream(values()).filter(field -> (mask & field.bit) != 0).collect(Collectors.toUnmodifiableSet());
    }
}
