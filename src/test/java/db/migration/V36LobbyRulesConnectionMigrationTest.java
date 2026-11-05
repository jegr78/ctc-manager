package db.migration;

import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.assertj.core.api.Assertions.*;

class V36LobbyRulesConnectionMigrationTest {
    @Test
    void givenClosedMigrationConnection_whenSavingLobbyChoices_thenKeepValidChoicesWritableAndRejectInvalidCombinations() throws Exception {
        // given
        String url = "jdbc:h2:mem:lobby-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try { verifyMigration(url, "sa", ""); }
        finally {
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) { sql.execute("SHUTDOWN"); }
        }
    }

    static void verifyMigration(String url, String user, String password) throws Exception {
        // given
        UUID id = UUID.randomUUID();
        List<String> tables = List.of("seasons", "race_settings", "matchday_pick_revisions", "pick_publication_attempts", "matchday_pick_decisions");
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            for (String table : tables) {
                String columns = table.equals("pick_publication_attempts") ? ",field_mask INT NOT NULL DEFAULT 0,CONSTRAINT ck_publication_fields CHECK (field_mask>=0 AND field_mask<=8191)"
                        : table.equals("matchday_pick_decisions") ? ",car_id UUID" : "";
                sql.execute("CREATE TABLE " + table + " (id UUID PRIMARY KEY" + columns + ")");
                sql.execute("INSERT INTO " + table + " (id) VALUES ('" + id + "')");
            }
            sql.execute("UPDATE matchday_pick_decisions SET car_id='" + UUID.randomUUID() + "'");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V36__version_lobby_rules.sql"));
        }
        // when / then
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            for (String table : tables) {
                assertThatCode(() -> sql.executeUpdate("UPDATE " + table + " SET id=id"))
                        .as("legacy %s stays writable after the migration connection closes", table).doesNotThrowAnyException();
            }
            for (String profile : List.of("CTC_2027", "LEGACY")) {
                assertThatCode(() -> sql.executeUpdate("UPDATE seasons SET lobby_rules_profile='" + profile + "'"))
                        .doesNotThrowAnyException();
            }
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE seasons SET lobby_rules_profile='FORGED'"))
                    .isInstanceOf(java.sql.SQLException.class);
            for (String table : tables.subList(1, tables.size())) {
                if (!table.equals("race_settings")) sql.executeUpdate("UPDATE " + table + " SET lobby_rules_profile='CTC_2027'");
                for (String start : List.of("GRID_FALSE_START", "ROLLING")) {
                    for (String slipstream : List.of("REAL", "WEAK")) {
                        assertThatCode(() -> sql.executeUpdate("UPDATE " + table + " SET start_type='" + start + "',slipstream='" + slipstream + "'"))
                                .as("valid %s choices survive a new connection", table).doesNotThrowAnyException();
                    }
                }
                for (String update : List.of("start_type='FORGED'", "slipstream='FORGED'")) {
                    assertThatThrownBy(() -> sql.executeUpdate("UPDATE " + table + " SET " + update)).isInstanceOf(java.sql.SQLException.class);
                }
                if (table.equals("race_settings")) continue;
                for (String update : List.of("lobby_rules_profile='FORGED'", "lobby_rules_profile='LEGACY'", "start_type=NULL", "slipstream=NULL", "lobby_rules_profile=NULL")) {
                    assertThatThrownBy(() -> sql.executeUpdate("UPDATE " + table + " SET " + update)).isInstanceOf(java.sql.SQLException.class);
                }
                assertThatCode(() -> sql.executeUpdate("UPDATE " + table + " SET lobby_rules_profile='LEGACY',start_type='GRID_FALSE_START',slipstream='REAL'"))
                        .doesNotThrowAnyException();
                assertThatThrownBy(() -> sql.executeUpdate("UPDATE " + table + " SET slipstream='WEAK'")).isInstanceOf(java.sql.SQLException.class);
            }
            assertThatCode(() -> sql.executeUpdate("UPDATE pick_publication_attempts SET field_mask=32767")).doesNotThrowAnyException();
            for (int invalid : List.of(-1, 32768)) assertThatThrownBy(() -> sql.executeUpdate("UPDATE pick_publication_attempts SET field_mask=" + invalid))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE matchday_pick_decisions SET car_id=NULL")).isInstanceOf(java.sql.SQLException.class);
            assertThatCode(() -> sql.executeUpdate("UPDATE matchday_pick_decisions SET car_id=NULL,lobby_rules_profile=NULL,start_type=NULL,slipstream=NULL"))
                    .doesNotThrowAnyException();
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE matchday_pick_decisions SET start_type='GRID_FALSE_START'"))
                    .isInstanceOf(java.sql.SQLException.class);
        }
    }
}
