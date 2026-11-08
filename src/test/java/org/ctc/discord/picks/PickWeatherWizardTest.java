package org.ctc.discord.picks;

import java.time.Instant;
import java.util.*;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.PickTeamView.Choice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.ctc.discord.picks.PickGatewayInteraction.Kind.*;

class PickWeatherWizardTest {
    final PickDraftSessionStore.Session session = new PickDraftSessionStore.Session("guild", "channel", "user", 0, Instant.MAX);
    final Choice rain = new Choice(UUID.randomUUID(), null, "Test rain layout", false, null, List.of(), TrackRainCapability.RAIN);
    final List<PickGatewayMessage> messages = new ArrayList<>();
    final PickGatewayResponse.Continuation reply = new PickGatewayResponse.Continuation() {
        public void replyPrivate(String text) { throw new AssertionError(text); }
        public void replyPrivate(PickGatewayMessage message) { messages.add(message); }
    };

    @Test
    void givenPresetMode_whenSelectingOnePagedCode_thenFinishWithExactlyOneExistingPreset() {
        // given
        PickWeatherWizard.start(session, rain, reply);
        assertThat(last().selection().options()).extracting(PickGatewayModal.Option::value).containsExactly("PRESET", "CUSTOM");
        // when
        assertThat(select("PRESET", rain)).isFalse();
        assertThat(last().selection().options()).hasSize(25).noneMatch(o -> o.value().equals("?"));
        assertThat(button("Next", rain)).isFalse();
        assertThat(select("R08", rain)).isTrue();
        // then
        assertThat(session.fields.get("weather")).containsExactly("Preset R08");
    }

    @ParameterizedTest @ValueSource(ints = {6, 7, 8})
    void givenCustomMode_whenFillingEachSlotWithRepeatsAndRandom_thenRetainEveryPositionAndFinishOnlyAtTheLast(int count) {
        // given
        session.fields.put("laps", List.of("22"));
        PickWeatherWizard.start(session, rain, reply); select("CUSTOM", rain);
        assertThat(last().selection().options()).extracting(PickGatewayModal.Option::value).containsExactly("6", "7", "8");
        select(Integer.toString(count), rain);
        // when / then
        var expected = new ArrayList<String>();
        for (int slot = 0; slot < count; slot++) {
            assertThat(last().content()).contains("Slot " + (slot + 1) + " of " + count);
            assertThat(last().selection().options()).hasSize(25);
            String code = slot % 2 == 0 ? "?" : "S01"; expected.add(code);
            assertThat(select(code, rain)).isEqualTo(slot == count - 1);
        }
        assertThat(session.fields.get("weather")).containsExactly("Custom " + String.join("; ", expected));
        assertThat(session.fields.get("laps")).containsExactly("22");
    }

    @Test
    void givenSavedCustomWeather_whenKeepingDefaultsAndEditingOneSlot_thenPreserveAllOtherValues() {
        // given
        session.fields.putAll(PickWeatherWizard.defaults(new PickWeatherSelection(PickWeatherSelection.Mode.CUSTOM,
                List.of("S02", "S02", "?", "R03", "R03", "C06", "S01", "?")).encode()));
        session.fields.put("laps", List.of("34"));
        PickWeatherWizard.start(session, rain, reply);
        assertThat(last().selection().options()).filteredOn(PickGatewayModal.Option::defaultSelected)
                .extracting(PickGatewayModal.Option::value).containsExactly("CUSTOM");
        assertThat(button("Keep selected weather", rain)).isTrue();
        // when
        messages.add(new PickGatewayMessage("Choose a slot", List.of(), PickWeatherWizard.editSlots(session)));
        select("3", rain);
        assertThat(last().selection().options()).filteredOn(PickGatewayModal.Option::defaultSelected)
                .extracting(PickGatewayModal.Option::value).containsExactly("R03");
        button("Previous", rain);
        assertThat(select("S05", rain)).isTrue();
        // then
        assertThat(session.fields.get("weather")).containsExactly("Custom S02; S02; ?; S05; R03; C06; S01; ?");
        assertThat(session.fields.get("laps")).containsExactly("34");
    }

    @Test
    void givenSavedPreset_whenSteppingThroughItsDefault_thenShowItsPageAndAllowExplicitKeep() {
        // given
        session.fields.putAll(PickWeatherWizard.defaults("Preset R08"));
        PickWeatherWizard.start(session, rain, reply);
        // when
        select("PRESET", rain);
        // then
        assertThat(last().content()).contains("Page 2/2");
        assertThat(last().selection().options()).filteredOn(PickGatewayModal.Option::defaultSelected)
                .extracting(PickGatewayModal.Option::value).containsExactly("R08");
        assertThat(button("Keep selected slot", rain)).isTrue();
    }

    @Test
    void givenSavedCustom_whenChoosingItsCount_thenAllowKeepingEachSlotIncludingRepeatedCodes() {
        // given
        session.fields.putAll(PickWeatherWizard.defaults("Custom S01; S01; S01; S01; S01; S01"));
        PickWeatherWizard.start(session, rain, reply); select("CUSTOM", rain);
        assertThat(last().selection().options()).filteredOn(PickGatewayModal.Option::defaultSelected)
                .extracting(PickGatewayModal.Option::value).containsExactly("6");
        select("6", rain);
        // when / then
        for (int slot = 0; slot < 6; slot++) assertThat(button("Keep selected slot", rain)).isEqualTo(slot == 5);
        assertThat(session.fields.get("weather")).containsExactly("Custom S01; S01; S01; S01; S01; S01");
    }

    @Test
    void givenRainOrUnknownLayout_whenForgingOffPageUnknownOrMultiSelections_thenRejectWithoutCompleting() {
        // given
        PickWeatherWizard.start(session, rain, reply);
        var mode = action(last().selection().id());
        assertThatThrownBy(() -> PickWeatherWizard.handle(session, mode, Map.of("selection", List.of("UNKNOWN")), rain, reply))
                .isInstanceOf(BusinessRuleException.class);
        select("CUSTOM", rain); var count = action(last().selection().id());
        for (String value : List.of("0", "5", "9", "6.0")) assertThatThrownBy(() ->
                PickWeatherWizard.handle(session, count, Map.of("selection", List.of(value)), rain, reply)).isInstanceOf(BusinessRuleException.class);
        select("6", rain); var firstPage = action(last().selection().id());
        // when / then
        for (var values : List.of(Map.of("selection", List.of("R01")), Map.of("selection", List.of("S00")),
                Map.of("selection", List.of("S01", "S02")), Map.of("selection", List.of("S01"), "extra", List.of("S02")))) {
            assertThatThrownBy(() -> PickWeatherWizard.handle(session, firstPage, values, rain, reply)).isInstanceOf(BusinessRuleException.class);
        }
        assertThat(session.fields).doesNotContainKey("weather");
        button("Next", rain); var rainPage = action(last().selection().id());
        var unknown = new Choice(rain.id(), null, rain.label());
        assertThatThrownBy(() -> PickWeatherWizard.handle(session, rainPage, Map.of("selection", List.of("R01")), unknown, reply))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenLegacyFreeText_whenMappingDefaults_thenKeepItUnchangedAndRequireExplicitNewWeather() {
        // given / when / then
        assertThat(PickWeatherWizard.defaults("Slot 1: S01; Slot 2: R03")).isEmpty();
        assertThat(PickWeatherWizard.defaults("S01")).containsEntry("weather-mode", List.of("PRESET"));
        PickWeatherWizard.start(session, new Choice(rain.id(), null, rain.label()), reply);
        assertThat(last().buttons()).isEmpty();
        select("PRESET", new Choice(rain.id(), null, rain.label()));
        assertThat(last().selection().options()).hasSize(24);
        assertThat(last().buttons()).isEmpty();
    }

    PickGatewayMessage last() { return messages.getLast(); }
    PickDraftSessionStore.Action action(String id) { return session.actions.get(id.substring(id.lastIndexOf(':') + 1)); }
    boolean select(String value, Choice layout) {
        return PickWeatherWizard.handle(session, action(last().selection().id()), Map.of("selection", List.of(value)), layout, reply);
    }
    boolean button(String label, Choice layout) {
        var button = last().buttons().stream().filter(b -> b.label().equals(label)).findFirst().orElseThrow();
        return PickWeatherWizard.handle(session, action(button.id()), Map.of(), layout, reply);
    }
}
