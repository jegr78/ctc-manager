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

/**
 * BACK-01 guard test: locks the backup wire-contract invariants (schema version + entity count)
 * so future cleanup work cannot silently change them without a deliberate decision.
 *
 * <p>Note: Despite the {@code *Test} filename suffix, this class is {@code @Tag("integration")}
 * because {@link BackupSchema} requires a Spring/JPA context ({@code EntityManagerFactory} +
 * {@code EntityTopoSorter} injection via {@code @PostConstruct}). It is routed to Failsafe, not
 * Surefire. Per-commit run command: {@code ./mvnw verify -Dit.test=BackupSchemaGuardTest}.
 */
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
    void givenBackupSchema_whenInspected_thenSchemaVersionIsTwo() {
        // when / then
        assertThat(BackupSchema.SCHEMA_VERSION)
                .as("BackupSchema.SCHEMA_VERSION changed from 2 — this is a wire contract bump; "
                        + "see Phase 75 SCHEMA_VERSION gate or write a new migration phase")
                .isEqualTo(2);
    }

    @Test
    void givenBackupSchema_whenInspected_thenExportOrderHasTwentySixEntities() {
        // when / then
        assertThat(backupSchema.getExportOrder().size())
                .as("BackupSchema.EXPORT_ORDER size changed from 26 — if a new entity was added, "
                        + "bump SCHEMA_VERSION and update BackupRoundTripIT expected row-count assertions")
                .isEqualTo(26);
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
