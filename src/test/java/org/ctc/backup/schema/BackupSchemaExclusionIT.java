package org.ctc.backup.schema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 72 / Plan 01 — Wave 0 stub. Proves D-06 (package-name filter) structurally excludes
 * the {@code org.ctc.backup.audit.DataImportAudit} entity from {@code getExportOrder()}
 * — the canonical IMPORT-08 enforcement.
 *
 * <p>NOTE: This IT references the FQN string of {@code DataImportAudit} so that it compiles
 * in Wave 0 (before plan 04 creates the class). Plan 04 may convert the string lookup to a
 * direct {@code import} once {@code DataImportAudit.class} exists on the classpath. The
 * assertion semantics already hold today against any class placed under
 * {@code org.ctc.backup.audit.*} — the package-name gate in
 * {@link BackupSchema} filters them out structurally.
 */
@SpringBootTest
@ActiveProfiles("dev")
class BackupSchemaExclusionIT {

    private static final String DATA_IMPORT_AUDIT_FQN = "org.ctc.backup.audit.DataImportAudit";
    private static final String DATA_IMPORT_AUDIT_TABLE = "data_import_audit";

    @Autowired
    private BackupSchema backupSchema;

    @Test
    void givenSpringContext_whenGetExportOrder_thenDataImportAuditIsNotPresent() {
        // when
        var exportOrder = backupSchema.getExportOrder();

        // then — D-06 structural guarantee for IMPORT-08
        assertThat(exportOrder)
                .as("DataImportAudit lives in org.ctc.backup.audit and must be filtered out by the org.ctc.domain.model package gate")
                .allSatisfy(ref ->
                        assertThat(ref.entityClass().getName()).isNotEqualTo(DATA_IMPORT_AUDIT_FQN));
        assertThat(exportOrder)
                .extracting(EntityRef::tableName)
                .doesNotContain(DATA_IMPORT_AUDIT_TABLE);
    }
}
