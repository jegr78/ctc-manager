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
    @CsvSource({"CTC_2027,true", "CTC_2027,false", "LEGACY,true"})
    void givenSavedEntryAndProfile_whenChoosingFamily_thenOfferSportsOnlyFor2027OneMake(
            LobbyRulesProfile profile, boolean oneMake) {
        // given / when
        var rules = PickInteractionViews.modal("test", "rules", profile, oneMake);
        var fuel = PickInteractionViews.modal("test", "fuel", profile, oneMake);
        // then
        assertThat(rules.fields()).hasSize(profile == LobbyRulesProfile.CTC_2027 && oneMake ? 5 : 4);
        assertThat(fuel.fields()).hasSize(3);
        assertThat(selection(fuel, "available").options()).extracting(PickGatewayModal.Option::value)
                .containsExactly("RS", "RM", "RH");
        assertThat(selection(fuel, "available").maxValues()).isEqualTo(3);
        if (profile == LobbyRulesProfile.CTC_2027 && oneMake) {
            assertThat(selection(rules, "family").options()).extracting(PickGatewayModal.Option::value)
                    .containsExactly("RACING", "SPORTS");
        } else assertThat(rules.fields()).extracting(PickGatewayModal.Field::id).doesNotContain("family");
    }

    @ParameterizedTest
    @CsvSource({"RACING,'RS,RH',RH", "SPORTS,'SS,SH',SH"})
    void givenFamilyAndAvailableDryCompounds_whenOpeningMandatory_thenOfferOnlyTheAvailableSubset(
            String family, String available, String mandatory) {
        // given
        var context = fuel(available);
        context.put("family", List.of(family)); context.put("mandatory", List.of(mandatory));
        // when
        var modal = PickInteractionViews.modal("test", "mandatory", LobbyRulesProfile.CTC_2027, true, context);
        // then
        assertThat(selection(modal, "mandatory").options()).extracting(PickGatewayModal.Option::value)
                .containsExactly("None", available.split(",")[0], available.split(",")[1]);
        assertThat(selection(modal, "mandatory").options().stream().filter(PickGatewayModal.Option::defaultSelected))
                .extracting(PickGatewayModal.Option::value).containsExactly(mandatory);
        assertThatCode(() -> PickInteractionViews.validateStage("fuel", fuel(available), LobbyRulesProfile.CTC_2027, true, context))
                .doesNotThrowAnyException();
        assertThatCode(() -> PickInteractionViews.validateStage("mandatory", Map.of("mandatory", List.of(mandatory)),
                LobbyRulesProfile.CTC_2027, true, context)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"RACING,SS", "SPORTS,RS"})
    void givenEligibleOneMakeButForgedCompoundFamily_whenSubmittingFuel_thenRequireThePreviouslyChosenFamily(String family, String available) {
        // given / when / then
        assertThatThrownBy(() -> PickInteractionViews.validateStage("fuel", fuel(available), LobbyRulesProfile.CTC_2027,
                true, Map.of("family", List.of(family)))).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"'RS,I',CTC_2027,true", "'RS,W',CTC_2027,true", "'RS,SS',CTC_2027,true",
            "SS,CTC_2027,false", "SS,LEGACY,true", "'RS,RS',CTC_2027,true"})
    void givenInvalidOrUnpermittedTyres_whenValidatingFuel_thenRejectBeforeAdvancingTheDraft(
            String available, LobbyRulesProfile profile, boolean oneMake) {
        // given / when / then
        assertThatThrownBy(() -> PickInteractionViews.validateStage("fuel", fuel(available), profile, oneMake))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"'RS,RH',I", "'RS,RH',W", "'RS,RH',SS", "'RS,RH',RM", "'RS,RH','None,RS'", "'RS,RH','RS,RS'"})
    void givenForgedMandatorySelection_whenValidating_thenRejectWetOtherFamilyMissingOrDuplicateCompounds(
            String available, String mandatory) {
        // given / when / then
        assertThatThrownBy(() -> PickInteractionViews.validateStage("mandatory",
                Map.of("mandatory", Arrays.asList(mandatory.split(","))), LobbyRulesProfile.CTC_2027, true, fuel(available)))
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

    private static Map<String, List<String>> fuel(String available) {
        var values = new HashMap<>(PickInteractionViewsTest.valid("fuel"));
        values.put("available", Arrays.asList(available.split(",")));
        return values;
    }

    private static PickDraftSessionStore.Session session(boolean oneMake) {
        var session = new PickDraftSessionStore.Session("100", "200", "300", 0, Instant.now().plusSeconds(60));
        session.lobbyRulesProfile = LobbyRulesProfile.CTC_2027;
        session.car = new Choice(UUID.randomUUID(), UUID.randomUUID(), "Test saved entry", oneMake);
        session.track = new Choice(UUID.randomUUID(), null, "Test track");
        session.fields.putAll(PickInteractionViewsTest.valid("rules"));
        session.fields.putAll(fuel("SS,SM,SH"));
        session.fields.put("family", List.of("SPORTS")); session.fields.put("mandatory", List.of("SM"));
        session.fields.putAll(PickInteractionViewsTest.valid("time"));
        session.fields.put("weather", List.of("Preset S01"));
        session.fields.put("start", List.of("GRID_FALSE_START")); session.fields.put("slipstream", List.of("REAL"));
        return session;
    }
}
