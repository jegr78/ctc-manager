package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.ctc.domain.model.TimeProgressionMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PickPayloadReaderTest {
    final ObjectMapper mapper = new ObjectMapper();

    @Test
    void givenTypedPayload_whenReadingBackup_thenRetainOrderModeAndNone() throws Exception {
        // given / when
        var payload = PickPayloadReader.read(valid());
        // then
        assertThat(payload).isNotNull();
        assertThat(payload.weather()).isEqualTo("S01, R03, S02");
        assertThat(payload.timeProgressionMode()).isEqualTo(TimeProgressionMode.MULTIPLIER);
        assertThat(payload.timeProgressionMultiplier()).isZero();
        assertThat(payload.mandatoryTyres()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"laps-string", "laps-fraction", "weather-number", "mode-number", "tyres-string", "tyres-null", "none-with-set", "missing-field", "extra-field"})
    void givenMalformedPayload_whenReadingBackup_thenRejectCoercionOrInformationLoss(String invalid) throws Exception {
        // given
        var row = valid();
        switch (invalid) {
            case "laps-string" -> row.put("laps", "12");
            case "laps-fraction" -> row.put("laps", 12.5);
            case "weather-number" -> row.put("weather", 123);
            case "mode-number" -> row.put("timeProgressionMode", 0);
            case "tyres-string" -> row.put("availableTyres", "RS");
            case "tyres-null" -> row.putArray("availableTyres").addNull();
            case "none-with-set" -> row.putArray("mandatoryTyres").add("None").add("RS");
            case "missing-field" -> row.remove("fuelConsumption");
            case "extra-field" -> row.put("discardedSetting", "secret");
            default -> throw new AssertionError(invalid);
        }
        // when / then
        assertThatThrownBy(() -> PickPayloadReader.read(row)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("payload");
    }

    private ObjectNode valid() throws Exception {
        return (ObjectNode) mapper.readTree("""
                {"trackId":"%s","carId":"%s","carPoolEntryId":null,"laps":12,"tyreWear":2,"fuelConsumption":3,
                 "refuelingSpeed":4,"initialFuel":"DEFAULT","requiredPitStops":1,"timeProgressionMode":"MULTIPLIER",
                 "timeProgressionMultiplier":0,"weather":"S01, R03, S02","timeOfDay":"Sunset","availableTyres":["RS","RM"],"mandatoryTyres":[],"lobbyRulesProfile":"LEGACY","startType":"GRID_FALSE_START","slipstream":"REAL"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID()));
    }
}
