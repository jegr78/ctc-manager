package db.migration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import javax.sql.DataSource;
import org.ctc.domain.model.Car;
import org.ctc.domain.repository.CarRepository;
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
class V49CarImageCutoutMigrationIT {
    @Autowired DataSource dataSource;
    @Autowired CarRepository cars;
    UUID car;

    @BeforeEach
    void setUp() {
        car = cars.saveAndFlush(new Car("Test", "V49 car " + UUID.randomUUID().toString().substring(0, 8))).getId();
    }

    @AfterEach
    void cleanUp() throws SQLException {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("DELETE FROM car_images WHERE car_id = ?")) {
            statement.setObject(1, car.toString());
            statement.executeUpdate();
        }
        cars.deleteById(car);
    }

    @ParameterizedTest
    @CsvSource({"CUTOUT,AUTO,cutout", "CUTOUT,MANUAL,", "STUDIO,GT7,2_01"})
    void givenV49_whenStoringValidImages_thenAccept(String kind, String source, String key) {
        // when / then
        assertThatCode(() -> carImage(kind, source, key)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"CUTOUT,AUTO,other", "CUTOUT,AUTO,", "STUDIO,AUTO,cutout", "WATERMARK,MANUAL,"})
    void givenV49_whenInsertAutoWithOtherKeyOrKind_thenCheckViolation(String kind, String source, String key) {
        // when / then
        assertThatThrownBy(() -> carImage(kind, source, key)).isInstanceOf(SQLException.class);
    }

    @Test
    void givenV49_whenInsertSecondAutoCutoutForCar_thenUniqueViolation() throws SQLException {
        // given
        carImage("CUTOUT", "AUTO", "cutout");
        // when / then
        assertThatThrownBy(() -> carImage("CUTOUT", "AUTO", "cutout")).isInstanceOf(SQLException.class);
    }

    private void carImage(String kind, String source, String key) throws SQLException {
        String sql = "INSERT INTO car_images (id, car_id, kind, source, source_key, image_path, width, height, sort_index, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, '/uploads/car-images/x.png', 10, 10, 0, ?, ?)";
        var now = Timestamp.valueOf(LocalDateTime.of(2035, 1, 1, 12, 0));
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID().toString());
            statement.setObject(2, car.toString());
            statement.setString(3, kind);
            statement.setString(4, source);
            statement.setString(5, key);
            statement.setTimestamp(6, now);
            statement.setTimestamp(7, now);
            statement.executeUpdate();
        }
    }
}
