package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.ctc.domain.model.CarImageKind;
import org.ctc.domain.model.ImageSource;
import org.ctc.domain.model.TrackImageKind;

/**
 * Bounded prewipe checks for car and track gallery images: schema 24 or later, existing owners, known kinds and
 * sources, a GT7 source key exactly for GT7 images, one image per GT7 slot and owner, and the file in the archive.
 */
public final class CatalogImageBackupValidator {
    public static final int IMAGE_VERSION = 24;
    private static final Set<String> TABLES = Set.of("cars", "tracks", "car_images", "track_images");
    private static final int BUDGET = 100_000;
    private final int version;
    private final Set<String> archivedUploads;
    private final Set<UUID> cars = new HashSet<>();
    private final Set<UUID> tracks = new HashSet<>();
    private final Set<String> slots = new HashSet<>();
    private final List<UUID[]> carOwners = new ArrayList<>();
    private final List<UUID[]> trackOwners = new ArrayList<>();
    private int retained;

    /** @param archivedUploads the archive entry names below {@code uploads/} */
    public CatalogImageBackupValidator(int version, Set<String> archivedUploads) {
        this.version = version;
        this.archivedUploads = Set.copyOf(archivedUploads);
    }

    public void accept(String table, JsonNode row) {
        if (!TABLES.contains(table)) return;
        UUID id = uuid(row, "id");
        switch (table) {
            case "cars" -> cars.add(id);
            case "tracks" -> tracks.add(id);
            case "car_images" -> image(row, id, "car", CarImageKind.class, carOwners);
            case "track_images" -> image(row, id, "track", TrackImageKind.class, trackOwners);
            default -> throw new IllegalStateException("Unexpected image table");
        }
        if (++retained > BUDGET) throw new IllegalArgumentException("Image validation exceeds the " + BUDGET + "-reference budget");
    }

    public void validate() {
        for (var owner : carOwners) if (!cars.contains(owner[0])) throw new IllegalArgumentException("A car image references a missing car");
        for (var owner : trackOwners) if (!tracks.contains(owner[0])) throw new IllegalArgumentException("A track image references a missing track");
    }

    private <E extends Enum<E>> void image(JsonNode row, UUID id, String owner, Class<E> kinds, List<UUID[]> owners) {
        if (version < IMAGE_VERSION) throw new IllegalArgumentException("Gallery images require backup schema " + IMAGE_VERSION);
        UUID ownerId = uuid(row, owner);
        enumValue(row, "kind", kinds);
        var source = enumValue(row, "source", ImageSource.class);
        String key = text(row, "sourceKey");
        boolean paired = source == ImageSource.GT7 ? key != null && !key.isBlank() : key == null;
        if (!paired) {
            throw new IllegalArgumentException("A GT7 image needs its source key and a manual image must not have one");
        }
        if (source == ImageSource.GT7 && !slots.add(owner + ':' + ownerId + ':' + key)) {
            throw new IllegalArgumentException("A " + owner + " has two GT7 images for slot " + key);
        }
        String label = text(row, "label");
        if (label != null && label.length() > 100) throw new IllegalArgumentException("An image label exceeds 100 characters");
        if (key != null && key.length() > 32) throw new IllegalArgumentException("An image source key exceeds 32 characters");
        if (positive(row, "width") && positive(row, "height") && row.path("sortIndex").isIntegralNumber()) {
            String path = text(row, "imagePath");
            if (path == null || path.length() > 500 || !path.startsWith("/uploads/") || !archivedUploads.contains("uploads/" + path.substring("/uploads/".length()))) {
                throw new IllegalArgumentException("The file of image " + id + " is missing from the archive");
            }
            owners.add(new UUID[] {ownerId, id});
            return;
        }
        throw new IllegalArgumentException("Image " + id + " needs a positive size and a sort index");
    }

    private static boolean positive(JsonNode row, String field) {
        var value = row.get(field);
        return value != null && value.isIntegralNumber() && value.intValue() > 0;
    }

    private static <E extends Enum<E>> E enumValue(JsonNode row, String field, Class<E> type) {
        String value = text(row, field);
        try {
            return Enum.valueOf(type, value == null ? "" : value);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Unknown image " + field + ": " + value);
        }
    }

    private static String text(JsonNode row, String field) {
        var value = row.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static UUID uuid(JsonNode row, String field) {
        try {
            return UUID.fromString(text(row, field));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid image reference " + field);
        }
    }
}
