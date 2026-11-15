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
                .hasMessage("A downloaded image needs its source key and a manual image must not have one");
        assertThatThrownBy(() -> validator(24).accept("car_images", carImage("MANUAL", "2_01", "/uploads/car-images/a/1.jpg")))
                .hasMessage("A downloaded image needs its source key and a manual image must not have one");
        assertThatThrownBy(() -> validator(24).accept("car_images", carImage("MANUAL", " ", "/uploads/car-images/a/1.jpg")))
                .as("the database accepts only a null key for manual images").hasMessage("A downloaded image needs its source key and a manual image must not have one");
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
    void givenGtEngineMapsInSchema25_whenValidating_thenAcceptOneMapPerTrack() {
        // given
        var validator = validator(25);
        validator.accept("track_images", trackImage().put("source", "GTENGINE").put("sourceKey", "map"));
        // when / then
        assertThatThrownBy(() -> validator.accept("track_images", trackImage().put("source", "GTENGINE").put("sourceKey", "other")))
                .hasMessage("A track has two GT-Engine maps");
        assertThatThrownBy(() -> validator(25).accept("track_images", trackImage().put("source", "GTENGINE").putNull("sourceKey")))
                .hasMessage("A downloaded image needs its source key and a manual image must not have one");
    }

    @Test
    void givenGtEngineMapInAnOlderSchemaOrOnACar_whenValidating_thenReject() {
        // when / then
        assertThatThrownBy(() -> validator(24).accept("track_images", trackImage().put("source", "GTENGINE").put("sourceKey", "map")))
                .hasMessage("GT-Engine maps belong to tracks and require backup schema 25");
        assertThatThrownBy(() -> validator(25).accept("car_images", carImage("GTENGINE", "map", "/uploads/car-images/a/1.jpg")))
                .hasMessage("GT-Engine maps belong to tracks and require backup schema 25");
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
    void givenValuesLongerThanTheirColumns_whenValidating_thenReject() {
        // when / then
        assertThatThrownBy(() -> validator(24).accept("car_images", carImage("GT7", "k".repeat(33), "/uploads/car-images/a/1.jpg")))
                .hasMessage("An image source key exceeds 32 characters");
        String longPath = "/uploads/car-images/a/" + "x".repeat(480) + ".jpg";
        var validator = new CatalogImageBackupValidator(24, Set.of(longPath.substring(1)));
        validator.accept("cars", JSON.createObjectNode().put("id", car.toString()));
        assertThatThrownBy(() -> validator.accept("car_images", carImage("MANUAL", null, longPath))).hasMessageContaining("is missing from the archive");
    }

    @Test
    void givenMissingTimestampDuplicateIdOrOversizedNumber_whenValidating_thenRejectBeforeTheWipe() {
        // given
        var image = carImage("MANUAL", null, "/uploads/car-images/a/1.jpg");
        var validator = validator(24);
        validator.accept("car_images", image);
        // when / then
        assertThatThrownBy(() -> validator.accept("car_images", image)).hasMessage("Image " + image.get("id").asText() + " appears twice");
        assertThatThrownBy(() -> validator(24).accept("car_images", carImage("MANUAL", null, "/uploads/car-images/a/1.jpg").putNull("createdAt")))
                .hasMessage("Invalid image timestamp createdAt");
        assertThatThrownBy(() -> validator(24).accept("car_images", carImage("MANUAL", null, "/uploads/car-images/a/1.jpg").put("updatedAt", "yesterday")))
                .hasMessage("Invalid image timestamp updatedAt");
        assertThatThrownBy(() -> validator(24).accept("car_images", carImage("MANUAL", null, "/uploads/car-images/a/1.jpg").put("width", 4294967297L)))
                .as("a long would truncate to 1 in the restorer").hasMessageContaining("needs a positive size");
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
                .put("source", source).put("sourceKey", key).put("imagePath", path).put("width", 640).put("height", 360).put("sortIndex", 0)
                .put("createdAt", "2035-01-01T12:00:00.123456").put("updatedAt", "2035-01-01T12:00:00.123456");
    }

    private ObjectNode trackImage() {
        return JSON.createObjectNode().put("id", UUID.randomUUID().toString()).put("track", track.toString()).put("kind", "LAYOUT")
                .put("source", "MANUAL").putNull("sourceKey").put("imagePath", "/uploads/track-images/b/1.png").put("width", 640).put("height", 360).put("sortIndex", 0)
                .put("createdAt", "2035-01-01T12:00:00.123456").put("updatedAt", "2035-01-01T12:00:00.123456");
    }
}
