package org.ctc.domain.model;

import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PickWeatherSelectionTest {
    @Test
    void givenPreset_whenEncodingAndReading_thenKeepExactlyOneCode() {
        var selection = new PickWeatherSelection(PickWeatherSelection.Mode.PRESET, List.of("S01"));
        assertThat(selection.encode()).isEqualTo("Preset S01");
        assertThat(PickWeatherSelection.parseCanonical(selection.encode())).isEqualTo(selection);
        selection.requireAvailable(TrackRainCapability.UNKNOWN);
    }

    @ParameterizedTest
    @ValueSource(ints = {6, 7, 8})
    void givenCustomSequenceWithRepeatedCodesAndRandom_whenRoundTripping_thenPreserveEveryPosition(int count) {
        var codes = List.of("S01", "S01", "?", "R03", "?", "C06", "R03", "S01").subList(0, count);
        var selection = new PickWeatherSelection(PickWeatherSelection.Mode.CUSTOM, codes);
        assertThat(PickWeatherSelection.parseCanonical(selection.encode()).slots()).containsExactlyElementsOf(codes);
        selection.requireAvailable(TrackRainCapability.RAIN);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 5, 9})
    void givenInvalidCustomCount_whenConstructing_thenReject(int count) {
        assertThatThrownBy(() -> new PickWeatherSelection(PickWeatherSelection.Mode.CUSTOM,
                java.util.Collections.nCopies(count, "S01"))).isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?", "S00", "S19", "C07", "R09", "s01", " S01", "S01\n", ""})
    void givenInvalidPreset_whenConstructing_thenRejectWithoutInferringAnotherCode(String code) {
        assertThatThrownBy(() -> new PickWeatherSelection(PickWeatherSelection.Mode.PRESET, List.of(code)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenMissingOrMultiplePresetInputs_whenConstructing_thenRejectAsDomainErrors() {
        assertThatThrownBy(() -> new PickWeatherSelection(null, List.of("S01"))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new PickWeatherSelection(PickWeatherSelection.Mode.PRESET, null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new PickWeatherSelection(PickWeatherSelection.Mode.PRESET, List.of())).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new PickWeatherSelection(PickWeatherSelection.Mode.PRESET, List.of("S01", "S02"))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new PickWeatherSelection(PickWeatherSelection.Mode.CUSTOM,
                Arrays.asList("S01", "S01", null, "S01", "S01", "S01"))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenRainCodes_whenCheckingCurrentLayout_thenRequireVerifiedRainCapability() {
        var preset = new PickWeatherSelection(PickWeatherSelection.Mode.PRESET, List.of("R01"));
        preset.requireAvailable(TrackRainCapability.RAIN);
        for (var rain : List.of(TrackRainCapability.UNKNOWN, TrackRainCapability.DRY_ONLY)) {
            assertThatThrownBy(() -> preset.requireAvailable(rain)).isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("layout");
        }
        assertThatThrownBy(() -> preset.requireAvailable(null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenLayoutCatalog_whenBuildingChoices_thenRandomIsAnExtraCustomChoiceAndOrderIsStable() {
        assertThat(PickWeatherSelection.catalog(TrackRainCapability.DRY_ONLY, PickWeatherSelection.Mode.PRESET)).hasSize(24);
        assertThat(PickWeatherSelection.catalog(TrackRainCapability.RAIN, PickWeatherSelection.Mode.PRESET)).hasSize(32);
        assertThat(PickWeatherSelection.catalog(TrackRainCapability.UNKNOWN, PickWeatherSelection.Mode.CUSTOM))
                .hasSize(25).startsWith("?", "S01").endsWith("C06");
        assertThat(PickWeatherSelection.catalog(TrackRainCapability.RAIN, PickWeatherSelection.Mode.CUSTOM))
                .hasSize(33).startsWith("?", "S01").endsWith("R08");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Random", "Slot 1: S01; Slot 2: R03", "Custom S01; S02", "Preset s01", "arbitrary old weather"})
    void givenUnmappedHistoricText_whenReadingDefaults_thenKeepItUnmappedWithoutInventingSlots(String raw) {
        assertThat(PickWeatherSelection.readDefaults(raw)).isEmpty();
    }

    @Test
    void givenRecognizedSavedDefaults_whenReading_thenPreselectWithoutMutatingHistoricText() {
        String raw = "Custom S01;S01;?;C01;C02;C03";
        assertThat(PickWeatherSelection.readDefaults(raw)).get().extracting(PickWeatherSelection::slots)
                .isEqualTo(List.of("S01", "S01", "?", "C01", "C02", "C03"));
        assertThat(raw).isEqualTo("Custom S01;S01;?;C01;C02;C03");
        assertThat(PickWeatherSelection.readDefaults("S01")).get().extracting(PickWeatherSelection::encode).isEqualTo("Preset S01");
        assertThatThrownBy(() -> PickWeatherSelection.parseCanonical(raw)).isInstanceOf(BusinessRuleException.class);
    }
}
