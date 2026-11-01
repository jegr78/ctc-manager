package db.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class V23CarMetadataMigrationTest {
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> invalidMetadata() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("category", "BOGUS", "GR_3", "CK_CARS_CATEGORY"),
                org.junit.jupiter.params.provider.Arguments.of("drivetrain", "AWD", "FOUR_WD", "CK_CARS_DRIVETRAIN"),
                org.junit.jupiter.params.provider.Arguments.of("model_year", 99, 1929, "CK_CARS_MODEL_YEAR"),
                org.junit.jupiter.params.provider.Arguments.of("proposed_model_year", 10000, 2025, "CK_CARS_PROPOSED_MODEL_YEAR"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("invalidMetadata")
    void givenMetadataConstraints_whenWritingInvalidValue_thenDatabaseRejectsIt(String column, Object invalid, Object valid, String constraint) throws Exception {
        // given
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:metadata-" + java.util.UUID.randomUUID() + ";MODE=MariaDB")) {
            var sql = connection.createStatement();
            sql.execute("CREATE TABLE cars (id VARCHAR(36))");
            for (String statement : Files.readString(Path.of("src/main/resources/db/migration/V23__add_reviewed_car_metadata.sql")).split(";"))
                if (!statement.isBlank()) sql.execute(statement);
            try (var insert = connection.prepareStatement("INSERT INTO cars (id, " + column + ") VALUES ('valid', ?)")) {
                insert.setObject(1, valid);
                insert.executeUpdate();
            }
            // when / then
            assertThatThrownBy(() -> {
                try (var insert = connection.prepareStatement("INSERT INTO cars (id, " + column + ") VALUES ('invalid', ?)")) {
                    insert.setObject(1, invalid);
                    insert.executeUpdate();
                }
            }).isInstanceOf(java.sql.SQLException.class).hasMessageContaining(constraint);
        }
    }

    @Test
    void givenLegacyCar_whenMigrating_thenIdentityAndUnknownDefaultsArePreserved() throws Exception {
        // given
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:car-metadata-defaults;MODE=MariaDB")) {
            var sql = connection.createStatement();
            sql.execute("CREATE TABLE cars (id VARCHAR(36), gt7_id VARCHAR(32), image_url VARCHAR(500))");
            sql.execute("INSERT INTO cars VALUES ('11111111-1111-1111-1111-111111111111','car3531','/uploads/test-original.png')");
            // when
            for (String statement : Files.readString(Path.of("src/main/resources/db/migration/V23__add_reviewed_car_metadata.sql")).split(";"))
                if (!statement.isBlank()) sql.execute(statement);
            // then
            var row = sql.executeQuery("SELECT * FROM cars");
            assertThat(row.next()).isTrue();
            assertThat(row.getString("gt7_id")).isEqualTo("car3531");
            assertThat(row.getString("image_url")).isEqualTo("/uploads/test-original.png");
            assertThat(row.getObject("category")).isNull();
            assertThat(row.getObject("drivetrain")).isNull();
            assertThat(row.getObject("model_year")).isNull();
            assertThat(row.getBoolean("model_year_override")).isFalse();
            assertThat(row.getBoolean("category_override")).isFalse();
            assertThat(row.getBoolean("drivetrain_override")).isFalse();
        }
    }
}
