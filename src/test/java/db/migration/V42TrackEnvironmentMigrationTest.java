package db.migration;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class V42TrackEnvironmentMigrationTest {
    @Test void givenLegacyLayouts_whenUpgrading_thenProtectConfiguredValuesAndRetainPristineImportEligibility()throws Exception {
        String url="jdbc:h2:mem:environment-upgrade-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url,"sa","").target("41").load().migrate();
        try {
            try(var connection=DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()) {
                sql.execute("INSERT INTO tracks(id,name,image_url,time_of_day_options,rain_capability,environment_version) VALUES ('11111111-1111-1111-1111-111111111111','Test pristine','/uploads/test.png',NULL,'UNKNOWN',0),('22222222-2222-2222-2222-222222222222','Test times',NULL,'Night','UNKNOWN',0),('33333333-3333-3333-3333-333333333333','Test rain',NULL,NULL,'RAIN',0),('44444444-4444-4444-4444-444444444444','Test manual unknown',NULL,NULL,'UNKNOWN',1)");
            }
            Flyway.configure().dataSource(url,"sa","").load().migrate();
            try(var connection=DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()) {
                try(var rows=sql.executeQuery("SELECT * FROM tracks ORDER BY id")) {
                    assertThat(rows.next()).isTrue();assertThat(rows.getBoolean("environment_override")).isFalse();assertThat(rows.getString("image_url")).isEqualTo("/uploads/test.png");
                    for(int index=0;index<3;index++){assertThat(rows.next()).isTrue();assertThat(rows.getBoolean("environment_override")).isTrue();assertThat(rows.getString("environment_source")).isNull();assertThat(rows.getString("environment_import_hash")).isNull();}
                    assertThat(rows.next()).isFalse();
                }
                assertThatCode(()->sql.executeUpdate("INSERT INTO tracks(id,name,environment_override) VALUES ('55555555-5555-5555-5555-555555555555','Test fresh false',FALSE),('66666666-6666-6666-6666-666666666666','Test fresh true',TRUE)"))
                    .as("boolean checks remain valid after the migration connection closes").doesNotThrowAnyException();
                assertThatThrownBy(()->sql.executeUpdate("UPDATE tracks SET environment_import_hash='"+"a".repeat(64)+"' WHERE name='Test pristine'")).isInstanceOf(java.sql.SQLException.class).satisfies(failure->assertThat(failure.getMessage()).containsIgnoringCase("chk_track_environment_source"));
            }
        }finally{try(var connection=DriverManager.getConnection(url,"sa","");var sql=connection.createStatement()){sql.execute("SHUTDOWN");}}
    }
}
