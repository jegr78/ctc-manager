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
    @ParameterizedTest @ValueSource(strings = {"duplicate", "blank", "long", "many", "control", "trim", "null"})
    void givenInvalidChoices_whenValidating_thenRejectWithoutInventingOptions(String invalid) {
        var values = switch (invalid) {
            case "duplicate" -> List.of("Sunrise", "Sunrise");
            case "blank" -> List.of(" ");
            case "long" -> List.of("x".repeat(101));
            case "many" -> java.util.stream.IntStream.range(0,26).mapToObj(i -> "Time " + i).toList();
            case "trim" -> List.of(" Sunrise");
            case "null" -> java.util.Arrays.asList((String) null);
            default -> List.of("Sunrise\tNight");
        };
        assertThatThrownBy(() -> new TrackEnvironmentValues(values, TrackRainCapability.UNKNOWN))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Time of day");
    }
    @Test void givenMissingRainOrOversizedNativeInput_whenParsing_thenRejectInsteadOfDefaultingVerifiedMetadata() {
        assertThatThrownBy(() -> TrackEnvironmentValues.fromText("Night", null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> TrackEnvironmentValues.fromText(" ".repeat(3001), TrackRainCapability.UNKNOWN))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("too long");
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullSource
    @ValueSource(strings = {"INVALID", "rain"})
    void givenMissingOrUnknownPersistedRain_whenReadingLayout_thenReportDomainError(String rain) {
        var layout = new Track("Test invalid metadata", "Test country");
        layout.setRainCapability(rain);
        assertThatThrownBy(() -> TrackEnvironmentValues.from(layout))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("metadata is invalid");
    }

    @Test void givenMissingLayout_whenReadingMetadata_thenReportDomainError() {
        assertThatThrownBy(() -> TrackEnvironmentValues.from(null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("metadata is invalid");
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
