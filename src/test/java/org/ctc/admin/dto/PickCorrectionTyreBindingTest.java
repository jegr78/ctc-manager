package org.ctc.admin.dto;

import java.util.Map;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.validation.DataBinder;

import static org.assertj.core.api.Assertions.*;

class PickCorrectionTyreBindingTest {
    @org.junit.jupiter.api.Test
    void givenFiveAvailableRacingAndThreeMandatoryDryCompounds_whenBindingAndValidating_thenRetainTheCompleteSelection() {
        // given
        var form = complete();
        form.setExpectedVersion(0L); form.setExpectedGeneration("00000000-0000-0000-0000-000000000000:0");
        form.setReason("Test complete compound selection"); form.setSettingsFingerprint("a".repeat(64));
        var binding = new DataBinder(form); binding.setConversionService(new DefaultConversionService());
        // when
        binding.bind(new MutablePropertyValues(Map.of("availableTyres", new String[]{"RS", "RM", "RH", "I", "W"},
                "mandatoryTyres", new String[]{"RS", "RM", "RH"})));
        // then
        assertThat(binding.getBindingResult().hasErrors()).isFalse();
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(form)).isEmpty();
        }
        assertThat(form.payload().availableTyres()).containsExactlyInAnyOrder(PickTyre.RS, PickTyre.RM, PickTyre.RH, PickTyre.I, PickTyre.W);
        assertThat(form.payload().mandatoryTyres()).containsExactlyInAnyOrder(PickTyre.RS, PickTyre.RM, PickTyre.RH);
    }

    @org.junit.jupiter.api.Test
    void givenDryOnlyRacingCorrection_whenBuildingPayload_thenIncludeAutomaticWetCompounds() {
        // given
        var form = complete();
        form.setAvailableTyres(java.util.List.of(PickTyre.RH)); form.setMandatoryTyres(java.util.List.of(PickTyre.RH));
        // when / then
        assertThat(form.payload().availableTyres()).containsExactlyInAnyOrder(PickTyre.RH, PickTyre.I, PickTyre.W);
        assertThat(form.payload().mandatoryTyres()).containsExactly(PickTyre.RH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"availableTyres", "mandatoryTyres"})
    void givenDuplicateRawCorrectionValues_whenBinding_thenRejectBeforeSetConversion(String duplicated) {
        // given
        var form = complete();
        var binding = new DataBinder(form);
        binding.setConversionService(new DefaultConversionService());
        var values = new MutablePropertyValues(Map.of("availableTyres", new String[]{"RS", "RM"}, "mandatoryTyres", new String[0]));
        values.add(duplicated, new String[]{"RM", "RM"});
        // when
        binding.bind(values);
        // then
        assertThat(binding.getBindingResult().hasErrors()).isFalse();
        assertThatThrownBy(form::payload).isInstanceOf(BusinessRuleException.class).hasMessageContaining("once");
    }

    private static PickCorrectionForm complete() {
        var form = new PickCorrectionForm();
        form.setTrackId(UUID.randomUUID()); form.setCarId(UUID.randomUUID()); form.setCarPoolEntryId(UUID.randomUUID());
        form.setLaps(12); form.setTyreWear(2); form.setFuelConsumption(3); form.setRefuelingSpeed(4);
        form.setInitialFuel(PickInitialFuel.DEFAULT); form.setRequiredPitStops(1);
        form.setTimeProgressionMode(TimeProgressionMode.MULTIPLIER); form.setTimeProgressionMultiplier(0);
        form.setWeather("Test weather"); form.setTimeOfDay("Sunset"); form.setLobbyRulesProfile(LobbyRulesProfile.CTC_2027);
        form.setStartType(RaceStartType.GRID_FALSE_START); form.setSlipstream(RaceSlipstream.REAL);
        return form;
    }
}
