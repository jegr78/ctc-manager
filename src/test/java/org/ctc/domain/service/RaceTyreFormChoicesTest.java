package org.ctc.domain.service;

import org.ctc.domain.model.LobbyRulesProfile;
import org.ctc.domain.model.PickTyre;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RaceTyreFormChoicesTest {
    @Test
    void givenRacingHardAndAutomaticWetTyres_whenOpeningNativeForm_thenSelectOnlyHardAndOfferOnlyHardAsMandatory() {
        // given / when
        var choices = RaceTyreFormChoices.from(LobbyRulesProfile.LEGACY, null, null, "RH, I, W", "RH");
        // then
        assertThat(choices.available()).containsExactly(PickTyre.RS, PickTyre.RM, PickTyre.RH);
        assertThat(choices.selectedAvailable()).containsExactly("RH");
        assertThat(choices.mandatory()).containsExactly(PickTyre.RH);
        assertThat(choices.retainedAvailable()).isNull();
    }

    @Test
    void givenHistoricalWetOnlyText_whenOpeningNativeForm_thenRetainItExplicitlyInsteadOfRenderingAnEmptySelection() {
        // given / when
        var choices = RaceTyreFormChoices.from(LobbyRulesProfile.LEGACY, null, null, "I, W", "None");
        // then
        assertThat(choices.selectedAvailable()).isEmpty();
        assertThat(choices.retainedAvailable()).isEqualTo("I, W");
    }

    @Test
    void givenNoAvailableSelection_whenOpeningNativeForm_thenOfferNoMandatoryCompounds() {
        // given / when
        var choices = RaceTyreFormChoices.from(LobbyRulesProfile.LEGACY, null, null, null, null);
        // then
        assertThat(choices.mandatory()).isEmpty();
        assertThat(choices.selectedMandatory()).containsExactly("None");
    }
}
