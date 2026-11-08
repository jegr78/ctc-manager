package org.ctc.domain.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.ctc.domain.exception.BusinessRuleException;

/** New Discord weather selections; historic free text remains stored unchanged. */
public record PickWeatherSelection(Mode mode, List<String> slots) {
    public enum Mode { PRESET, CUSTOM }

    public PickWeatherSelection {
        if (mode == null || slots == null) throw invalid();
        if (mode == Mode.PRESET ? slots.size() != 1 : slots.size() < 6 || slots.size() > 8) throw invalid();
        for (String code : slots) {
            if (!(mode == Mode.CUSTOM && "?".equals(code))
                    && Arrays.stream(WeatherPreset.values()).noneMatch(preset -> preset.name().equals(code))) throw invalid();
        }
        slots = List.copyOf(slots);
    }

    public String encode() {
        return (mode == Mode.PRESET ? "Preset " : "Custom ") + String.join("; ", slots);
    }

    public void requireAvailable(TrackRainCapability rain) {
        if (!catalog(rain, mode).containsAll(slots)) {
            throw new BusinessRuleException("Weather is unavailable for this layout; reload its current weather choices");
        }
    }

    public static List<String> catalog(TrackRainCapability rain, Mode mode) {
        if (rain == null || mode == null) throw invalid();
        var choices = new ArrayList<String>();
        if (mode == Mode.CUSTOM) choices.add("?");
        WeatherPreset.available(rain).forEach(preset -> choices.add(preset.name()));
        return List.copyOf(choices);
    }

    public static PickWeatherSelection parseCanonical(String raw) {
        var selection = readDefaults(raw).orElseThrow(PickWeatherSelection::invalid);
        if (!selection.encode().equals(raw)) throw invalid();
        return selection;
    }

    public static Optional<PickWeatherSelection> readDefaults(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 255) return Optional.empty();
        Mode mode;
        String codes;
        if (raw.startsWith("Preset ")) { mode = Mode.PRESET; codes = raw.substring(7); }
        else if (raw.startsWith("Custom ")) { mode = Mode.CUSTOM; codes = raw.substring(7); }
        else { mode = Mode.PRESET; codes = raw; }
        try {
            return Optional.of(new PickWeatherSelection(mode, Arrays.stream(codes.split(";", -1)).map(String::strip).toList()));
        } catch (BusinessRuleException invalid) {
            return Optional.empty();
        }
    }

    private static BusinessRuleException invalid() {
        return new BusinessRuleException("Choose one preset or 6 to 8 custom weather slots using known codes; Random (?) is a custom choice");
    }
}
