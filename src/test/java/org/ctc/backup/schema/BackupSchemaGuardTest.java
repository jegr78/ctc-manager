package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ctc.discord.model.DiscordPostType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupSchemaGuardTest {

    @Autowired
    BackupSchema backupSchema;

    @Autowired
    @Qualifier("backupObjectMapper")
    ObjectMapper backupObjectMapper;

    @Test
    void givenBackupSchema_whenInspected_thenSchemaVersionIsEight() {
        // when / then
        assertThat(BackupSchema.SCHEMA_VERSION)
                .as("BackupSchema.SCHEMA_VERSION changed from 8; this is a wire contract bump")
                .isEqualTo(8);
    }

    @Test
    void givenBackupSchema_whenInspected_thenExportOrderHasFortyOneEntities() {
        // when / then
        assertThat(backupSchema.getExportOrder().size())
                .as("BackupSchema.EXPORT_ORDER size changed from 41; a new entity needs a SCHEMA_VERSION bump "
                        + "and an entry in BackupImportService.TABLES_ABSENT_IN_SCHEMA for older versions")
                .isEqualTo(41);
    }

    @ParameterizedTest
    @EnumSource(DiscordPostType.class)
    void givenEachDiscordPostType_whenSerializedAndDeserialized_thenRoundTripPreservesEnumName(
            DiscordPostType type) throws Exception {
        String json = backupObjectMapper.writeValueAsString(type);
        DiscordPostType restored = backupObjectMapper.readValue(json, DiscordPostType.class);

        assertThat(json).contains(type.name());
        assertThat(restored).isEqualTo(type);
    }
}
