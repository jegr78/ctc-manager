package db.migration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

@CtcDevSpringBootContext
@Tag("integration")
class V46CatalogImageMigrationIT {
    @Autowired DataSource dataSource;
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    UUID car;
    UUID track;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        car = cars.saveAndFlush(new Car("Test", "V46 car " + suffix)).getId();
        track = tracks.saveAndFlush(new Track("V46 track " + suffix)).getId();
    }

    @AfterEach
    void cleanUp() {
        cars.deleteById(car);
        tracks.deleteById(track);
    }

    @ParameterizedTest
    @CsvSource({"STUDIO,GT7,2_01", "SCENE,GT7,1_01", "COCKPIT,MANUAL,", "OTHER,MANUAL,"})
    void givenNewConnection_whenStoringValidCarImages_thenAccept(String kind, String source, String key) {
        // when / then
        assertThatCode(() -> carImage(kind, source, key, 640)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"BACKGROUND,MANUAL,,640", "STUDIO,GT7,,640", "STUDIO,GT7,' ',640", "STUDIO,MANUAL,2_01,640", "STUDIO,WEB,,640", "STUDIO,MANUAL,,0"})
    void givenNewConnection_whenStoringInvalidCarImages_thenReject(String kind, String source, String key, int width) {
        // when / then
        assertThatThrownBy(() -> carImage(kind, source, key, width)).isInstanceOf(SQLException.class);
    }

    @Test
    void givenGt7Slot_whenStoringItTwice_thenReject() throws SQLException {
        // given
        carImage("STUDIO", "GT7", "2_01", 640);
        // when / then
        assertThatThrownBy(() -> carImage("SCENE", "GT7", "2_01", 640)).isInstanceOf(SQLException.class);
        assertThatCode(() -> carImage("STUDIO", "MANUAL", null, 640)).as("manual images have no slot").doesNotThrowAnyException();
        assertThatCode(() -> carImage("STUDIO", "MANUAL", null, 640)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"BACKGROUND,true", "LAYOUT,true", "OTHER,true", "STUDIO,false"})
    void givenNewConnection_whenStoringTrackImageKinds_thenEnforceThem(String kind, boolean valid) {
        // when
        org.assertj.core.api.ThrowableAssert.ThrowingCallable insert = () -> execute("""
                INSERT INTO track_images (id, track_id, kind, source, source_key, image_path, width, height, sort_index, created_at, updated_at)
                VALUES (?, ?, ?, 'MANUAL', NULL, '/uploads/track-images/x.png', 10, 10, 0, ?, ?)""",
                UUID.randomUUID().toString(), track.toString(), kind, now(), now());
        // then
        if (valid) assertThatCode(insert).doesNotThrowAnyException(); else assertThatThrownBy(insert).isInstanceOf(SQLException.class);
    }

    private void carImage(String kind, String source, String key, int width) throws SQLException {
        execute("""
                INSERT INTO car_images (id, car_id, kind, source, source_key, image_path, width, height, sort_index, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, '/uploads/car-images/x.jpg', ?, 10, 0, ?, ?)""",
                UUID.randomUUID().toString(), car.toString(), kind, source, key, width, now(), now());
    }

    private void execute(String sql, Object... values) throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    private static Timestamp now() { return Timestamp.valueOf(LocalDateTime.of(2035, 1, 1, 12, 0)); }
}
