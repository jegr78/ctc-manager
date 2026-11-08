package org.ctc.domain.model;

import java.util.List;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class TrackEnvironmentValuesTest {
    @Test void givenManualLines_whenReadingEnvironment_thenPreserveOrderAndExplicitRainState() {
        var values = TrackEnvironmentValues.fromText("  Sunrise \nNight\n", TrackRainCapability.RAIN);
        assertThat(values.timeOfDayOptions()).containsExactly("Sunrise", "Night");
        assertThat(values.rainCapability()).isEqualTo(TrackRainCapability.RAIN);
        assertThat(TrackEnvironmentValues.fromText("", TrackRainCapability.UNKNOWN).timeOfDayOptions()).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"duplicate", "blank", "long", "many", "control"})
    void givenInvalidChoices_whenValidating_thenRejectWithoutInventingOptions(String invalid) {
        var values = switch (invalid) {
            case "duplicate" -> List.of("Sunrise", "Sunrise");
            case "blank" -> List.of(" ");
            case "long" -> List.of("x".repeat(101));
            case "many" -> java.util.stream.IntStream.range(0,26).mapToObj(i -> "Time " + i).toList();
            default -> List.of("Sunrise\tNight");
        };
        assertThatThrownBy(() -> new TrackEnvironmentValues(values, TrackRainCapability.UNKNOWN))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Time of day");
    }
    @Test void givenGlobalPresets_whenFilteringRainCapability_thenReturnOrdered24Or32WithoutInventingRainSupport() {
        assertThat(WeatherPreset.available(TrackRainCapability.DRY_ONLY)).hasSize(24);
        assertThat(WeatherPreset.available(TrackRainCapability.UNKNOWN)).hasSize(24);
        var wet = WeatherPreset.available(TrackRainCapability.RAIN);
        assertThat(wet).hasSize(32); assertThat(wet.getFirst().name()).isEqualTo("S01");
        assertThat(wet.getLast().name()).isEqualTo("R08");
        assertThat(wet).allSatisfy(p -> assertThat(p.getDescription()).isNotBlank());
        assertThat(wet.stream().map(Enum::name)).doesNotHaveDuplicates();
    }
    @Test void givenSavedChoices_whenConverting_thenKeepExactLabelsAndOrderAndRejectMalformedStorage() {
        var converter = new TrackTimeOptionsConverter();
        var options = List.of("Night", "Sunrise");
        assertThat(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(options))).containsExactlyElementsOf(options);
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
        assertThatThrownBy(() -> new TrackEnvironmentValues(converter.convertToEntityAttribute("Sunrise\nSunrise"), TrackRainCapability.UNKNOWN)).isInstanceOf(BusinessRuleException.class);
    }
}
