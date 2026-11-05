package org.ctc.discord.picks;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.LobbyRulesProfile;
import org.ctc.domain.model.PickTyre;
import org.ctc.domain.picks.PickTeamView.Choice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class PickTyreDialogTest {
    @ParameterizedTest
    @CsvSource({"CTC_2027,true,8,7", "CTC_2027,false,5,4", "LEGACY,true,5,4"})
    void givenSavedEntryAndProfile_whenOpeningFuelModal_thenOfferPermittedFamiliesAndOnlyDryMandatory(
            LobbyRulesProfile profile, boolean oneMake, int availableCount, int mandatoryCount) {
        // given / when
        var modal = PickInteractionViews.modal("test", "fuel", profile, oneMake);
        var available = selection(modal, "available"); var mandatory = selection(modal, "mandatory");
        // then
        assertThat(modal.fields()).hasSize(4);
        assertThat(available.options()).hasSize(availableCount);
        assertThat(available.maxValues()).isEqualTo(5);
        assertThat(mandatory.options()).hasSize(mandatoryCount);
        assertThat(mandatory.options()).extracting(PickGatewayModal.Option::value).doesNotContain("I", "W");
        assertThat(mandatory.maxValues()).isEqualTo(3);
        if (profile == LobbyRulesProfile.CTC_2027 && oneMake) {
            assertThat(available.options()).extracting(PickGatewayModal.Option::value).contains("SS", "SM", "SH");
        } else assertThat(available.options()).extracting(PickGatewayModal.Option::value).doesNotContain("SS", "SM", "SH");
    }

    @Test
    void givenFiveRacingCompounds_whenValidatingFuel_thenAcceptWetAvailabilityWithDryMandatory() {
        // given / when / then
        assertThatCode(() -> PickInteractionViews.validateStage("fuel", fuel("RS,RM,RH,I,W", "RM"), LobbyRulesProfile.CTC_2027, false))
                .doesNotThrowAnyException();
    }

    @Test
    void givenSaved2027OneMake_whenValidatingSports_thenAcceptItsSingleFamily() {
        // given / when / then
        assertThatCode(() -> PickInteractionViews.validateStage("fuel", fuel("SS,SM,SH", "SM"), LobbyRulesProfile.CTC_2027, true))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"'RS,I',I,CTC_2027,true", "'RS,W',W,CTC_2027,true", "'RS,SS',None,CTC_2027,true",
            "SS,None,CTC_2027,false", "SS,None,LEGACY,true", "'SS,SM',RS,CTC_2027,true", "'RS,RS',None,CTC_2027,true"})
    void givenInvalidOrUnpermittedTyres_whenValidatingFuel_thenRejectBeforeAdvancingTheDraft(
            String available, String mandatory, LobbyRulesProfile profile, boolean oneMake) {
        // given / when / then
        assertThatThrownBy(() -> PickInteractionViews.validateStage("fuel", fuel(available, mandatory), profile, oneMake))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenSportsFieldsAndACategoryChoice_whenAssemblingFinalPayload_thenRequireAnExplicitOneMake() {
        // given / when / then
        assertThatThrownBy(() -> PickInteractionViews.payload(session(false))).isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("One-Make");
    }

    @Test
    void givenSportsFieldsAndASavedOneMakeChoice_whenAssembling_thenRetainEveryCompound() {
        // given / when
        var payload = PickInteractionViews.payload(session(true));
        // then
        assertThat(payload.availableTyres()).containsExactlyInAnyOrder(PickTyre.SS, PickTyre.SM, PickTyre.SH);
        assertThat(payload.mandatoryTyres()).containsExactly(PickTyre.SM);
    }

    private static PickGatewayModal.Selection selection(PickGatewayModal modal, String field) {
        return (PickGatewayModal.Selection) modal.fields().stream().filter(value -> value.id().equals(field)).findFirst().orElseThrow();
    }

    private static Map<String, List<String>> fuel(String available, String mandatory) {
        var values = new HashMap<>(PickInteractionViewsTest.valid("fuel"));
        values.put("available", Arrays.asList(available.split(","))); values.put("mandatory", Arrays.asList(mandatory.split(",")));
        return values;
    }

    private static PickDraftSessionStore.Session session(boolean oneMake) {
        var session = new PickDraftSessionStore.Session("100", "200", "300", 0, Instant.now().plusSeconds(60));
        session.lobbyRulesProfile = LobbyRulesProfile.CTC_2027;
        session.car = new Choice(UUID.randomUUID(), UUID.randomUUID(), "Test saved entry", oneMake);
        session.track = new Choice(UUID.randomUUID(), null, "Test track");
        session.fields.putAll(PickInteractionViewsTest.valid("rules"));
        session.fields.putAll(fuel("SS,SM,SH", "SM"));
        session.fields.putAll(PickInteractionViewsTest.valid("time"));
        session.fields.put("start", List.of("GRID_FALSE_START")); session.fields.put("slipstream", List.of("REAL"));
        return session;
    }
}
