package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

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

@CtcDevSpringBootContext
@Tag("integration")
class V17MigrationIT {

	@Autowired
	private DataSource dataSource;

	@Test
	void givenH2WithV17Applied_whenInspectingMatchesWalkoverTeamIdColumn_thenExists() throws Exception {
		Set<String> columns = collectColumnNames("MATCHES");
		assertThat(columns).contains("walkover_team_id");
	}

	@Test
	void givenH2WithV17Applied_whenInspectingWalkoverTeamIdNullability_thenNullable() throws Exception {
		try (Connection c = dataSource.getConnection()) {
			DatabaseMetaData md = c.getMetaData();
			assertColumnNullable(md, "MATCHES", "WALKOVER_TEAM_ID");
		}
	}

	private Set<String> collectColumnNames(String tableName) throws Exception {
		Set<String> columns = new HashSet<>();
		try (Connection c = dataSource.getConnection()) {
			DatabaseMetaData md = c.getMetaData();
			try (ResultSet rs = md.getColumns(null, null, tableName, null)) {
				while (rs.next()) {
					columns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
				}
			}
		}
		return columns;
	}

	private void assertColumnNullable(DatabaseMetaData md, String table, String column) throws Exception {
		try (ResultSet rs = md.getColumns(null, null, table, column)) {
			assertThat(rs.next()).as("column %s.%s must exist", table, column).isTrue();
			int nullable = rs.getInt("NULLABLE");
			assertThat(nullable)
					.as("column %s.%s must be NULLABLE", table, column)
					.isEqualTo(DatabaseMetaData.columnNullable);
		}
	}
}
