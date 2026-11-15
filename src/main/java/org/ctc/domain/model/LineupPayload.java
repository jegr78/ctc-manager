package org.ctc.domain.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * An ordered lineup as submitted for one team and leg. Known drivers carry their ID plus a readable snapshot;
 * proposed new drivers carry only the PSN ID and an optional display name.
 */
public record LineupPayload(int requiredSize, List<Entry> entries) {
    public static final int MAX_NAME = 64;
    private static final Pattern PROPOSED_PSN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{2,15}");
    private static final ObjectMapper JSON = new ObjectMapper();

    public LineupPayload {
        if (requiredSize < 1 || requiredSize > Season.LINEUP_SIZE_MAX) throw new IllegalArgumentException("Invalid lineup size");
        entries = List.copyOf(Objects.requireNonNull(entries));
        if (entries.isEmpty()) throw new IllegalArgumentException("A lineup needs at least one driver");
        if (entries.size() > requiredSize) throw new IllegalArgumentException("A lineup has at most " + requiredSize + " drivers");
        var drivers = new HashSet<UUID>();
        var psns = new HashSet<String>();
        for (var entry : entries) {
            if (entry.driverId() != null && !drivers.add(entry.driverId())) throw new IllegalArgumentException("A driver appears twice");
            if (!psns.add(entry.psnId().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("A PSN ID appears twice");
        }
    }

    public boolean complete() { return entries.size() == requiredSize; }

    public List<UUID> driverIds() {
        return entries.stream().map(Entry::driverId).filter(Objects::nonNull).toList();
    }

    public List<Entry> proposed() { return entries.stream().filter(Entry::proposed).toList(); }

    public String toJson() {
        var root = JSON.createObjectNode();
        root.put("requiredSize", requiredSize);
        ArrayNode rows = root.putArray("entries");
        for (var entry : entries) {
            var row = rows.addObject();
            if (entry.driverId() == null) row.putNull("driverId"); else row.put("driverId", entry.driverId().toString());
            row.put("psnId", entry.psnId());
            if (entry.displayName() == null) row.putNull("displayName"); else row.put("displayName", entry.displayName());
        }
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("Lineup payload cannot be written", impossible);
        }
    }

    public static LineupPayload fromJson(String text) {
        JsonNode root;
        try {
            root = JSON.readTree(text);
        } catch (JsonProcessingException | RuntimeException invalid) {
            throw new IllegalArgumentException("Lineup payload is not valid JSON");
        }
        if (root == null || !root.isObject() || root.size() != 2 || !root.path("requiredSize").isIntegralNumber()
                || !root.path("entries").isArray()) throw new IllegalArgumentException("Lineup payload has an invalid shape");
        var entries = new ArrayList<Entry>();
        for (var row : root.get("entries")) {
            if (!row.isObject() || row.size() != 3 || !row.has("driverId") || !row.path("psnId").isTextual() || !row.has("displayName")) {
                throw new IllegalArgumentException("Lineup entry has an invalid shape");
            }
            var id = row.get("driverId");
            var name = row.get("displayName");
            if (!id.isNull() && !id.isTextual() || !name.isNull() && !name.isTextual()) throw new IllegalArgumentException("Lineup entry has an invalid shape");
            entries.add(new Entry(id.isNull() ? null : UUID.fromString(id.textValue()), row.get("psnId").textValue(),
                    name.isNull() ? null : name.textValue()));
        }
        return new LineupPayload(root.get("requiredSize").intValue(), entries);
    }

    public record Entry(UUID driverId, String psnId, String displayName) {
        public Entry {
            if (psnId == null || psnId.isBlank() || psnId.length() > 255 || !psnId.equals(psnId.strip())) {
                throw new IllegalArgumentException("A PSN ID is required");
            }
            if (displayName != null) {
                displayName = displayName.strip();
                if (displayName.isEmpty()) displayName = null;
                else if (displayName.length() > MAX_NAME) throw new IllegalArgumentException("Display names have at most " + MAX_NAME + " characters");
            }
            if (driverId == null && !PROPOSED_PSN.matcher(psnId).matches()) {
                throw new IllegalArgumentException("A new driver's PSN ID has 3 to 16 letters, digits, hyphens or underscores and starts with a letter");
            }
        }

        public static Entry known(Driver driver) { return new Entry(driver.getId(), driver.getPsnId(), driver.getNickname()); }
        public static Entry proposal(String psnId, String displayName) { return new Entry(null, psnId == null ? null : psnId.strip(), displayName); }
        public boolean proposed() { return driverId == null; }
        public String label() { return displayName == null || displayName.equalsIgnoreCase(psnId) ? psnId : displayName + " (" + psnId + ")"; }
    }
}
