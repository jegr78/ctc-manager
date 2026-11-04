package db.migration;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V33ActiveSeasonMigrationTest {
    @Test
    void givenSeveralLegacyActiveSeasons_whenMigrated_thenKeepTheLatestAndRejectASecondActivation() throws Exception {
        String url = "jdbc:h2:mem:active-upgrade-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try {
            verifyMigration(url, "sa", "");
        } finally {
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
                sql.execute("SHUTDOWN");
            }
        }
    }

    static void verifyMigration(String url, String user, String password) throws Exception {
        // given
        Flyway.configure().dataSource(url, user, password).target("32").load().migrate();
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            sql.execute("INSERT INTO seasons (id,name,season_year,season_number,active) VALUES "
                    + "('11111111-1111-1111-1111-111111111111','Test older',2030,1,TRUE),"
                    + "('22222222-2222-2222-2222-222222222222','Test latest',2031,2,TRUE)");
        }
        // when
        Flyway.configure().dataSource(url, user, password).load().migrate();
        // then
        try (var connection = DriverManager.getConnection(url, user, password); var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("SELECT name FROM seasons WHERE active = TRUE")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("Test latest");
                assertThat(rows.next()).isFalse();
            }
            assertThatThrownBy(() -> sql.execute("UPDATE seasons SET active=TRUE WHERE name='Test older'"))
                    .isInstanceOf(java.sql.SQLException.class);
            sql.execute("UPDATE seasons SET active=FALSE");
            sql.execute("UPDATE seasons SET active=TRUE WHERE name='Test older'");
        }
    }
}
