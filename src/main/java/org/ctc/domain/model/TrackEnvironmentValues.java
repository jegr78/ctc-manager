package org.ctc.domain.model;

import java.util.HashSet;
import java.util.List;
import org.ctc.domain.exception.BusinessRuleException;

public record TrackEnvironmentValues(List<String> timeOfDayOptions, TrackRainCapability rainCapability) {
    public TrackEnvironmentValues {
        timeOfDayOptions = timeOfDayOptions == null ? List.of() : timeOfDayOptions;
        if (rainCapability == null) throw new BusinessRuleException("Choose a rain capability, including Unknown when unverified");
        if (timeOfDayOptions.size() > 25) throw new BusinessRuleException("Time of day supports at most 25 choices per layout");
        var unique = new HashSet<String>();
        for (String option : timeOfDayOptions) {
            if (option == null || option.isBlank() || !option.equals(option.strip()) || option.length() > 100
                    || option.chars().anyMatch(Character::isISOControl) || !unique.add(option)) {
                throw new BusinessRuleException("Time of day choices must be distinct, nonblank labels of at most 100 characters without control characters");
            }
        }
        timeOfDayOptions = List.copyOf(timeOfDayOptions);
    }
    public static TrackEnvironmentValues fromText(String text, TrackRainCapability rain) {
        if (text != null && text.length() > 3000) throw new BusinessRuleException("Time of day input is too long");
        return new TrackEnvironmentValues(text == null ? List.of() : text.lines().map(String::strip).filter(s -> !s.isEmpty()).toList(), rain);
    }
    public static TrackEnvironmentValues from(Track track) {
        if (track == null || track.getRainCapability() == null) {
            throw new BusinessRuleException("Track environment metadata is invalid; review this layout");
        }
        try { return new TrackEnvironmentValues(track.getTimeOfDayOptions(), TrackRainCapability.valueOf(track.getRainCapability())); }
        catch (IllegalArgumentException invalid) { throw new BusinessRuleException("Track environment metadata is invalid; review this layout"); }
    }
}
