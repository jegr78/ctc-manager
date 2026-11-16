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
import org.ctc.domain.service.CatalogImageService;
import java.time.DateTimeException;
import java.time.LocalDateTime;

/**
 * Bounded prewipe checks for car and track gallery images: schema 24 or later, existing owners, known kinds and
 * sources, a source key exactly for downloaded images, one image per GT7 slot and owner, at most one GT-Engine map per
 * track from schema 25 on, and the file in the archive.
 */
public final class CatalogImageBackupValidator {
    public static final int IMAGE_VERSION = 24;
    public static final int GT_ENGINE_VERSION = 25;
    public static final int CUTOUT_VERSION = 27;
    private static final Set<String> TABLES = Set.of("cars", "tracks", "car_images", "track_images");
    private static final int BUDGET = 100_000;
    private final int version;
    private final Set<String> archivedUploads;
    private final Set<UUID> cars = new HashSet<>();
    private final Set<UUID> tracks = new HashSet<>();
    private final Set<String> slots = new HashSet<>();
    private final List<UUID> carOwners = new ArrayList<>();
    private final List<UUID> trackOwners = new ArrayList<>();
    private final Set<UUID> imageIds = new HashSet<>();
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
        for (var owner : carOwners) if (!cars.contains(owner)) throw new IllegalArgumentException("A car image references a missing car");
        for (var owner : trackOwners) if (!tracks.contains(owner)) throw new IllegalArgumentException("A track image references a missing track");
    }

    private <E extends Enum<E>> void image(JsonNode row, UUID id, String owner, Class<E> kinds, List<UUID> owners) {
        if (version < IMAGE_VERSION) throw new IllegalArgumentException("Gallery images require backup schema " + IMAGE_VERSION);
        if (!imageIds.add(id)) throw new IllegalArgumentException("Image " + id + " appears twice");
        time(row, "createdAt");
        time(row, "updatedAt");
        UUID ownerId = uuid(row, owner);
        var kind = enumValue(row, "kind", kinds);
        var source = enumValue(row, "source", ImageSource.class);
        String key = text(row, "sourceKey");
        if (version < CUTOUT_VERSION && (source == ImageSource.AUTO || kind == CarImageKind.CUTOUT)) {
            throw new IllegalArgumentException("Car cutouts require backup schema " + CUTOUT_VERSION);
        }
        if (source == ImageSource.GTENGINE && (version < GT_ENGINE_VERSION || !"track".equals(owner))) {
            throw new IllegalArgumentException("GT-Engine maps belong to tracks and require backup schema " + GT_ENGINE_VERSION);
        }
        boolean paired = source.requiresSourceKey() ? key != null && !key.isBlank() : key == null;
        if (!paired) {
            throw new IllegalArgumentException("A downloaded image needs its source key and a manual image must not have one");
        }
        if (source == ImageSource.GT7 && !slots.add(owner + ':' + ownerId + ':' + key)) {
            throw new IllegalArgumentException("A " + owner + " has two GT7 images for slot " + key);
        }
        if (source == ImageSource.GTENGINE && (!CatalogImageService.GT_ENGINE_KEY.equals(key) || !"LAYOUT".equals(text(row, "kind")))) {
            throw new IllegalArgumentException("A GT-Engine map needs source key " + CatalogImageService.GT_ENGINE_KEY + " and kind LAYOUT");
        }
        if (source == ImageSource.AUTO && (!"car".equals(owner) || !CatalogImageService.AUTO_CUTOUT_KEY.equals(key) || kind != CarImageKind.CUTOUT)) {
            throw new IllegalArgumentException("An automatic cutout needs source key " + CatalogImageService.AUTO_CUTOUT_KEY + " and kind CUTOUT");
        }
        if (source == ImageSource.AUTO && !slots.add("auto:" + ownerId)) {
            throw new IllegalArgumentException("A car has two automatic cutouts");
        }
        if (source == ImageSource.GTENGINE && !slots.add("gtengine:" + ownerId)) {
            throw new IllegalArgumentException("A track has two GT-Engine maps");
        }
        String label = text(row, "label");
        if (label != null && label.length() > 100) throw new IllegalArgumentException("An image label exceeds 100 characters");
        if (key != null && key.length() > 32) throw new IllegalArgumentException("An image source key exceeds 32 characters");
        if (positive(row, "width") && positive(row, "height") && row.path("sortIndex").isIntegralNumber() && row.path("sortIndex").canConvertToInt()) {
            String path = text(row, "imagePath");
            if (path == null || path.length() > 500 || !path.startsWith("/uploads/") || !archivedUploads.contains("uploads/" + path.substring("/uploads/".length()))) {
                throw new IllegalArgumentException("The file of image " + id + " is missing from the archive");
            }
            owners.add(ownerId);
            return;
        }
        throw new IllegalArgumentException("Image " + id + " needs a positive size and a sort index");
    }

    private static boolean positive(JsonNode row, String field) {
        var value = row.get(field);
        return value != null && value.isIntegralNumber() && value.canConvertToInt() && value.intValue() > 0;
    }

    private static <E extends Enum<E>> E enumValue(JsonNode row, String field, Class<E> type) {
        String value = text(row, field);
        try {
            return Enum.valueOf(type, value == null ? "" : value);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Unknown image " + field + ": " + value);
        }
    }

    private static void time(JsonNode row, String field) {
        String value = text(row, field);
        try {
            LocalDateTime.parse(value == null ? "" : value);
        } catch (DateTimeException invalid) {
            throw new IllegalArgumentException("Invalid image timestamp " + field);
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
