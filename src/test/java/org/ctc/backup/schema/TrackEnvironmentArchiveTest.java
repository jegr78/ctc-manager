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
        row.putArray("timeOfDayOptions").add("Night").add("Sunrise");
        row.putNull("environmentSource"); row.putNull("environmentImportHash"); row.put("environmentOverride",true); return row;
    }
    @Test void givenCurrentMetadata_whenNormalizing_thenPreserveEveryValueAndOriginalInput() {
        var row = row(); var normalized = TrackEnvironmentArchive.normalize(20, "tracks", row);
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
        assertThatThrownBy(() -> TrackEnvironmentArchive.normalize(20,"tracks",row)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void givenSchema19Environment_whenUpgrading_thenProtectExistingManualSettingsAndLeavePristineEligible() {
        var legacy=row();legacy.remove(java.util.List.of("environmentSource","environmentImportHash","environmentOverride"));
        assertThat(TrackEnvironmentArchive.normalize(19,"tracks",legacy).path("environmentOverride").booleanValue()).isTrue();
        legacy.putArray("timeOfDayOptions");legacy.put("rainCapability","UNKNOWN");legacy.put("environmentVersion",0);
        assertThat(TrackEnvironmentArchive.normalize(19,"tracks",legacy).path("environmentOverride").booleanValue()).isFalse();
        legacy.put("environmentSource","undeclared");
        assertThatThrownBy(() -> TrackEnvironmentArchive.normalize(19,"tracks",legacy)).isInstanceOf(IllegalArgumentException.class);
    }
    @org.junit.jupiter.params.ParameterizedTest @ValueSource(strings={"missing-override","string-override","source-object","source-null-json","hash-only","wrong-hash","coerced-profile"})
    void givenSchema20MalformedSource_whenPreflighting_thenRejectBeforeAnyRestore(String kind) {
        var row=row();
        switch(kind) {
            case "missing-override" -> row.remove("environmentOverride");
            case "string-override" -> row.put("environmentOverride","true");
            case "source-object" -> row.putObject("environmentSource");
            case "source-null-json" -> row.put("environmentSource","null");
            case "hash-only" -> row.put("environmentImportHash","a".repeat(64));
            case "coerced-profile" -> row.put("environmentSource","{\"profile\":123,\"sourceEditedAt\":\"2026-07-08T01:07:32Z\",\"retrievedAt\":\"2026-11-08T12:00:00Z\",\"sourceHash\":\""+"a".repeat(64)+"\"}");
            default -> {
                var source=new org.ctc.domain.model.TrackEnvironmentProvenance("Test source","2026-07-08T01:07:32Z","2026-11-08T12:00:00Z","a".repeat(64));
                row.put("environmentSource",source.encode());row.put("environmentImportHash","b".repeat(64));row.put("environmentOverride",false);
            }
        }
        assertThatThrownBy(() -> TrackEnvironmentArchive.normalize(20,"tracks",row)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void givenImportedSource_whenNormalizing_thenRetainProvenanceAndExactValuesFingerprint() {
        var row=row();
        var source=new org.ctc.domain.model.TrackEnvironmentProvenance("Test source","2026-07-08T01:07:32Z","2026-11-08T12:00:00Z","a".repeat(64));
        var values=new org.ctc.domain.model.TrackEnvironmentValues(java.util.List.of("Night","Sunrise"),org.ctc.domain.model.TrackRainCapability.RAIN);
        row.put("environmentSource",source.encode());row.put("environmentImportHash",org.ctc.domain.model.TrackEnvironmentProvenance.valuesHash(values));row.put("environmentOverride",false);
        assertThat(TrackEnvironmentArchive.normalize(20,"tracks",row)).isEqualTo(row);
    }
}
