package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Prewipe checks for music tracks: schema 26 or later, field limits, unique IDs and the file in the archive. */
public final class MusicTrackBackupValidator {
    public static final int MUSIC_VERSION = 26;
    private static final int BUDGET = 100_000;
    private final int version;
    private final Set<String> archivedUploads;
    private final Set<UUID> ids = new HashSet<>();

    /** @param archivedUploads the archive entry names below {@code uploads/} */
    public MusicTrackBackupValidator(int version, Set<String> archivedUploads) {
        this.version = version;
        this.archivedUploads = Set.copyOf(archivedUploads);
    }

    public void accept(String table, JsonNode row) {
        if (!"music_tracks".equals(table)) return;
        if (version < MUSIC_VERSION) throw new IllegalArgumentException("Music tracks require backup schema " + MUSIC_VERSION);
        UUID id;
        try {
            id = UUID.fromString(text(row, "id"));
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid music track id");
        }
        if (!ids.add(id)) throw new IllegalArgumentException("Music track " + id + " appears twice");
        if (ids.size() > BUDGET) throw new IllegalArgumentException("Music validation exceeds the " + BUDGET + "-row budget");
        required(row, "title", 150, id);
        required(row, "artist", 150, id);
        String credit = text(row, "credit");
        if (credit != null && credit.length() > 500) throw new IllegalArgumentException("Music track " + id + " has a credit over 500 characters");
        var duration = row.get("durationSeconds");
        if (duration == null || !duration.isIntegralNumber() || !duration.canConvertToInt() || duration.intValue() <= 0) {
            throw new IllegalArgumentException("Music track " + id + " needs a positive duration");
        }
        time(row, "createdAt");
        time(row, "updatedAt");
        String path = text(row, "filePath");
        if (path == null || path.length() > 500 || !path.startsWith("/uploads/")
                || !archivedUploads.contains("uploads/" + path.substring("/uploads/".length()))) {
            throw new IllegalArgumentException("The file of music track " + id + " is missing from the archive");
        }
    }

    public void validate() {
        // Rows carry no references, so every check runs per row.
    }

    private static void required(JsonNode row, String field, int max, UUID id) {
        String value = text(row, field);
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException("Music track " + id + " needs a " + field + " of at most " + max + " characters");
        }
    }

    private static void time(JsonNode row, String field) {
        String value = text(row, field);
        try {
            LocalDateTime.parse(value == null ? "" : value);
        } catch (DateTimeException invalid) {
            throw new IllegalArgumentException("Invalid music track timestamp " + field);
        }
    }

    private static String text(JsonNode row, String field) {
        var value = row.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
