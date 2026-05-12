package org.ctc.backup.dto;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BackupImportPreviewTest {

    @Test
    void givenAllFields_whenConstruct_thenAccessorsReturnSameValues() {
        // given
        UUID stagingId = UUID.randomUUID();
        String originalFilename = "backup.zip";
        long fileSizeBytes = 12_345_678L;
        int schemaVersion = 1;
        int currentSchemaVersion = 1;
        boolean schemaMatches = true;
        EntityRowCount card1 = new EntityRowCount("seasons", "Seasons", 5L, 6L);
        EntityRowCount card2 = new EntityRowCount("races", "Races", 10L, 12L);
        List<EntityRowCount> entityCounts = List.of(card1, card2);
        int uploadFileCount = 5;
        long totalImportedRows = 1_000L;

        // when
        BackupImportPreview preview = new BackupImportPreview(
                stagingId, originalFilename, fileSizeBytes,
                schemaVersion, currentSchemaVersion, schemaMatches,
                entityCounts, uploadFileCount, totalImportedRows
        );

        // then
        assertThat(preview.stagingId()).isEqualTo(stagingId);
        assertThat(preview.originalFilename()).isEqualTo(originalFilename);
        assertThat(preview.fileSizeBytes()).isEqualTo(fileSizeBytes);
        assertThat(preview.schemaVersion()).isEqualTo(schemaVersion);
        assertThat(preview.currentSchemaVersion()).isEqualTo(currentSchemaVersion);
        assertThat(preview.schemaMatches()).isEqualTo(schemaMatches);
        assertThat(preview.entityCounts()).isEqualTo(entityCounts);
        assertThat(preview.uploadFileCount()).isEqualTo(uploadFileCount);
        assertThat(preview.totalImportedRows()).isEqualTo(totalImportedRows);
    }

    @Test
    void givenSchemaMismatch_whenConstruct_thenSchemaMatchesIsCallerSupplied() {
        // given
        UUID stagingId = UUID.randomUUID();
        int schemaVersion = 999;
        int currentSchemaVersion = 1;
        boolean schemaMatches = false;

        // when
        BackupImportPreview preview = new BackupImportPreview(
                stagingId, "backup.zip", 1000L,
                schemaVersion, currentSchemaVersion, schemaMatches,
                List.of(), 0, 0L
        );

        // then
        assertThat(preview.schemaMatches())
                .as("schemaMatches is stored, not derived — service computes once and passes in")
                .isFalse();
    }

    @Test
    void givenTwoRecordsWithIdenticalFields_whenEquals_thenEqual() {
        // given
        UUID stagingId = UUID.randomUUID();
        List<EntityRowCount> entityCounts = List.of(new EntityRowCount("seasons", "Seasons", 5L, 6L));

        // when
        BackupImportPreview preview1 = new BackupImportPreview(
                stagingId, "backup.zip", 1000L,
                1, 1, true, entityCounts, 3, 100L
        );
        BackupImportPreview preview2 = new BackupImportPreview(
                stagingId, "backup.zip", 1000L,
                1, 1, true, entityCounts, 3, 100L
        );

        // then
        assertThat(preview1).isEqualTo(preview2);
        assertThat(preview1.hashCode()).isEqualTo(preview2.hashCode());
    }
}
