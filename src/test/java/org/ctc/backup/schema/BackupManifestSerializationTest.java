package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 72 / Plan 02 — Wave 0 stub turning GREEN once {@link BackupManifest} lands (task 2).
 *
 * <p>Pure unit (Surefire, *Test.java) — does NOT boot a Spring context. The ObjectMapper
 * is constructed inline with the same settings as
 * {@code BackupObjectMapperConfig.backupObjectMapper(...)} (plan 03) so this test stays
 * independent of the Spring DI graph.
 */
class BackupManifestSerializationTest {

    private ObjectMapper backupMapper;

    @BeforeEach
    void setUp() {
        backupMapper = new ObjectMapper();
        backupMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        backupMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        backupMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void givenSampleManifest_whenSerializeThroughBackupMapper_thenJsonHasSnakeCaseKeys() throws Exception {
        // given
        var manifest = new BackupManifest(
                1,
                "1.10.0",
                Instant.parse("2026-05-11T10:00:00Z"),
                Map.of("seasons", 5L));
        // when
        String json = backupMapper.writeValueAsString(manifest);
        // then — snake_case keys (per-field @JsonProperty on record components)
        assertThat(json).contains("\"schema_version\"");
        assertThat(json).contains("\"app_version\"");
        assertThat(json).contains("\"export_date\"");
        assertThat(json).contains("\"table_counts\"");
        // and — no camelCase leakage
        assertThat(json).doesNotContain("\"schemaVersion\"");
        assertThat(json).doesNotContain("\"appVersion\"");
        assertThat(json).doesNotContain("\"exportDate\"");
        assertThat(json).doesNotContain("\"tableCounts\"");
    }

    @Test
    void givenSampleManifest_whenSerializeExportDate_thenIsoStringNotMillis() throws Exception {
        // given
        var manifest = new BackupManifest(
                1, "1.10.0",
                Instant.parse("2026-05-11T10:00:00Z"),
                Map.of("seasons", 5L));
        // when
        String json = backupMapper.writeValueAsString(manifest);
        // then — ISO-8601 string (WRITE_DATES_AS_TIMESTAMPS=false)
        assertThat(json).contains("\"export_date\":\"2026-05-11T10:00:00Z\"");
    }

    @Test
    void givenSampleManifest_whenSerializeSchemaVersion_thenJsonInteger() throws Exception {
        // given
        var manifest = new BackupManifest(
                1, "1.10.0", Instant.parse("2026-05-11T10:00:00Z"), Map.of("seasons", 5L));
        // when
        String json = backupMapper.writeValueAsString(manifest);
        // then — integer (no quotes around the 1)
        assertThat(json).contains("\"schema_version\":1");
        assertThat(json).doesNotContain("\"schema_version\":\"1\"");
    }

    @Test
    void givenSerializedJson_whenDeserialize_thenRoundTripsEqual() throws Exception {
        // given
        var original = new BackupManifest(
                1, "1.10.0",
                Instant.parse("2026-05-11T10:00:00Z"),
                Map.of("seasons", 5L, "season_phases", 18L));
        // when
        String json = backupMapper.writeValueAsString(original);
        BackupManifest roundTripped = backupMapper.readValue(json, BackupManifest.class);
        // then
        assertThat(roundTripped).isEqualTo(original);
    }
}
