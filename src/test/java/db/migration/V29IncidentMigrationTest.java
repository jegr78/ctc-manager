package db.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.api.migration.Context;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class V29IncidentMigrationTest {
    static final UUID SEASON = UUID.randomUUID(), HOME = UUID.randomUUID(), AWAY = UUID.randomUUID(), MATCH = UUID.randomUUID(),
            OTHER_MATCH = UUID.randomUUID(), PLAYOFF = UUID.randomUUID(), DAY = UUID.randomUUID(), RACE = UUID.randomUUID(),
            CONTEXT = UUID.randomUUID(), WINDOW = UUID.randomUUID(), REPORT = UUID.randomUUID(), REQUEST = UUID.randomUUID();

    @Test
    void givenMigrationCreatorClosed_whenWritingAllIncidentTables_thenPreserveFutureUtcTimesAndEnforceIndependentConstraints() throws Exception {
        String url = "jdbc:h2:mem:incident-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try {
            try (var creator = DriverManager.getConnection(url, "sa", ""); var sql = creator.createStatement()) {
                for (String table : List.of("seasons", "teams", "matches", "playoff_matchups", "matchdays", "races")) {
                    sql.execute("CREATE TABLE " + table + " (id UUID PRIMARY KEY)");
                }
                seed(creator, "seasons", SEASON); seed(creator, "teams", HOME, AWAY);
                seed(creator, "matches", MATCH, OTHER_MATCH); seed(creator, "playoff_matchups", PLAYOFF);
                seed(creator, "matchdays", DAY); seed(creator, "races", RACE);
                var migration = mock(Context.class); when(migration.getConnection()).thenReturn(creator);
                new V29__AddIncidentReporting().migrate(migration);
            }
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
                assertThatCode(() -> insertContext(connection, CONTEXT, MATCH, "200")).doesNotThrowAnyException();
                execute(connection, "INSERT INTO incident_report_windows (id,context_id,aggregate_version,state) VALUES (?,?,0,'DRAFT')", WINDOW, CONTEXT);
                sql.executeUpdate("UPDATE incident_report_windows SET aggregate_version=1,state='OPEN',opened_by='test-manager',opened_at=TIMESTAMP '2060-11-03 12:00:00',deadline=TIMESTAMP '2060-11-03 13:00:00'");
                execute(connection, "INSERT INTO incident_reports (id,window_id,race_id,original_matchday_id,original_home_team_id,original_away_team_id,case_reference,author_id,party_type,party_id,party_label,party_role_id,state,aggregate_version,latest_revision_number) VALUES (?,?,?,?,?,?,'INC-TEST','400','TEAM',?,'Test home','300','ACTIVE',1,1)", REPORT, WINDOW, RACE, DAY, HOME, AWAY, HOME);
                execute(connection, "INSERT INTO incident_report_revisions (id,report_id,revision_number,report_version,involved_drivers,replay_timestamp,description,evidence_links,actor_id,accepted_at,request_id) VALUES (?,?,1,1,'Test driver and guest','00:00','Test private description','','400',TIMESTAMP '2060-11-03 12:00:01',?)", UUID.randomUUID(), REPORT, REQUEST);
                execute(connection, "INSERT INTO incident_report_events (id,window_id,report_id,event_key,kind,window_version,report_version,revision_number,actor_kind,actor_id,reason,accepted_at,deadline,request_id) VALUES (?,?,?,'report:test:1','SUBMITTED',1,1,1,'REPORTER','400','',TIMESTAMP '2060-11-03 12:00:01',TIMESTAMP '2060-11-03 13:00:00',?)", UUID.randomUUID(), WINDOW, REPORT, REQUEST);
                try (var row = sql.executeQuery("SELECT deadline FROM incident_report_windows")) {
                    assertThat(row.next()).isTrue(); assertThat(row.getObject(1, java.time.LocalDateTime.class)).isEqualTo(java.time.LocalDateTime.of(2060, 11, 3, 13, 0));
                }
                for (String update : List.of("match_id=NULL", "playoff_matchup_id='" + PLAYOFF + "'", "home_team_id=away_team_id", "guild_id='wrong'", "channel_id='wrong'", "aggregate_version=0")) {
                    assertThatThrownBy(() -> sql.executeUpdate("UPDATE incident_match_contexts SET " + update)).isInstanceOf(java.sql.SQLException.class);
                }
                assertThatThrownBy(() -> insertContext(connection, UUID.randomUUID(), MATCH, "201")).isInstanceOf(java.sql.SQLException.class);
                assertThatThrownBy(() -> insertContext(connection, UUID.randomUUID(), OTHER_MATCH, "200")).isInstanceOf(java.sql.SQLException.class);
                for (String update : List.of("state='INVALID'", "deadline=NULL", "state='CLOSED'", "aggregate_version=-1")) {
                    assertThatThrownBy(() -> sql.executeUpdate("UPDATE incident_report_windows SET " + update)).isInstanceOf(java.sql.SQLException.class);
                }
                for (String update : List.of("state='INVALID'", "party_type='INVALID'", "latest_revision_number=0", "frozen_revision_number=2", "original_home_team_id=original_away_team_id", "author_id='invalid'")) {
                    assertThatThrownBy(() -> sql.executeUpdate("UPDATE incident_reports SET " + update)).isInstanceOf(java.sql.SQLException.class);
                }
                for (String update : List.of("revision_number=0", "report_version=0", "involved_drivers=' '", "replay_timestamp=' '", "description=' '", "actor_id='invalid'")) {
                    assertThatThrownBy(() -> sql.executeUpdate("UPDATE incident_report_revisions SET " + update)).isInstanceOf(java.sql.SQLException.class);
                }
                for (String update : List.of("kind='INVALID'", "actor_kind='INVALID'", "request_id=NULL", "report_id=NULL", "report_version=0")) {
                    assertThatThrownBy(() -> sql.executeUpdate("UPDATE incident_report_events SET " + update)).isInstanceOf(java.sql.SQLException.class);
                }
                assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM races")).isInstanceOf(java.sql.SQLException.class);
                assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM matches")).isInstanceOf(java.sql.SQLException.class);
            }
        } finally {
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) { sql.execute("SHUTDOWN"); }
        }
    }

    private static void insertContext(Connection connection, UUID id, UUID match, String channel) throws Exception {
        execute(connection, "INSERT INTO incident_match_contexts (id,match_id,season_id,home_team_id,away_team_id,guild_id,channel_id,aggregate_version,bound_by,bound_at) VALUES (?,?,?,?,?,'100',?,1,'test-manager',TIMESTAMP '2060-11-03 11:00:00')", id, match, SEASON, HOME, AWAY, channel);
    }
    private static void seed(Connection connection, String table, UUID... ids) throws Exception {
        for (var id : ids) execute(connection, "INSERT INTO " + table + " (id) VALUES (?)", id);
    }
    private static void execute(Connection connection, String sql, Object... values) throws Exception {
        try (var row = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) row.setObject(i + 1, values[i]);
            row.executeUpdate();
        }
    }
}
