package org.ctc.discord.picks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

class PickInteractionViewsTest {
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
        var tyres = new HashMap<>(valid("fuel")); tyres.put("available", List.of("RS", "RM", "RH")); tyres.put("mandatory", List.of("RS", "RH"));
        assertThatCode(() -> PickInteractionViews.validateStage("fuel", tyres)).doesNotThrowAnyException();
        tyres.put("initial", List.of("FUEL_90"));
        assertThatCode(() -> PickInteractionViews.validateStage("fuel", tyres)).doesNotThrowAnyException();
        tyres.remove("initial");
        assertThatThrownBy(() -> PickInteractionViews.validateStage("fuel", tyres)).isInstanceOf(IllegalArgumentException.class);
        var none = new HashMap<>(valid("fuel")); none.put("mandatory", List.of("None"));
        assertThatCode(() -> PickInteractionViews.validateStage("fuel", none)).doesNotThrowAnyException();
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
            case "fuel" -> PickInteractionHandlerTest.fields("initial", "DEFAULT", "stops", "1", "available", "RM", "mandatory", "RM");
            default -> PickInteractionHandlerTest.fields("progression", "0", "weather", "S01", "daytime", "Sunset");
        };
    }
}
