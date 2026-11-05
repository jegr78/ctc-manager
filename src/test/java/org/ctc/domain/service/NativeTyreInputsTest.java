package org.ctc.domain.service;

import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class NativeTyreInputsTest {
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
            assertThat(resolved.available()).isEqualTo("RS");
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
