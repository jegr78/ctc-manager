package db.migration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class V22TimeProgressionMigrationTest {
    @Test
    void givenLegacyValues_whenMigrating_thenNumericValuesAndIncompleteNullSurvive() throws Exception {
        // given
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:time-mode;MODE=MariaDB")) {
            var statement = connection.createStatement();
            statement.execute("CREATE TABLE race_settings (time_progression_multiplier INTEGER)");
            statement.execute("INSERT INTO race_settings VALUES (0), (6), (60), (NULL)");
            // when
            statement.execute(Files.readString(Path.of("src/main/resources/db/migration/V22__add_time_progression_mode.sql")));
            // then
            var rows = statement.executeQuery("SELECT * FROM race_settings");
            for (Integer expected : new Integer[]{0, 6, 60, null}) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getObject("time_progression_multiplier")).isEqualTo(expected);
                assertThat(rows.getString("time_progression_mode")).isEqualTo("MULTIPLIER");
            }
        }
    }
}
