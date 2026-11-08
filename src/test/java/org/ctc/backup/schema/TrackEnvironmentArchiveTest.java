package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class TrackEnvironmentArchiveTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ObjectNode row() {
        var row = mapper.createObjectNode().put("rainCapability", "RAIN").put("environmentVersion", 3);
        row.putArray("timeOfDayOptions").add("Night").add("Sunrise"); return row;
    }
    @Test void givenCurrentMetadata_whenNormalizing_thenPreserveEveryValueAndOriginalInput() {
        var row = row(); var normalized = TrackEnvironmentArchive.normalize(19, "tracks", row);
        assertThat(normalized).isEqualTo(row); assertThat(normalized).isNotSameAs(row);
        assertThat(normalized.path("timeOfDayOptions").get(0).textValue()).isEqualTo("Night");
    }
    @ParameterizedTest @ValueSource(ints={1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18})
    void givenLegacyArchive_whenReading_thenUseUnknownAndRejectUndeclaredMetadata(int version) {
        var legacy = mapper.createObjectNode().put("name","Test layout");
        var normalized = TrackEnvironmentArchive.normalize(version, "tracks", legacy);
        assertThat(normalized.path("rainCapability").textValue()).isEqualTo("UNKNOWN");
        assertThat(normalized.path("timeOfDayOptions")).isEmpty();
        assertThat(normalized.path("environmentVersion").longValue()).isZero();
        assertThat(legacy.has("rainCapability")).isFalse();
        assertThatThrownBy(() -> TrackEnvironmentArchive.normalize(version,"tracks",row())).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings={"times", "rain", "version"})
    void givenOneUndeclaredLegacyField_whenNormalizing_thenRejectWithoutSilentlyDiscardingIt(String field) {
        var row = mapper.createObjectNode().put("rainCapability", "UNKNOWN").put("environmentVersion", 0);
        row.putArray("timeOfDayOptions");
        switch (field) {
            case "times" -> row.putArray("timeOfDayOptions").add("Night");
            case "rain" -> row.put("rainCapability", "RAIN");
            default -> row.put("environmentVersion", 1);
        }
        assertThatThrownBy(() -> TrackEnvironmentArchive.normalize(18, "tracks", row))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("schema 19");
    }
    @ParameterizedTest @ValueSource(strings={"missing", "rain", "version", "fraction", "duplicates", "options", "nontext", "overflow"})
    void givenInvalidCurrentArchive_whenPreflighting_thenRejectTypedMetadata(String change) {
        var row = row();
        switch(change) {
            case "missing" -> row.remove("rainCapability");
            case "rain" -> row.put("rainCapability","YES");
            case "version" -> row.put("environmentVersion",-1);
            case "fraction" -> row.put("environmentVersion",1.5);
            case "duplicates" -> row.putArray("timeOfDayOptions").add("Night").add("Night");
            case "nontext" -> row.putArray("timeOfDayOptions").add(8);
            case "overflow" -> row.put("environmentVersion", java.math.BigInteger.valueOf(Long.MAX_VALUE).add(java.math.BigInteger.ONE));
            default -> row.put("timeOfDayOptions","Night");
        }
        assertThatThrownBy(() -> TrackEnvironmentArchive.normalize(19,"tracks",row)).isInstanceOf(IllegalArgumentException.class);
    }
}
