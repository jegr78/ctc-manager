package org.ctc.backup.schema;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogImageBackupValidatorTest {
    static final ObjectMapper JSON = new ObjectMapper();
    final UUID car = UUID.randomUUID();
    final UUID track = UUID.randomUUID();

    @Test
    void givenCompleteRows_whenValidating_thenAccept() {
        // given
        var validator = validator(24);
        // when / then
        assertThatCode(() -> {
            validator.accept("car_images", carImage("GT7", "2_01", "/uploads/car-images/a/1.jpg"));
            validator.accept("car_images", carImage("MANUAL", null, "/uploads/car-images/a/2.png"));
            validator.accept("track_images", trackImage());
            validator.validate();
        }).doesNotThrowAnyException();
    }

    @Test
    void givenImageRowsInAnOlderSchema_whenValidating_thenReject() {
        // when / then
        assertThatThrownBy(() -> validator(23).accept("car_images", carImage("MANUAL", null, "/uploads/car-images/a/2.png")))
                .hasMessage("Gallery images require backup schema 24");
    }

    @Test
    void givenSourceAndKeyThatDoNotPair_whenValidating_thenReject() {
        // when / then
        assertThatThrownBy(() -> validator(24).accept("car_images", carImage("GT7", null, "/uploads/car-images/a/1.jpg")))
                .hasMessage("A GT7 image needs its source key and a manual image must not have one");
        assertThatThrownBy(() -> validator(24).accept("car_images", carImage("MANUAL", "2_01", "/uploads/car-images/a/1.jpg")))
                .hasMessage("A GT7 image needs its source key and a manual image must not have one");
    }

    @Test
    void givenTwoImagesForOneGt7Slot_whenValidating_thenReject() {
        // given
        var validator = validator(24);
        validator.accept("car_images", carImage("GT7", "2_01", "/uploads/car-images/a/1.jpg"));
        // when / then
        assertThatThrownBy(() -> validator.accept("car_images", carImage("GT7", "2_01", "/uploads/car-images/a/2.png")))
                .hasMessage("A car has two GT7 images for slot 2_01");
    }

    @Test
    void givenFileMissingFromTheArchive_whenValidating_thenReject() {
        // when / then
        assertThatThrownBy(() -> validator(24).accept("car_images", carImage("MANUAL", null, "/uploads/car-images/a/gone.png")))
                .hasMessageContaining("is missing from the archive");
        assertThatThrownBy(() -> validator(24).accept("car_images", carImage("MANUAL", null, "/elsewhere/a/1.jpg")))
                .hasMessageContaining("is missing from the archive");
    }

    @Test
    void givenUnknownKindOrInvalidSize_whenValidating_thenReject() {
        // given
        var unknownKind = carImage("MANUAL", null, "/uploads/car-images/a/1.jpg").put("kind", "BACKGROUND");
        var zeroWidth = carImage("MANUAL", null, "/uploads/car-images/a/1.jpg").put("width", 0);
        // when / then
        assertThatThrownBy(() -> validator(24).accept("car_images", unknownKind)).hasMessage("Unknown image kind: BACKGROUND");
        assertThatThrownBy(() -> validator(24).accept("car_images", zeroWidth)).hasMessageContaining("needs a positive size");
    }

    @Test
    void givenImageOfAMissingOwner_whenValidating_thenReject() {
        // given
        var validator = new CatalogImageBackupValidator(24, Set.of("uploads/car-images/a/1.jpg"));
        validator.accept("car_images", carImage("MANUAL", null, "/uploads/car-images/a/1.jpg"));
        // when / then
        assertThatThrownBy(validator::validate).hasMessage("A car image references a missing car");
    }

    private CatalogImageBackupValidator validator(int version) {
        var validator = new CatalogImageBackupValidator(version,
                Set.of("uploads/car-images/a/1.jpg", "uploads/car-images/a/2.png", "uploads/track-images/b/1.png"));
        validator.accept("cars", JSON.createObjectNode().put("id", car.toString()));
        validator.accept("tracks", JSON.createObjectNode().put("id", track.toString()));
        return validator;
    }

    private ObjectNode carImage(String source, String key, String path) {
        return JSON.createObjectNode().put("id", UUID.randomUUID().toString()).put("car", car.toString()).put("kind", "STUDIO")
                .put("source", source).put("sourceKey", key).put("imagePath", path).put("width", 640).put("height", 360).put("sortIndex", 0);
    }

    private ObjectNode trackImage() {
        return JSON.createObjectNode().put("id", UUID.randomUUID().toString()).put("track", track.toString()).put("kind", "LAYOUT")
                .put("source", "MANUAL").putNull("sourceKey").put("imagePath", "/uploads/track-images/b/1.png").put("width", 640).put("height", 360).put("sortIndex", 0);
    }
}
