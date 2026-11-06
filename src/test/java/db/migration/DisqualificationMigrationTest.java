package db.migration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DisqualificationMigrationTest {
    @Test
    void givenVersion37Result_whenMigrating_thenDefaultToClassifiedWithoutChangingOriginalPoints() throws Exception {
        // given
        try (var data = fixture()) {
            // when
            data.migrate();
            // then
            assertThatCode(() -> {
                try (var c = data.open(); var q = c.createStatement(); var r = q.executeQuery(
                        "select disqualified,position,quali_position,points_race,points_quali,points_fl,points_total from race_results")) {
                    assertThat(r.next()).isTrue(); assertThat(r.getBoolean(1)).isFalse();
                    assertThat(r.getInt(2)).isEqualTo(3); assertThat(r.getInt(3)).isEqualTo(3);
                    assertThat(r.getInt(4)).isEqualTo(14); assertThat(r.getInt(5)).isEqualTo(1);
                    assertThat(r.getInt(6)).isZero(); assertThat(r.getInt(7)).isEqualTo(15);
                }
            }).as("legacy results migrate with a false DSQ flag and exact saved classification/points").doesNotThrowAnyException();
        }
    }

    @Test
    void givenClosedMigrationConnection_whenWritingDisqualification_thenRequireZeroRacePointsAndANonNullFlag() throws Exception {
        // given
        try (var data = fixture()) {
            data.migrate();
            // when / then
            assertThatCode(() -> data.update("disqualified=true,points_race=0,points_total=1"))
                    .as("a valid DSQ keeps its qualifying bonus on a new connection").doesNotThrowAnyException();
            assertThatThrownBy(() -> data.update("points_race=14")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> data.update("disqualified=NULL")).isInstanceOf(SQLException.class);
            assertThatCode(() -> data.update("disqualified=false,points_race=14,points_total=15")).doesNotThrowAnyException();
        }
    }

    @Test
    void givenPermanentDisqualificationHistory_whenWritingMetadata_thenEnforceIdentitySequenceAndReasons() throws Exception {
        // given
        try (var data = fixture()) {
            data.migrate();
            // when / then
            assertThatCode(() -> data.history(1, false, true, "Test steward decision", "test-manager", "a".repeat(64), data.result))
                    .as("a complete first DSQ revision is storable").doesNotThrowAnyException();
            assertThatThrownBy(() -> data.history(1, false, true, "Test duplicate", "test-manager", "a".repeat(64), data.result))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> data.history(0, false, true, "Test invalid sequence", "test-manager", "a".repeat(64), data.result))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> data.history(2, true, false, "  ", "test-manager", "a".repeat(64), data.result))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> data.history(2, true, false, "Test clear", "", "a".repeat(64), data.result))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> data.history(2, true, false, "Test clear", "test-manager", "short", data.result))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> data.history(2, true, false, "Test foreign result", "test-manager", "a".repeat(64), UUID.randomUUID()))
                    .isInstanceOf(SQLException.class);
            assertThatCode(() -> data.history(2, true, false, "Test DSQ cleared", "test-manager", "b".repeat(64), data.result))
                    .doesNotThrowAnyException();
        }
    }

    private static Fixture fixture() throws Exception {
        var data = new Fixture();
        Flyway.configure().dataSource(data.url, "sa", "").target("37").load().migrate();
        try (var c = data.open(); var sql = c.createStatement()) {
            sql.execute("insert into seasons(id,name,season_year,season_number) values('" + data.season + "','Test legacy DSQ',2027,1)");
            sql.execute("insert into season_phases(id,season_id,sort_index,phase_type,layout) values('" + data.phase + "','" + data.season + "',1,'REGULAR','LEAGUE')");
            sql.execute("insert into matchdays(id,phase_id,label,sort_index) values('" + data.day + "','" + data.phase + "','Test day',1)");
            sql.execute("insert into teams(id,name,short_name) values('" + data.home + "','Test home','TH'),('" + data.away + "','Test away','TA')");
            sql.execute("insert into matches(id,matchday_id,home_team_id,away_team_id) values('" + data.match + "','" + data.day + "','" + data.home + "','" + data.away + "')");
            sql.execute("insert into races(id,matchday_id,match_id) values('" + data.race + "','" + data.day + "','" + data.match + "')");
            sql.execute("insert into drivers(id,psn_id,nickname) values('" + data.driver + "','test-dsq-driver','Test driver')");
            sql.execute("insert into race_results(id,race_id,driver_id,position,quali_position,points_race,points_quali,points_total) values('"
                    + data.result + "','" + data.race + "','" + data.driver + "',3,3,14,1,15)");
        }
        return data;
    }

    private static final class Fixture implements AutoCloseable {
        final String url = "jdbc:h2:mem:dsq-migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        final UUID season=UUID.randomUUID(), phase=UUID.randomUUID(), day=UUID.randomUUID(), home=UUID.randomUUID(),
                away=UUID.randomUUID(), match=UUID.randomUUID(), race=UUID.randomUUID(), driver=UUID.randomUUID(), result=UUID.randomUUID();
        Connection open() throws SQLException { return DriverManager.getConnection(url,"sa",""); }
        void migrate() { Flyway.configure().dataSource(url,"sa","").load().migrate(); }
        void update(String values) throws SQLException {
            try(var c=open();var sql=c.createStatement()) { assertThat(sql.executeUpdate("update race_results set " + values)).isEqualTo(1); }
        }
        void history(int number, boolean previous, boolean disqualified, String reason, String actor, String hash, UUID resultId) throws SQLException {
            String fields="id,result_id,race_id,driver_id,season_id,phase_id,matchday_id,match_id,playoff_matchup_id,home_team_id,away_team_id,revision_number,previous_disqualified,disqualified,recorded_position,reason,actor_id,result_fingerprint,created_at,updated_at";
            var time=Timestamp.valueOf("2026-11-05 12:00:00.123456");
            var values=Arrays.asList(UUID.randomUUID(),resultId,race,driver,season,phase,day,match,null,home,away,number,previous,disqualified,3,reason,actor,hash,time,time);
            try(var c=open();var insert=c.prepareStatement("insert into race_disqualification_revisions("+fields+") values("+String.join(",",Collections.nCopies(values.size(),"?"))+")")) {
                for(int i=0;i<values.size();i++)insert.setObject(i+1,values.get(i));
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
        }
        @Override public void close() throws SQLException { try(var c=open();var sql=c.createStatement()){sql.execute("SHUTDOWN");} }
    }
}
