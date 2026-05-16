package org.ctc.backup.schema;

import org.ctc.backup.audit.DataImportAudit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 72 / Plan 04 (Wave 3) — proves D-06 (package-name filter) structurally excludes
 * the {@link DataImportAudit} entity from {@code BackupSchema.getExportOrder()} — the
 * canonical IMPORT-08 enforcement.
 *
 * <p>This IT was originally created in plan 01 with an FQN-string lookup so it could compile
 * before {@link DataImportAudit} existed on the classpath. Plan 04 tightens the assertion to
 * a direct {@code import org.ctc.backup.audit.DataImportAudit;} + by-class comparison now that
 * the entity exists — the exclusion is provable concretely against the loaded class object.
 * The complementary {@code tableName} assertion stays in place for defense in depth.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupSchemaExclusionIT {

    @Autowired
    private BackupSchema backupSchema;

    @Test
    void givenSpringContext_whenGetExportOrder_thenDataImportAuditIsNotPresent() {
        // when
        var exportOrder = backupSchema.getExportOrder();

        // then — D-06 structural guarantee for IMPORT-08 (proved against DataImportAudit.class)
        assertThat(exportOrder)
                .as("DataImportAudit lives in org.ctc.backup.audit and must be filtered out by the org.ctc.domain.model package gate")
                .extracting(EntityRef::entityClass)
                .doesNotContain(DataImportAudit.class);
        assertThat(exportOrder)
                .extracting(EntityRef::tableName)
                .doesNotContain("data_import_audit");
    }
}
