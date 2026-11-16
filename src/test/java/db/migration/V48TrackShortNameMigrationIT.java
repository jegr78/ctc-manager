package db.migration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@CtcDevSpringBootContext
@Tag("integration")
class V48TrackShortNameMigrationIT {
    @Autowired DataSource dataSource;
    @Autowired TrackRepository tracks;
    UUID track;

    @BeforeEach
    void setUp() {
        track = tracks.saveAndFlush(new Track("V48 track " + UUID.randomUUID().toString().substring(0, 8))).getId();
    }

    @AfterEach
    void cleanUp() {
        tracks.deleteById(track);
    }

    @Test
    void givenV48_whenStoring30Chars_thenAccepted() {
        // when / then
        assertThatCode(() -> setShortName("X".repeat(30))).doesNotThrowAnyException();
        assertThatCode(() -> setShortName(null)).as("short name is optional").doesNotThrowAnyException();
    }

    @Test
    void givenV48_whenInsert31Chars_thenRejected() {
        // when / then
        assertThatThrownBy(() -> setShortName("X".repeat(31))).isInstanceOf(SQLException.class);
    }

    private void setShortName(String value) throws SQLException {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("UPDATE tracks SET display_short_name = ? WHERE id = ?")) {
            statement.setString(1, value);
            statement.setObject(2, track.toString());
            statement.executeUpdate();
        }
    }
}
