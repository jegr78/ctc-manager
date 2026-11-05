package db.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.ctc.domain.model.PickTyre;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class V37TyreConnectionMigrationTest {
    private static final List<String> TABLES = List.of("matchday_pick_revisions", "matchday_pick_decisions");
    private static final List<String> COLUMNS = List.of("track_id", "car_id", "car_pool_entry_id", "laps", "tyre_wear",
            "fuel_consumption", "refueling_speed", "initial_fuel", "required_pit_stops", "time_progression_mode",
            "time_progression_multiplier", "weather", "time_of_day", "available_tyres", "mandatory_tyres", "lobby_rules_profile");

    @Test
    void givenClosedMigrationConnection_whenWritingCanonicalTyres_thenEnforceEveryFamilyAndRequiredCompoundRule() throws Exception {
        // given
        String url = "jdbc:h2:mem:tyre-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try { verifyMigration(url, "sa", ""); }
        finally {
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) { sql.execute("SHUTDOWN"); }
        }
    }

    static void verifyMigration(String url, String user, String password) throws Exception {
        // given
        UUID id = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            for (String table : TABLES) {
                boolean decision = table.equals("matchday_pick_decisions");
                var fields = new ArrayList<String>();
                fields.add("id UUID PRIMARY KEY");
                if (decision) fields.add("kind VARCHAR(16) NOT NULL DEFAULT 'CORRECTION'");
                for (String column : COLUMNS) {
                    String type = column.endsWith("_id") ? "UUID" : List.of("laps", "tyre_wear", "fuel_consumption", "refueling_speed",
                            "required_pit_stops", "time_progression_multiplier").contains(column) ? "INT"
                            : column.endsWith("tyres") ? "VARCHAR(8)" : "VARCHAR(255)";
                    boolean nullable = decision || column.equals("car_pool_entry_id") || column.equals("time_progression_multiplier");
                    fields.add(column + " " + type + (nullable ? "" : " NOT NULL"));
                }
                fields.add("CONSTRAINT " + (decision ? "chk_pick_decision_correction" : "chk_pick_revision_payload") + " CHECK (1=1)");
                sql.execute("CREATE TABLE " + table + " (" + String.join(",", fields) + ")");
                List<Object> values = Arrays.asList(id, UUID.randomUUID(), UUID.randomUUID(), null, 12, 2, 3, 4,
                        "DEFAULT", 1, "MULTIPLIER", 0, "Test weather", "Sunset", "RS", "", "CTC_2027");
                try (var insert = connection.prepareStatement("INSERT INTO " + table + " (id," + String.join(",", COLUMNS)
                        + ") VALUES (" + String.join(",", java.util.Collections.nCopies(values.size(), "?")) + ")")) {
                    for (int i = 0; i < values.size(); i++) insert.setObject(i + 1, values.get(i));
                    insert.executeUpdate();
                }
            }
            var context = mock(Context.class); when(context.getConnection()).thenReturn(connection);
            assertThatCode(() -> new V37__ExtendPickTyres().migrate(context)).as("the dialect-specific migration applies").doesNotThrowAnyException();
        }
        // when / then
        try (var connection = DriverManager.getConnection(url, user, password)) {
            for (String table : TABLES) {
                for (var family : List.of(List.of(PickTyre.RS, PickTyre.RM, PickTyre.RH, PickTyre.I, PickTyre.W),
                        List.of(PickTyre.SS, PickTyre.SM, PickTyre.SH))) {
                    for (int mask = 1; mask < 1 << family.size(); mask++) {
                        var selected = new ArrayList<PickTyre>();
                        for (int i = 0; i < family.size(); i++) if ((mask & 1 << i) != 0) selected.add(family.get(i));
                        String available = encode(selected);
                        String dry = encode(selected.stream().filter(tyre -> !tyre.isWet()).toList());
                        for (String mandatory : List.of("", dry)) {
                            assertThatCode(() -> update(connection, table, id, available, mandatory, "CTC_2027"))
                                    .as("canonical %s values survive the closed migration connection", table).doesNotThrowAnyException();
                        }
                        if (family.getFirst() == PickTyre.RS) {
                            assertThatCode(() -> update(connection, table, id, available, "", "LEGACY")).doesNotThrowAnyException();
                        } else {
                            assertThatThrownBy(() -> update(connection, table, id, available, "", "LEGACY")).isInstanceOf(SQLException.class);
                        }
                    }
                }
                for (var invalid : List.of(List.of("", ""), List.of("RS,SS", ""), List.of("RS,RS", ""), List.of("FORGED", ""),
                        List.of("RM,RS", ""), List.of("RS,RM,RH,I,W,SS", ""), List.of("RS,RM,RH,I,W", "I"),
                        List.of("RS,RM,RH,I,W", "W"), List.of("RS", "RM"), List.of("SS,SM,SH", "RS"), List.of("SS,SM,SH", "SM,SS"))) {
                    assertThatThrownBy(() -> update(connection, table, id, invalid.get(0), invalid.get(1), "CTC_2027"))
                            .isInstanceOf(SQLException.class);
                }
            }
            try (var sql = connection.createStatement()) {
                assertThatThrownBy(() -> sql.executeUpdate("UPDATE matchday_pick_revisions SET available_tyres=NULL")).isInstanceOf(SQLException.class);
                String empty = COLUMNS.stream().map(column -> column + "=NULL").collect(Collectors.joining(","));
                assertThatCode(() -> sql.executeUpdate("UPDATE matchday_pick_decisions SET kind='OPEN'," + empty)).doesNotThrowAnyException();
                assertThatThrownBy(() -> sql.executeUpdate("UPDATE matchday_pick_decisions SET mandatory_tyres='RS'")).isInstanceOf(SQLException.class);
            }
        }
    }

    private static void update(Connection connection, String table, UUID id, String available, String mandatory, String profile) throws SQLException {
        try (var update = connection.prepareStatement("UPDATE " + table + " SET available_tyres=?,mandatory_tyres=?,lobby_rules_profile=? WHERE id=?")) {
            update.setString(1, available); update.setString(2, mandatory); update.setString(3, profile); update.setObject(4, id);
            assertThat(update.executeUpdate()).isEqualTo(1);
        }
    }

    private static String encode(List<PickTyre> tyres) { return tyres.stream().map(Enum::name).collect(Collectors.joining(",")); }
}
