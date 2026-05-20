package db.migration;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.sql.DataSource;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 72 / Plan 04 — Wave 0 stub turning GREEN once V7__data_import_audit.sql lands (task 2).
 *
 * <p>Asserts the post-migration schema shape of {@code data_import_audit} on H2 (dev profile).
 * MariaDB equivalent runs in the existing {@code mariadb-migration-smoke.yml} CI workflow
 * (analog to v1.9) — no new MariaDB-specific IT in Phase 72.
 *
 * <p>{@code @SpringBootTest(classes = CtcManagerApplication.class)} is mandatory because
 * {@code db.migration} package is OUTSIDE the {@code org.ctc.*} component-scan tree
 * (mirrors V4MigrationSmokeIT.java).
 */
@CtcDevSpringBootContext
@Tag("integration")
class V7DataImportAuditMigrationIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void givenH2WithV7Applied_whenInspectingDataImportAuditColumns_thenAllExpectedColumnsExist() throws Exception {
        // when
        Set<String> columns = new HashSet<>();
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, "DATA_IMPORT_AUDIT", null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
        }
        // then — exactly 8 columns per D-09
        assertThat(columns).containsExactlyInAnyOrder(
                "id",
                "executed_at",
                "executed_by",
                "schema_version",
                "table_counts_wiped",
                "table_counts_restored",
                "source_filename",
                "success");
    }

    @Test
    void givenH2WithV7Applied_whenInspectingExecutedAtColumn_thenItIsNotNullable() throws Exception {
        // when
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getColumns(null, null, "DATA_IMPORT_AUDIT", "EXECUTED_AT")) {
                // then
                assertThat(rs.next()).isTrue();
                int nullable = rs.getInt("NULLABLE");
                assertThat(nullable)
                        .as("executed_at must be NOT NULL")
                        .isEqualTo(DatabaseMetaData.columnNoNulls);
            }
        }
    }

    @Test
    void givenH2WithV7Applied_whenInspectingIndex_thenExecutedAtIndexExists() throws Exception {
        // when
        boolean foundIndex = false;
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getIndexInfo(null, null, "DATA_IMPORT_AUDIT", false, false)) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    String columnName = rs.getString("COLUMN_NAME");
					if (indexName == null) {
						continue;
					}
                    if ("idx_data_import_audit_executed_at".equalsIgnoreCase(indexName)
                            && "EXECUTED_AT".equalsIgnoreCase(columnName)) {
                        foundIndex = true;
                    }
                }
            }
        }
        // then
        assertThat(foundIndex)
                .as("idx_data_import_audit_executed_at index must exist on executed_at column")
                .isTrue();
    }
}
