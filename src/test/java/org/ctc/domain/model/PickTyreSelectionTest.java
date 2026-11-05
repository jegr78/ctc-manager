package org.ctc.domain.model;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class PickTyreSelectionTest {
    @Test
    void givenOriginalRacingCompounds_whenConverting_thenPreserveCanonicalOrderAndText() {
        // given
        var payload = payload(LobbyRulesProfile.LEGACY, "RH,RS,RM", "RM");
        var converter = new PickTyreSetConverter();
        // when / then
        assertThat(converter.convertToDatabaseColumn(payload.availableTyres())).isEqualTo("RS,RM,RH");
        assertThat(RaceSettingsValues.from(payload).availableTyres()).isEqualTo("RS, RM, RH");
        assertThat(RaceSettingsValues.from(payload).mandatoryTyres()).isEqualTo("RM");
    }

    @Test
    void givenAllRacingCompounds_whenChoosingWetAvailabilityAndNoMandatory_thenRetainEveryCompound() {
        // given / when / then
        assertThatCode(() -> {
            var payload = payload(LobbyRulesProfile.CTC_2027, "RS,RM,RH,I,W", "");
            var converter = new PickTyreSetConverter();
            assertThat(converter.convertToDatabaseColumn(payload.availableTyres())).isEqualTo("RS,RM,RH,I,W").hasSize(12);
            assertThat(converter.convertToEntityAttribute("RS,RM,RH,I,W")).isEqualTo(payload.availableTyres());
            assertThat(RaceSettingsValues.from(payload).mandatoryTyres()).isEqualTo("None");
        }).doesNotThrowAnyException();
    }

    @Test
    void givenSportsCompounds_whenConstructingA2027Payload_thenRetainOneSportsFamily() {
        // given / when / then
        assertThatCode(() -> {
            var payload = payload(LobbyRulesProfile.CTC_2027, "SS,SM,SH", "SM");
            assertThat(new PickTyreSetConverter().convertToDatabaseColumn(payload.availableTyres())).isEqualTo("SS,SM,SH");
            assertThat(RaceSettingsValues.from(payload).availableTyres()).isEqualTo("SS, SM, SH");
            assertThat(RaceSettingsValues.from(payload).mandatoryTyres()).isEqualTo("SM");
        }).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"I", "W"})
    void givenAnAvailableWetCompound_whenMakingItMandatory_thenRejectWithDomainGuidance(String wet) {
        // given / when / then
        assertThatThrownBy(() -> payload(LobbyRulesProfile.CTC_2027, "RS,RM,RH,I,W", wet))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("mandatory");
    }

    @Test
    void givenBothFamilies_whenChoosingAvailableCompounds_thenRejectTheMixture() {
        // given / when / then
        assertThatThrownBy(() -> payload(LobbyRulesProfile.CTC_2027, "RS,SS", ""))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("family");
    }

    @Test
    void givenSportsAvailability_whenChoosingARacingMandatoryCompound_thenReject() {
        // given / when / then
        assertThatThrownBy(() -> payload(LobbyRulesProfile.CTC_2027, "SS,SM,SH", "RS"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenPreviousSeasonRules_whenChoosingSports_thenPreserveRacingOnlyPolicy() {
        // given / when / then
        assertThatThrownBy(() -> payload(LobbyRulesProfile.LEGACY, "SS", ""))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("season");
    }

    @Test
    void givenAMandatoryOnlyPublicationProjection_whenReading_thenDoNotInventAvailability() {
        // given / when
        var values = new RaceSettingsValues(null, null, null, null, null, null, null, null,
                null, null, null, "SM", null, null);
        // then
        assertThat(values.availableTyres()).isNull();
        assertThat(values.mandatoryTyres()).isEqualTo("SM");
    }

    private static PickPayload payload(LobbyRulesProfile profile, String available, String mandatory) {
        return new PickPayload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 12, 2, 3, 4,
                PickInitialFuel.DEFAULT, 1, TimeProgressionMode.MULTIPLIER, 0, "Test weather", "Sunset",
                tyres(available), tyres(mandatory), profile, RaceStartType.GRID_FALSE_START, RaceSlipstream.REAL);
    }

    private static Set<PickTyre> tyres(String codes) {
        return codes.isEmpty() ? Set.of() : Arrays.stream(codes.split(",")).map(PickTyre::valueOf).collect(Collectors.toSet());
    }
}
