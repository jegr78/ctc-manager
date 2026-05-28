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
class V13MigrationIT {

	@Autowired
	private DataSource dataSource;

	@Test
	void givenH2WithV13Applied_whenInspectingSeasonsThreadIdColumns_thenBothExist() throws Exception {
		Set<String> columns = collectColumnNames("SEASONS");
		assertThat(columns)
				.contains("discord_race_results_thread_id", "discord_standings_thread_id");
	}

	@Test
	void givenH2WithV13Applied_whenInspectingDiscordGlobalConfigForumWebhookColumns_thenBothExist() throws Exception {
		Set<String> columns = collectColumnNames("DISCORD_GLOBAL_CONFIG");
		assertThat(columns)
				.contains("race_results_forum_webhook_url", "standings_forum_webhook_url");
	}

	@Test
	void givenH2WithV13Applied_whenInspectingThreadIdColumnsNullability_thenAllNullable() throws Exception {
		try (Connection c = dataSource.getConnection()) {
			DatabaseMetaData md = c.getMetaData();
			assertColumnNullable(md, "SEASONS", "DISCORD_RACE_RESULTS_THREAD_ID");
			assertColumnNullable(md, "SEASONS", "DISCORD_STANDINGS_THREAD_ID");
			assertColumnNullable(md, "DISCORD_GLOBAL_CONFIG", "RACE_RESULTS_FORUM_WEBHOOK_URL");
			assertColumnNullable(md, "DISCORD_GLOBAL_CONFIG", "STANDINGS_FORUM_WEBHOOK_URL");
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
