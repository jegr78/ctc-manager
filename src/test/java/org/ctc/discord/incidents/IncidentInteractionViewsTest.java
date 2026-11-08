package org.ctc.discord.incidents;

import java.util.List;
import java.util.Map;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.IncidentPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class IncidentInteractionViewsTest {
    @Test
    void givenLongStoredFields_whenLeavingTheirOptionalInputsEmpty_thenRetainTheExactStoredText() {
        // given
        var stored = new IncidentPayload("Test driver", "00:00", "🚗".repeat(10000), "x".repeat(8000));
        // when
        var result = IncidentInteractionViews.payload(Map.of("drivers", List.of("Test updated driver"), "replay", List.of("lap 3")), stored);
        // then
        assertThat(result.getDescription()).isEqualTo(stored.getDescription());
        assertThat(result.getEvidenceLinks()).isEqualTo(stored.getEvidenceLinks());
        assertThat(result.getInvolvedDrivers()).isEqualTo("Test updated driver");
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty-description", "missing-description", "whitespace", "oversized-input", "extra-field", "multiple-values"})
    void givenInvalidReplacementFields_whenParsing_thenRejectWithoutReusingOrdinaryShortFields(String kind) {
        // given
        var saved = new IncidentPayload("Test driver", "00:00", "Test saved description", "Test saved footage");
        var fields = new java.util.HashMap<>(IncidentInteractionHandlerTest.fields("Test driver", "00:00", "Test replacement", ""));
        switch (kind) {
            case "empty-description" -> fields.put("description", List.of(""));
            case "missing-description" -> fields.remove("description");
            case "whitespace" -> fields.put("description", List.of(" "));
            case "oversized-input" -> fields.put("description", List.of("x".repeat(4001)));
            case "extra-field" -> fields.put("reportId", List.of("another report"));
            case "multiple-values" -> fields.put("description", List.of("Test one", "Test two"));
            default -> throw new AssertionError();
        }
        // when / then
        assertThatThrownBy(() -> IncidentInteractionViews.payload(fields, saved)).isInstanceOf(BusinessRuleException.class);
        assertThat(saved.getDescription()).isEqualTo("Test saved description");
    }

    @Test
    void givenOrdinaryOptionalFootage_whenSubmittingAnEmptyValue_thenClearItInTheDraft() {
        // given
        var saved = new IncidentPayload("Test driver", "00:00", "Test saved description", "Test old footage");
        // when
        var changed = IncidentInteractionViews.payload(IncidentInteractionHandlerTest.fields("Test driver", "00:00", "Test saved description", ""), saved);
        // then
        assertThat(changed.getEvidenceLinks()).isEmpty();
        assertThat(saved.getEvidenceLinks()).isEqualTo("Test old footage");
    }
    @ParameterizedTest
    @ValueSource(strings = {"unknown", "duplicate", "free-text", "extra-field", "empty"})
    void givenForgedOrEmptyDriverSelection_whenParsing_thenRejectIt(String kind) {
        var choices = IncidentJdaModalContractTest.choices("Test driver");
        var fields = new java.util.HashMap<>(IncidentInteractionHandlerTest.fields(IncidentJdaModalContractTest.DRIVER.toString(), "00:00", "Test description", ""));
        switch (kind) {
            case "unknown" -> fields.put("drivers", List.of(java.util.UUID.randomUUID().toString()));
            case "duplicate" -> fields.put("drivers", List.of(IncidentJdaModalContractTest.DRIVER.toString(), IncidentJdaModalContractTest.DRIVER.toString()));
            case "free-text" -> fields.put("drivers", List.of("Test driver"));
            case "extra-field" -> fields.put("team", List.of("Test other"));
            case "empty" -> fields.put("drivers", List.of());
            default -> throw new AssertionError();
        }
        assertThatThrownBy(() -> IncidentInteractionViews.submitted(fields, null, choices)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenSplitDriverMenus_whenSelectingBothTeams_thenRequireMembershipOfEachMenuAndAtLeastOneDriver() {
        var drivers = java.util.stream.IntStream.range(0, 26).mapToObj(index ->
                new org.ctc.domain.incidents.IncidentDriverChoices.DriverChoice(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "Test driver " + index, index < 13)).toList();
        var choices = new org.ctc.domain.incidents.IncidentDriverChoices(java.util.UUID.randomUUID(), "f".repeat(64), drivers);
        var fields = new java.util.HashMap<String, List<String>>(Map.of("replay", List.of("00:00"), "description", List.of("Test description"),
                "drivers-home", List.of(drivers.getFirst().id().toString()), "drivers-away", List.of(drivers.getLast().id().toString())));
        var result = IncidentInteractionViews.submitted(fields, null, choices);
        assertThat(result.payload().getInvolvedDrivers()).isEqualTo("Test driver 0\nTest driver 25");
        fields.put("drivers-home", List.of(drivers.getLast().id().toString()));
        assertThatThrownBy(() -> IncidentInteractionViews.submitted(fields, null, choices)).isInstanceOf(BusinessRuleException.class);
        fields.put("drivers-home", List.of()); fields.put("drivers-away", List.of());
        assertThatThrownBy(() -> IncidentInteractionViews.submitted(fields, null, choices)).hasMessageContaining("at least one");
    }
}
