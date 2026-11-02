package db.migration;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class V25CarMetadataConnectionMigrationTest {
    @ParameterizedTest
    @CsvSource({"category,GR_3,CK_CARS_CATEGORY", "drivetrain,MR,CK_CARS_DRIVETRAIN"})
    void givenLegacyCarAndClosedMigrationConnection_whenMetadataIsUpdated_thenValidUnknownAndInvalidValuesKeepTheirRules(
            String column, String valid, String constraint) throws Exception {
        // given
        String url = "jdbc:h2:mem:metadata-upgrade-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("22").load().migrate();
        try {
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
                sql.execute("INSERT INTO cars (id,manufacturer,name,gt7_id,image_url) VALUES ('11111111-1111-1111-1111-111111111111','Test metadata connection','Legacy','car3531','/uploads/legacy.png')");
            }
            Flyway.configure().dataSource(url, "sa", "").load().migrate();
            // when / then
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
                assertThatCode(() -> sql.executeUpdate("UPDATE cars SET " + column + "='" + valid + "'"))
                        .as("accepted metadata remains writable after the migration connection closes").doesNotThrowAnyException();
                assertThatThrownBy(() -> sql.executeUpdate("UPDATE cars SET " + column + "='INVALID'"))
                        .isInstanceOf(java.sql.SQLException.class).hasMessageContaining(constraint);
                try (var row = sql.executeQuery("SELECT * FROM cars")) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString(column)).isEqualTo(valid);
                    assertThat(row.getString("gt7_id")).isEqualTo("car3531");
                    assertThat(row.getString("image_url")).isEqualTo("/uploads/legacy.png");
                    assertThat(row.next()).isFalse();
                }
                assertThatCode(() -> sql.executeUpdate("UPDATE cars SET " + column + "=NULL"))
                        .as("unknown metadata remains valid").doesNotThrowAnyException();
            }
        } finally {
            try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
                sql.execute("SHUTDOWN");
            }
        }
    }
}
