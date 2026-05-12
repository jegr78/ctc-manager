package org.ctc.backup.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntityRowCountTest {

    @Test
    void givenAllFields_whenConstruct_thenAccessorsReturnSameValues() {
        // given / when
        EntityRowCount rowCount = new EntityRowCount("season_phases", "Season Phases", 42L, 50L);

        // then
        assertThat(rowCount.tableName()).isEqualTo("season_phases");
        assertThat(rowCount.humanLabel()).isEqualTo("Season Phases");
        assertThat(rowCount.currentRows()).isEqualTo(42L);
        assertThat(rowCount.importedRows()).isEqualTo(50L);
    }

    @Test
    void givenZeroCounts_whenConstruct_thenAccessorsReturnZero() {
        // given / when
        EntityRowCount rowCount = new EntityRowCount("races", "Races", 0L, 0L);

        // then
        assertThat(rowCount.currentRows()).isEqualTo(0L);
        assertThat(rowCount.importedRows()).isEqualTo(0L);
    }

    @Test
    void givenTwoRecordsWithIdenticalFields_whenEquals_thenEqual() {
        // given / when
        EntityRowCount rowCount1 = new EntityRowCount("season_phases", "Season Phases", 42L, 50L);
        EntityRowCount rowCount2 = new EntityRowCount("season_phases", "Season Phases", 42L, 50L);

        // then
        assertThat(rowCount1).isEqualTo(rowCount2);
        assertThat(rowCount1.hashCode()).isEqualTo(rowCount2.hashCode());
    }
}
