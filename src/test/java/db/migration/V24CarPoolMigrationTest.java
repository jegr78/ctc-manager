package db.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V24CarPoolMigrationTest {
    @Test
    void givenMigrationConnectionClosed_whenAnotherConnectionWrites_thenPoolConstraintsStillWork() throws Exception {
        // given
        String url = "jdbc:h2:mem:pool-migration-" + UUID.randomUUID() + ";MODE=MariaDB;DB_CLOSE_DELAY=-1";
        try (var connection = DriverManager.getConnection(url); var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE seasons (id UUID PRIMARY KEY)");
            sql.execute("CREATE TABLE cars (id UUID PRIMARY KEY)");
            sql.execute("CREATE TABLE races (id UUID PRIMARY KEY)");
            for (String statement : Files.readString(Path.of("src/main/resources/db/migration/V24__add_season_car_pools.sql")).split(";"))
                if (!statement.isBlank()) sql.execute(statement);
        }
        // when / then
        try (var connection = DriverManager.getConnection(url); var sql = connection.createStatement()) {
            assertThatCode(() -> {
                sql.execute("INSERT INTO seasons VALUES ('11111111-1111-1111-1111-111111111111', 'CATEGORY_POOL', 'FROZEN')");
                sql.execute("INSERT INTO season_car_pool_entries (id,season_id,label,sort_index) VALUES ('22222222-2222-2222-2222-222222222222','11111111-1111-1111-1111-111111111111','Test pool',0)");
                sql.execute("INSERT INTO season_car_pool_clauses (id,entry_id,category,drivetrain,model_year,sort_index) VALUES (RANDOM_UUID(),'22222222-2222-2222-2222-222222222222','GR_3','MR',2016,0)");
            }).as("valid values remain writable after the migration connection closes").doesNotThrowAnyException();
            assertThatThrownBy(() -> sql.execute("UPDATE seasons SET car_pool_mode='INVALID'"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("CHK_SEASON_CAR_POOL_MODE");
            assertThatThrownBy(() -> sql.execute("UPDATE seasons SET car_pool_state='INVALID'"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("CHK_SEASON_CAR_POOL_STATE");
            assertThatThrownBy(() -> sql.execute("UPDATE season_car_pool_clauses SET category='INVALID'"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("CHK_CAR_POOL_CLAUSE_CATEGORY");
            assertThatThrownBy(() -> sql.execute("UPDATE season_car_pool_clauses SET drivetrain='INVALID'"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("CHK_CAR_POOL_CLAUSE_DRIVETRAIN");
        } finally {
            try (var connection = DriverManager.getConnection(url); var sql = connection.createStatement()) {
                sql.execute("SHUTDOWN");
            }
        }
    }
}
