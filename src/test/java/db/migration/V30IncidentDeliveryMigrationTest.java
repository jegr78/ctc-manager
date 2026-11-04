package db.migration;

import java.sql.*;
import java.util.*;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class V30IncidentDeliveryMigrationTest {
    @Test
    void givenOldCommittedSubmissionRequests_whenUpgrading_thenPreserveFirstReceiptsAndFrozenDisclosuresWithoutInventingRequests() throws Exception {
        String url = "jdbc:h2:mem:delivery-migration-" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1";
        UUID season=UUID.randomUUID(), home=UUID.randomUUID(), away=UUID.randomUUID(), match=UUID.randomUUID(), day=UUID.randomUUID(), race=UUID.randomUUID(), context=UUID.randomUUID(), window=UUID.randomUUID();
        UUID active=UUID.randomUUID(), withdrawn=UUID.randomUUID(), unrequested=UUID.randomUUID();
        try {
            try (var connection=DriverManager.getConnection(url,"sa","");var ddl=connection.createStatement()) {
                for (String table:List.of("seasons","teams","matches","playoff_matchups","matchdays","races")) ddl.execute("CREATE TABLE "+table+" (id UUID PRIMARY KEY)");
                execute(connection,"INSERT INTO seasons(id) VALUES (?)",season); execute(connection,"INSERT INTO teams(id) VALUES (?)",home); execute(connection,"INSERT INTO teams(id) VALUES (?)",away);
                execute(connection,"INSERT INTO matches(id) VALUES (?)",match); execute(connection,"INSERT INTO matchdays(id) VALUES (?)",day); execute(connection,"INSERT INTO races(id) VALUES (?)",race);
                var migration=mock(Context.class); when(migration.getConnection()).thenReturn(connection); new V29__AddIncidentReporting().migrate(migration);
                execute(connection,"INSERT INTO incident_match_contexts(id,match_id,season_id,home_team_id,away_team_id,guild_id,channel_id,aggregate_version,bound_by,bound_at) VALUES (?,?,?,?,?,'100','200',1,'test-manager',TIMESTAMP '2060-11-03 11:00:00')",context,match,season,home,away);
                execute(connection,"INSERT INTO incident_report_windows(id,context_id,aggregate_version,state,deadline,opened_at,opened_by,closed_at) VALUES (?,?,2,'CLOSED',TIMESTAMP '2060-11-03 13:00:00',TIMESTAMP '2060-11-03 12:00:00','test-manager',TIMESTAMP '2060-11-03 13:00:00')",window,context);
                for (UUID report:List.of(active,withdrawn,unrequested)) {
                    boolean isActive=report.equals(active); String state=isActive?"ACTIVE":"WITHDRAWN";
                    execute(connection,"INSERT INTO incident_reports(id,window_id,race_id,original_matchday_id,original_home_team_id,original_away_team_id,case_reference,author_id,party_type,party_id,party_label,party_role_id,state,aggregate_version,latest_revision_number,frozen_revision_number) VALUES (?,?,?,?,?,?,?,'400','TEAM',?,'Test private party','300',?,?,1,?)",report,window,race,day,home,away,"INC-"+report,home,state,isActive?1:2,isActive?1:null);
                    var request=UUID.randomUUID();
                    execute(connection,"INSERT INTO incident_report_revisions(id,report_id,revision_number,report_version,involved_drivers,replay_timestamp,description,evidence_links,actor_id,accepted_at,request_id) VALUES (?,?,1,1,'Test PRIVATE driver','00:00','Test PRIVATE description','','400',TIMESTAMP '2060-11-03 12:01:00',?)",UUID.randomUUID(),report,request);
                    if (!report.equals(unrequested)) execute(connection,"INSERT INTO incident_report_events(id,window_id,report_id,event_key,kind,window_version,report_version,revision_number,actor_kind,actor_id,reason,accepted_at,deadline,request_id) VALUES (?,?,?,?,'SUBMITTED',1,1,1,'REPORTER','400','',TIMESTAMP '2060-11-03 12:01:00',TIMESTAMP '2060-11-03 13:00:00',?)",UUID.randomUUID(),window,report,"report:"+report+":1",request);
                }
                new V30__AddIncidentDelivery().migrate(migration);
            }
            try (var connection=DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()) {
                try(var rows=sql.executeQuery("SELECT operation,report_id,captured_at,party_label,nonce FROM incident_deliveries")) {
                    int count=0, receipts=0, detailed=0;
                    while(rows.next()) {
                        count++; assertThat(UUID.fromString(rows.getString("report_id"))).isNotEqualTo(unrequested);
                        assertThat(rows.getString("nonce")).hasSizeLessThanOrEqualTo(25);
                        if(rows.getString("operation").equals("RECEIPT")) { receipts++; assertThat(rows.getString("party_label")).isNull(); assertThat(rows.getTimestamp("captured_at").toLocalDateTime()).isEqualTo(java.time.LocalDateTime.of(2060,11,3,12,1)); }
                        else { detailed++; assertThat(UUID.fromString(rows.getString("report_id"))).isEqualTo(active); assertThat(rows.getString("party_label")).isEqualTo("Test private party"); }
                    }
                    assertThat(count).isEqualTo(3); assertThat(receipts).isEqualTo(2); assertThat(detailed).isEqualTo(1);
                }
                try(var rows=sql.executeQuery("SELECT delivery_paused FROM incident_report_windows")) { assertThat(rows.next()).isTrue(); assertThat(rows.getBoolean(1)).isFalse(); }
            }
        } finally { try(var connection=DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()) { sql.execute("SHUTDOWN"); } }
    }
    private static void execute(Connection connection,String sql,Object... parameters) throws SQLException {
        try(var statement=connection.prepareStatement(sql)) { for(int i=0;i<parameters.length;i++) statement.setObject(i+1,parameters[i]); statement.executeUpdate(); }
    }
}
