package org.ctc.backup.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BackupImportLimitsTest {

    @Test
    void givenMaxEntryBytes_whenRead_thenEquals52428800() {
        assertThat(BackupImportLimits.MAX_ENTRY_BYTES).isEqualTo(52_428_800L);
    }

    @Test
    void givenMaxTotalBytes_whenRead_thenEquals524288000() {
        assertThat(BackupImportLimits.MAX_TOTAL_BYTES).isEqualTo(524_288_000L);
    }

    @Test
    void givenMaxEntries_whenRead_thenEquals50000() {
        assertThat(BackupImportLimits.MAX_ENTRIES).isEqualTo(50_000);
    }
}
