package db.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class V28PublicationConnectionMigrationTest {
    static final UUID SEASON = UUID.randomUUID(), DAY = UUID.randomUUID(), RACE = UUID.randomUUID(), HOME = UUID.randomUUID(), AWAY = UUID.randomUUID();
    static final String INSERT = """
            INSERT INTO pick_publication_attempts
            (id,batch_id,race_id,season_id,original_matchday_id,original_home_team_id,original_away_team_id,content_version,source_version,
             origin,output_type,content_hash,field_mask,actor_id,channel_id,webhook_id,template_hash,request_manifest,request_hash,runtime_generation,captured_at,state,active_race_id)
            VALUES (RANDOM_UUID(),RANDOM_UUID(),?,?,?,?,?,1,0,'LEGACY','SETTINGS',?,0,'test-admin','100','200',?,'{}',?,?,TIMESTAMP '2026-11-03 12:00:00','PREPARED',?)
            """;

    @Test
    void givenClosedMigrationConnection_whenWritingFromANewConnection_thenKeepValidPublicationStatesWritableAndRejectInvalidMemberships() throws Exception {
        // given
        String url = "jdbc:h2:mem:publication-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try {
            try (var creator = DriverManager.getConnection(url, "sa", ""); var sql = creator.createStatement()) {
                for (String table : List.of("seasons", "matchdays", "teams", "races")) sql.execute("CREATE TABLE " + table + " (id UUID PRIMARY KEY)");
                for (var item : java.util.Map.of("seasons", List.of(SEASON), "matchdays", List.of(DAY), "teams", List.of(HOME, AWAY), "races", List.of(RACE)).entrySet()) {
                    try (var row = creator.prepareStatement("INSERT INTO " + item.getKey() + " (id) VALUES (?)")) {
                        for (var id : item.getValue()) { row.setObject(1, id); row.executeUpdate(); }
                    }
                }
                var context = mock(Context.class); when(context.getConnection()).thenReturn(creator);
                new V28__AddPickPublicationRecords().migrate(context);
                assertThatCode(() -> insert(creator)).doesNotThrowAnyException(); sql.executeUpdate("DELETE FROM pick_publication_attempts");
            }
            // when / then
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
                assertThatCode(() -> insert(connection)).as("valid publication remains writable after its migration connection closes").doesNotThrowAnyException();
                for (String update : List.of("origin='APPROVAL',source_version=1", "origin='CORRECTION'", "output_type='LOBBY_SETTINGS'", "output_type='MATCH_PREVIEW'",
                        "state='UNCERTAIN',completed_at=TIMESTAMP '2026-11-03 12:00:01',failure_code='LOST_RESPONSE'",
                        "state='SUCCEEDED',active_race_id=NULL,message_id='300'", "state='FAILED'", "state='ABANDONED'",
                        "state='PREPARED',completed_at=NULL,active_race_id=race_id,message_id=NULL")) {
                    assertThatCode(() -> sql.executeUpdate("UPDATE pick_publication_attempts SET " + update)).doesNotThrowAnyException();
                }
                for (String update : List.of("origin='INVALID'", "output_type='INVALID'", "state='INVALID'")) {
                    assertThatThrownBy(() -> sql.executeUpdate("UPDATE pick_publication_attempts SET " + update)).isInstanceOf(java.sql.SQLException.class);
                }
            }
        } finally {
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) { sql.execute("SHUTDOWN"); }
        }
    }

    private static void insert(Connection connection) throws Exception {
        try (var row = connection.prepareStatement(INSERT)) {
            Object[] values = {RACE, SEASON, DAY, HOME, AWAY, "a".repeat(64), "b".repeat(64), "c".repeat(64), UUID.randomUUID() + ":0", RACE};
            for (int index = 0; index < values.length; index++) row.setObject(index + 1, values[index]);
            row.executeUpdate();
        }
    }
}
