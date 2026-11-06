package org.ctc.domain.service;

import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class NativeTyreInputsTest {
    @Test
    void givenNewRacingHardSelection_whenResolvingNativeInput_thenIncludeBothAutomaticWetCompounds() {
        // given / when
        var resolved = NativeTyreInputs.resolve(new Race(), null, null, LobbyRulesProfile.LEGACY, "RH", "RH");
        // then
        assertThat(resolved.available()).isEqualTo("RH, I, W");
        assertThat(resolved.mandatory()).isEqualTo("RH");
    }

    @Test
    void givenMalformedMixedFamilyHistory_whenExplicitlyRepairingToSports_thenDoNotPreserveRacingWetCompounds() {
        // given
        var race = new Race(); var settings = new RaceSettings(race); race.setSettings(settings);
        settings.setAvailableTyres("SS, I, W"); settings.setMandatoryTyres("None");
        // when
        var resolved = NativeTyreInputs.resolve(race, null, null, LobbyRulesProfile.CTC_2027, "SS", "None");
        // then
        assertThat(resolved.available()).isEqualTo("SS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"available", "mandatory"})
    void givenMalformedSavedText_whenReplacingItWithValidCompounds_thenAllowTheExplicitRepair(String malformed) {
        // given
        var race = new Race(); var settings = new RaceSettings(race); race.setSettings(settings);
        settings.setAvailableTyres(malformed.equals("available") ? "RS,RS" : "RS");
        settings.setMandatoryTyres(malformed.equals("mandatory") ? "RS,RS" : "None");
        // when / then
        assertThatCode(() -> {
            var resolved = NativeTyreInputs.resolve(race, null, null, LobbyRulesProfile.LEGACY, "RS", "None");
            assertThat(resolved.available()).isEqualTo(malformed.equals("available") ? "RS, I, W" : "RS");
            assertThat(resolved.mandatory()).isEqualTo("None");
        }).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"car", "entry"})
    void givenUnclassifiedHistoricalText_whenChangingItsSelectionContext_thenRequireARecognizedSelection(String changed) {
        // given
        var race = new Race(); var settings = new RaceSettings(race); race.setSettings(settings);
        settings.setAvailableTyres("Test saved unknown wording"); settings.setMandatoryTyres("None");
        var car = new Car(); car.setId(UUID.randomUUID()); race.setCar(car);
        var entry = new SeasonCarPoolEntry(); entry.setId(UUID.randomUUID()); race.setCarPoolEntry(entry);
        UUID entryId = changed.equals("entry") ? UUID.randomUUID() : entry.getId();
        UUID carId = changed.equals("car") ? UUID.randomUUID() : car.getId();
        // when / then
        assertThatThrownBy(() -> NativeTyreInputs.resolve(race, entryId, carId, LobbyRulesProfile.CTC_2027,
                "Test saved unknown wording", "None")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("tyre");
    }
}
