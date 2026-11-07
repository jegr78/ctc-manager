package org.ctc.discord.picks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.ctc.domain.model.LobbyRulesProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class PickInteractionViewsTest {
    @ParameterizedTest
    @CsvSource({"wear,7,x", "consumption,6,x", "refuel,10,' L/s'"})
    void givenNewRules_whenOpening_thenRequireOneChoiceFromEachCompleteNumericRange(String id, int max, String unit) {
        // given / when
        var modal = PickInteractionViews.modal("test", "rules");
        var selection = (PickGatewayModal.Selection) modal.fields().stream()
                .filter(field -> field.id().equals(id)).findFirst().orElseThrow();
        // then
        assertThat(selection.minValues()).isEqualTo(1);
        assertThat(selection.maxValues()).isEqualTo(1);
        assertThat(selection.options()).extracting(PickGatewayModal.Option::value)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, max).mapToObj(Integer::toString).toList());
        assertThat(selection.options()).extracting(PickGatewayModal.Option::label)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, max).mapToObj(value -> value + unit).toList());
        assertThat(selection.options()).noneMatch(PickGatewayModal.Option::defaultSelected);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"@everyone", "<@300>", "000", "300\n@everyone"})
    void givenInvalidAuthorIdentifier_whenRenderingReceipt_thenUseNeutralTextWithoutInsertingMarkup(String author) {
        // given
        var receipt = new org.ctc.domain.picks.SubmissionReceipt(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                1, 1, java.time.Instant.parse("2026-11-06T13:46:21Z"), author);
        // when / then
        assertThat(PickInteractionViews.receipt(receipt)).contains("by an unknown team member").doesNotContain(author);
    }

    @Test
    void givenSavedReceipt_whenRenderingAuthor_thenUseAReadableDiscordReference() {
        // given
        var receipt = new org.ctc.domain.picks.SubmissionReceipt(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                1, 1, java.time.Instant.parse("2026-11-06T13:46:21Z"), "300");
        // when / then
        assertThat(PickInteractionViews.receipt(receipt)).contains("by <@300>")
                .doesNotContain("Discord user ");
    }

    @Test
    void givenRacingFuelDialog_whenOpening_thenOfferOnlyDryCompoundsBeforeChoosingMandatoryTyres() {
        // given / when
        var modal = PickInteractionViews.modal("test", "fuel");
        assertThat(modal.title()).isEqualTo("Private picks: race strategy");
        var available = (PickGatewayModal.Selection) modal.fields().stream()
                .filter(field -> field.id().equals("available")).findFirst().orElseThrow();
        // then
        assertThat(available.options()).extracting(PickGatewayModal.Option::value).containsExactly("RS", "RM", "RH");
        assertThat(modal.fields()).extracting(PickGatewayModal.Field::id).doesNotContain("mandatory");
    }

    @Test
    void given2027TimeStage_whenOpeningAndValidating_thenIncludeBothChoicesWithinFiveModalFields() {
        // given / when
        var modal = PickInteractionViews.modal("test", "time", LobbyRulesProfile.CTC_2027);
        // then
        assertThat(modal.fields()).hasSize(5);
        var values = new HashMap<>(valid("time"));
        values.put("start", List.of("ROLLING")); values.put("slipstream", List.of("WEAK"));
        assertThatCode(() -> PickInteractionViews.validateStage("time", values, LobbyRulesProfile.CTC_2027)).doesNotThrowAnyException();
        values.remove("start");
        assertThatThrownBy(() -> PickInteractionViews.validateStage("time", values, LobbyRulesProfile.CTC_2027)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"rules,laps,0", "rules,laps,2147483648", "rules,wear,8", "rules,consumption,7", "rules,refuel,11",
            "rules,laps,1.5", "fuel,initial,unknown", "fuel,stops,3", "time,progression,7", "time,weather,' '", "time,daytime,' '"})
    void givenInvalidField_whenValidatingStage_thenRejectBeforeSavingDraft(String stage, String field, String raw) {
        // given
        var values = new HashMap<>(valid(stage)); values.put(field, List.of(raw));
        // when / then
        assertThatThrownBy(() -> PickInteractionViews.validateStage(stage, values)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenBoundedCompleteTextAndTyres_whenValidating_thenRetainAllRequiredFields() {
        // given
        var time = new HashMap<>(valid("time")); time.put("weather", List.of("x".repeat(255)));
        // when / then
        assertThatCode(() -> PickInteractionViews.validateStage("time", time)).doesNotThrowAnyException();
        time.put("weather", List.of("x".repeat(256)));
        assertThatThrownBy(() -> PickInteractionViews.validateStage("time", time)).isInstanceOf(IllegalArgumentException.class);
        var tyres = new HashMap<>(valid("fuel")); tyres.put("available", List.of("RS", "RM", "RH"));
        assertThatCode(() -> PickInteractionViews.validateStage("fuel", tyres)).doesNotThrowAnyException();
        tyres.put("initial", List.of("FUEL_90"));
        assertThatCode(() -> PickInteractionViews.validateStage("fuel", tyres)).doesNotThrowAnyException();
        tyres.remove("initial");
        assertThatThrownBy(() -> PickInteractionViews.validateStage("fuel", tyres)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> PickInteractionViews.validateStage("mandatory", Map.of("mandatory", List.of("None")),
                LobbyRulesProfile.LEGACY, false, tyres)).doesNotThrowAnyException();
        assertThatCode(() -> PickInteractionViews.validateStage("mandatory", Map.of("mandatory", List.of("RS", "RH")),
                LobbyRulesProfile.LEGACY, false, tyres)).doesNotThrowAnyException();
        var multiple = new HashMap<>(valid("time")); multiple.put("weather", List.of("S01", "R03"));
        assertThatThrownBy(() -> PickInteractionViews.validateStage("time", multiple)).isInstanceOf(IllegalArgumentException.class);
        var empty = new HashMap<>(valid("fuel")); empty.put("available", List.of());
        assertThatThrownBy(() -> PickInteractionViews.validateStage("fuel", empty)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenLongPrivateText_whenRendering_thenSplitWithinDiscordLimitsAndRetainButtonsOnlyOnFinalMessage() {
        // given
        var response = new PickInteractionHandlerTest.Response();
        String text = "Test target\n" + "x".repeat(4100);
        var button = new PickGatewayMessage.Button("opaque", "Confirm");
        // when
        PickInteractionViews.send(response, text, List.of(button), null);
        // then
        assertThat(response.text).allSatisfy(part -> assertThat(part.length()).isLessThanOrEqualTo(1900));
        assertThat(response.last().content().length()).isLessThanOrEqualTo(1900);
        assertThat(response.last().buttons()).containsExactly(button);
        assertThat(String.join("", response.text) + response.last().content()).isEqualTo(text.replace("\n", ""));
        assertThat(PickInteractionViews.optionLabel("x".repeat(200))).hasSize(100);
    }

    @Test
    void givenInteractiveMessageLimits_whenBuilding_thenRejectOverlongContentAndTooManyControls() {
        // when / then
        assertThatThrownBy(() -> new PickGatewayMessage("x".repeat(2001), List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PickGatewayMessage("", List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PickGatewayMessage("Test", IntStream.range(0, 6).mapToObj(i -> new PickGatewayMessage.Button("id" + i, "Test")).toList(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PickGatewayMessage.Selection("id", "Test", List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PickGatewayMessage.Selection("id", "Test", IntStream.range(0, 26)
                .mapToObj(i -> new PickGatewayModal.Option("Test " + i, "id" + i)).toList())).isInstanceOf(IllegalArgumentException.class);
    }

    static Map<String, List<String>> valid(String stage) {
        return switch (stage) {
            case "rules" -> PickInteractionHandlerTest.fields("laps", "12", "wear", "2", "consumption", "3", "refuel", "4");
            case "fuel" -> PickInteractionHandlerTest.fields("initial", "DEFAULT", "stops", "1", "available", "RM");
            default -> PickInteractionHandlerTest.fields("progression", "0", "weather", "S01", "daytime", "Sunset");
        };
    }
}
