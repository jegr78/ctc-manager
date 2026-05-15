package org.ctc.backup.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip test for the JSON-text fields on {@link DataImportAudit}.
 *
 * <p>Serializes a {@link DataImportAudit} with realistic 24-entity {@code tableCounts}
 * via the {@code @Qualifier("backupObjectMapper")} {@link ObjectMapper}, then deserializes
 * back to a {@code Map<String, Long>} via the SAME mapper. Round-trip fidelity is asserted
 * via map equality with a {@link TypeReference} so {@code Long} values survive JSON-number
 * coercion.
 */
@SpringBootTest
@ActiveProfiles("dev")
class DataImportAuditSerializationTest {

    @Autowired
    @Qualifier("backupObjectMapper")
    private ObjectMapper backupObjectMapper;

    @Test
    void givenAuditWithLargeTableCounts_whenSerializedThenDeserialized_thenRoundTripMatches() throws Exception {
        // given — realistic 24-entity tableCounts (mirrors BackupSchema.getExportOrder())
        Map<String, Long> tableCounts = new LinkedHashMap<>();
        tableCounts.put("cars", 480L);
        tableCounts.put("tracks", 32L);
        tableCounts.put("seasons", 5L);
        tableCounts.put("season_phases", 18L);
        tableCounts.put("season_phase_groups", 9L);
        tableCounts.put("teams", 24L);
        tableCounts.put("phase_teams", 90L);
        tableCounts.put("season_teams", 120L);
        tableCounts.put("drivers", 287L);
        tableCounts.put("season_drivers", 540L);
        tableCounts.put("psn_aliases", 30L);
        tableCounts.put("race_scorings", 4L);
        tableCounts.put("match_scorings", 4L);
        tableCounts.put("race_settings", 60L);
        tableCounts.put("matchdays", 75L);
        tableCounts.put("matches", 300L);
        tableCounts.put("races", 900L);
        tableCounts.put("race_lineups", 1800L);
        tableCounts.put("race_results", 4500L);
        tableCounts.put("race_attachments", 50L);
        tableCounts.put("playoffs", 5L);
        tableCounts.put("playoff_rounds", 15L);
        tableCounts.put("playoff_matchups", 30L);
        tableCounts.put("playoff_seeds", 80L);

        String tableCountsJson = backupObjectMapper.writeValueAsString(tableCounts);

        DataImportAudit audit = DataImportAudit.builder()
                .id(UUID.randomUUID())
                .executedAt(Instant.parse("2026-05-14T17:23:09Z"))
                .executedBy("dev")
                .schemaVersion(1)
                .tableCountsWiped(tableCountsJson)
                .tableCountsRestored(tableCountsJson)
                .sourceFilename("backup-2023-saison.zip")
                .success(true)
                .build();

        // when — deserialize the JSON-text columns back to Map<String, Long>
        Map<String, Long> roundTrippedWiped = backupObjectMapper.readValue(
                audit.getTableCountsWiped(), new TypeReference<>() { });
        Map<String, Long> roundTrippedRestored = backupObjectMapper.readValue(
                audit.getTableCountsRestored(), new TypeReference<>() { });

        // then — exact map equality (24 entries, Long values preserved)
        assertThat(roundTrippedWiped)
                .as("tableCountsWiped JSON-text must round-trip via backupObjectMapper")
                .isEqualTo(tableCounts);
        assertThat(roundTrippedRestored)
                .as("tableCountsRestored JSON-text must round-trip via backupObjectMapper")
                .isEqualTo(tableCounts);
        assertThat(roundTrippedWiped).hasSize(24);
    }
}
