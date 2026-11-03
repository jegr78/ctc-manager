package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.ctc.domain.model.*;

public final class PickBackupValidator {
    private static final int MAX_VALUES = 100_000;
    private static final Map<String, Set<String>> FIELDS = Map.ofEntries(
            Map.entry("seasons", Set.of("id", "picksCollectionEnabled", "carPoolMode", "carPoolState", "cars", "tracks")),
            Map.entry("teams", Set.of("id")), Map.entry("cars", Set.of("id")), Map.entry("tracks", Set.of("id")),
            Map.entry("season_phases", Set.of("id", "season")), Map.entry("matchdays", Set.of("id", "phase")),
            Map.entry("matches", Set.of("id", "homeTeam", "awayTeam", "bye", "discordChannelId")),
            Map.entry("team_pick_channel_bindings", Set.of("id", "season", "team", "guildId", "channelId", "createdAt", "updatedAt")),
            Map.entry("playoff_matchups", Set.of("id", "team1", "team2", "bye")),
            Map.entry("races", Set.of("id", "matchday", "match", "playoffMatchup", "homeTeamOverride", "awayTeamOverride", "car", "track", "carPoolEntry", "picksManaged")),
            Map.entry("season_car_pool_entries", Set.of("id", "season")), Map.entry("season_car_pool_members", Set.of("id", "entry", "car")),
            Map.entry("race_settings", Set.of("id", "race", "numberOfLaps", "tyreWearMultiplier", "fuelConsumptionMultiplier", "refuelingSpeed", "initialFuel", "numberOfRequiredPitStops", "timeProgressionMode", "timeProgressionMultiplier", "weather", "timeOfDay", "availableTyres", "mandatoryTyres")),
            Map.entry("matchday_picks", Set.of("id", "race", "season", "team", "originalMatchday", "originalAwayTeam", "version", "state", "latestRevisionNumber", "approvedRevisionNumber", "manualOpen", "manualClosed", "createdAt", "updatedAt")),
            Map.entry("matchday_pick_revisions", Set.of("id", "pick", "revisionNumber", "payload", "confirmedAt", "submitterId", "requestId", "receiptVersion", "createdAt", "updatedAt")),
            Map.entry("pick_publication_attempts", Set.of("id", "batchId", "race", "seasonId", "originalMatchdayId", "originalHomeTeamId", "originalAwayTeamId",
                    "contentVersion", "sourceVersion", "origin", "output", "content", "contentHash", "fieldMask", "actorId", "channelId", "webhookId", "targetMessageId",
                    "templateHash", "requestManifest", "requestHash", "runtimeGeneration", "capturedAt", "state", "activeRaceId", "messageId", "completedAt",
                    "failureCode", "reconciledBy", "reconciliationReason", "resolvedAt", "createdAt", "updatedAt")),
            Map.entry("pick_publication_snapshots", Set.of("id", "attempt", "confirmedAt", "createdAt", "updatedAt")),
            Map.entry("matchday_pick_decisions", Set.of("id", "pick", "kind", "aggregateVersion", "actorKind", "actorId", "reason", "decidedAt", "correctionPayload", "createdAt", "updatedAt")));
    private final int version;
    private final Map<String, Map<String, JsonNode>> rows = new HashMap<>();
    private int retainedValues;
    private boolean confidentialContent;

    public PickBackupValidator(int version) { this.version = version; }

    public void accept(String table, JsonNode row) {
        if (!FIELDS.containsKey(table)) return;
        try {
            boolean publication = table.startsWith("pick_publication_");
            if (publication && version < 7) fail("Publication evidence requires schema version 7");
            if (version < 6 && table.equals("team_pick_channel_bindings")) fail("Channel bindings require schema version 6");
            String flag = table.equals("seasons") ? "picksCollectionEnabled" : table.equals("races") ? "picksManaged" : null;
            if (flag != null) {
                if (version >= 5 || row.has(flag)) bool(row, flag);
                if (version < 5 && row.path(flag).asBoolean()) fail("Confidential flags require schema version 5");
                confidentialContent |= row.path(flag).asBoolean();
            }
            if (version < 5) {
                if (table.startsWith("matchday_pick")) fail("History requires schema version 5");
                return;
            }
            if (!row.isObject()) fail("A retained row must be an object");
            if (publication || table.startsWith("matchday_pick") || table.equals("team_pick_channel_bindings")) {
                row.fieldNames().forEachRemaining(field -> { if (!FIELDS.get(table).contains(field)) fail("Unknown history field " + field); });
                for (String field : List.of("createdAt", "updatedAt")) {
                    if (row.hasNonNull(field)) validYear(LocalDateTime.parse(text(row, field, 40)));
                }
            }
            if (publication) {
                for (String field : FIELDS.get(table)) if (!row.has(field)) fail("Missing publication field " + field);
                retainedValues += row.size();
            }
            var projected = JsonNodeFactory.instance.objectNode();
            for (String field : FIELDS.get(table)) {
                var value = row.get(field);
                if (value == null) continue;
                if (publication && field.equals("content")) {
                    PickPublicationContentReader.read(value);
                    retainedValues += value.size();
                } else if (publication && field.equals("requestManifest")) {
                    text(row, field, 16000);
                    retainedValues += value.textValue().length() / 1000;
                } else if (field.equals("payload") || field.equals("correctionPayload")) {
                    if (!value.isNull()) PickPayloadReader.read(value);
                } else if (field.equals("cars") || field.equals("tracks")) {
                    if (!value.isArray()) fail("Season pools must be arrays");
                    if (value.size() > MAX_VALUES - retainedValues) fail("Validation exceeds the 100000-value budget");
                    for (var item : value) parseUuid(item);
                    retainedValues += value.size();
                } else if (!value.isNull() && !(value.isBoolean() || value.isIntegralNumber()
                        || value.isTextual() && value.textValue().length() <= 1000)) fail("Invalid retained scalar " + field);
                projected.set(field, value.deepCopy());
            }
            if (++retainedValues > MAX_VALUES) fail("Validation exceeds the 100000-value budget");
            var indexed = rows.computeIfAbsent(table, ignored -> new LinkedHashMap<>());
            if (indexed.putIfAbsent(uuid(row, "id"), projected) != null) fail("Duplicate row identity in " + table);
        } catch (IllegalArgumentException | DateTimeException exception) {
            throw wrapped(exception);
        }
    }

    public static boolean supports(String table) { return FIELDS.containsKey(table); }

    public boolean hasConfidentialContent() { return confidentialContent; }

    public void validate() {
        if (version < 5) return;
        try {
            validateBindings();
            var events = new HashMap<String, SortedMap<Long, Event>>();
            var requestIds = new HashSet<String>();
            for (var revision : index("matchday_pick_revisions").values()) {
                String owner = reference(revision, "pick", "matchday_picks");
                int number = positiveInt(revision, "revisionNumber");
                long receiptVersion = nonnegativeLong(revision, "receiptVersion");
                if (receiptVersion < number || !requestIds.add(uuid(revision, "requestId"))) fail("Invalid receipt version or duplicate request");
                if (!text(revision, "submitterId", 32).matches("[0-9]{1,32}")) fail("Invalid revision submitter");
                instant(revision, "confirmedAt");
                var payload = PickPayloadReader.read(revision.get("payload"));
                validatePayload(payload, uuid(index("matchday_picks").get(owner), "season"));
                event(events, owner, receiptVersion, new Event(null, number, payload));
            }
            for (var decision : index("matchday_pick_decisions").values()) {
                String owner = reference(decision, "pick", "matchday_picks");
                var kind = PickDecisionKind.valueOf(text(decision, "kind", 16));
                if (!text(decision, "actorKind", 16).equals("MANAGEMENT")) fail("Invalid decision actor kind");
                text(decision, "actorId", 128);
                text(decision, "reason", 1000);
                instant(decision, "decidedAt");
                if (!decision.has("correctionPayload")) fail("Missing correction payload field");
                var payload = decision.hasNonNull("correctionPayload") ? PickPayloadReader.read(decision.get("correctionPayload")) : null;
                if ((kind == PickDecisionKind.CORRECTION) != (payload != null)) fail("Invalid correction payload");
                if (payload != null) validatePayload(payload, uuid(index("matchday_picks").get(owner), "season"));
                event(events, owner, nonnegativeLong(decision, "aggregateVersion"), new Event(kind, null, payload));
            }
            var raceIds = new HashSet<String>();
            var pending = new ArrayList<Pending>();
            for (var item : index("matchday_picks").entrySet()) {
                var row = item.getValue();
                String raceId = reference(row, "race", "races");
                String seasonId = reference(row, "season", "seasons");
                String teamId = reference(row, "team", "teams");
                String originalDay = reference(row, "originalMatchday", "matchdays");
                String originalAway = reference(row, "originalAwayTeam", "teams");
                var race = index("races").get(raceId);
                if (!raceIds.add(raceId) || !bool(race, "picksManaged") || !seasonId.equals(seasonOf(race)) || !teamId.equals(home(race))
                        || teamId.equals(originalAway) || !originalDay.equals(uuid(race, "matchday")) || !originalAway.equals(away(race))) {
                    fail("Aggregate identity disagrees with its managed home race " + raceId);
                }
                var replay = new MatchdayPick(new Race(), new Season(), new Team());
                PickPayload latestPayload = null;
                for (var entry : events.getOrDefault(item.getKey(), Collections.emptySortedMap()).entrySet()) {
                    replay.requireVersion(entry.getKey() - 1);
                    var event = entry.getValue();
                    if (event.kind() == null) {
                        if (replay.submit() != event.revisionNumber()) fail("Revision numbers must be sequential");
                        latestPayload = event.payload();
                    } else {
                        switch (event.kind()) {
                            case OPEN -> replay.openMissing();
                            case RETURN -> replay.returnForCorrection();
                            case CLOSE -> replay.close();
                            case APPROVE -> {
                                if (replay.getLatestRevisionNumber() == null) fail("Cannot approve a missing revision");
                                replay.approve(replay.getLatestRevisionNumber());
                            }
                            case CORRECTION -> { replay.correct(); latestPayload = event.payload(); }
                        }
                    }
                }
                if (replay.getVersion() != nonnegativeLong(row, "version")
                        || replay.getState() != PickState.valueOf(text(row, "state", 16))
                        || !Objects.equals(replay.getLatestRevisionNumber(), optionalInt(row, "latestRevisionNumber"))
                        || !Objects.equals(replay.getApprovedRevisionNumber(), optionalInt(row, "approvedRevisionNumber"))
                        || replay.isManualOpen() != bool(row, "manualOpen") || replay.isManualClosed() != bool(row, "manualClosed")) {
                    fail("Aggregate state does not match immutable history for race " + raceId);
                }
                if (latestPayload != null) {
                    if (replay.getState() == PickState.APPROVED) validateApproved(raceId, race, latestPayload);
                    else pending.add(new Pending(raceId, seasonId, teamId, latestPayload));
                }
            }
            validateReservations(pending);
            validatePublications();
        } catch (IllegalArgumentException | DateTimeException | org.ctc.domain.exception.BusinessRuleException exception) {
            throw wrapped(exception);
        }
    }

    private void validatePublications() {
        var active = new HashSet<String>();
        var versions = new HashMap<String, String>();
        var confirmed = new HashSet<String>();
        for (var item : index("pick_publication_attempts").entrySet()) {
            var row = item.getValue();
            String raceId = reference(row, "race", "races");
            String seasonId = reference(row, "seasonId", "seasons");
            String dayId = reference(row, "originalMatchdayId", "matchdays");
            String homeId = reference(row, "originalHomeTeamId", "teams");
            String awayId = reference(row, "originalAwayTeamId", "teams");
            String phaseId = reference(index("matchdays").get(dayId), "phase", "season_phases");
            if (homeId.equals(awayId)) fail("Invalid publication target context");
            uuid(row, "batchId"); text(row, "actorId", 128); text(row, "channelId", 64); text(row, "webhookId", 64);
            for (String field : List.of("targetMessageId", "messageId")) if (row.hasNonNull(field)) text(row, field, 64);
            if (!text(row, "templateHash", 64).matches("[0-9a-f]{64}")) fail("Invalid publication template proof");
            String generation = text(row, "runtimeGeneration", 64);
            if (!generation.matches("[0-9a-f-]{36}:[0-9]+")) fail("Invalid publication runtime context");
            UUID.fromString(generation.substring(0, 36));
            long version = nonnegativeLong(row, "contentVersion"), sourceVersion = nonnegativeLong(row, "sourceVersion");
            if (version == 0) fail("Invalid publication content version");
            var origin = PickPublicationOrigin.valueOf(text(row, "origin", 16));
            if (origin != PickPublicationOrigin.LEGACY && !seasonId.equals(uuid(index("season_phases").get(phaseId), "season"))) {
                fail("Invalid publication target context");
            }
            if ((origin == PickPublicationOrigin.LEGACY) != (sourceVersion == 0)) fail("Invalid publication source version");
            var output = PickPublicationOutput.valueOf(text(row, "output", 24));
            long mask = nonnegativeLong(row, "fieldMask");
            if (mask > Integer.MAX_VALUE) fail("Invalid publication field mask");
            var fields = PickDisclosureField.fields((int) mask);
            if (output == PickPublicationOutput.LOBBY_SETTINGS && fields.contains(PickDisclosureField.CAR)) fail("Lobby cannot disclose a car");
            var content = PickPublicationContentReader.read(row.get("content"));
            String hash = text(row, "contentHash", 64);
            if (!hash.equals(content.fingerprint()) || !text(row, "requestHash", 64).equals(PickPublicationAttempt.manifestHash(text(row, "requestManifest", 16000)))) {
                fail("Publication hashes disagree with captured evidence");
            }
            String signature = hash + ":" + origin + ":" + sourceVersion + ":" + seasonId + ":" + dayId + ":" + homeId + ":" + awayId;
            String previous = versions.putIfAbsent(raceId + ":" + version, signature);
            if (previous != null && !previous.equals(signature)) fail("Publication version mixes different content or provenance");
            if (origin != PickPublicationOrigin.LEGACY) validatePublicationSource(raceId, sourceVersion, origin, content);
            instant(row, "capturedAt");
            var state = PickPublicationState.valueOf(text(row, "state", 16));
            boolean open = state == PickPublicationState.PREPARED || state == PickPublicationState.UNCERTAIN;
            if (open ? !raceId.equals(nullableUuid(row, "activeRaceId")) || !active.add(raceId) : row.hasNonNull("activeRaceId")) fail("Invalid active publication claim");
            if ((state == PickPublicationState.PREPARED) == row.hasNonNull("completedAt")) fail("Invalid publication completion state");
            if (row.hasNonNull("completedAt")) {
                instant(row, "completedAt");
                if (Instant.parse(row.get("completedAt").asText()).isBefore(Instant.parse(row.get("capturedAt").asText()))) fail("Publication completion precedes capture");
            }
            boolean reconciled = row.hasNonNull("resolvedAt");
            if (reconciled != row.hasNonNull("reconciledBy") || reconciled != row.hasNonNull("reconciliationReason")) fail("Incomplete publication reconciliation");
            if (reconciled) {
                if (state != PickPublicationState.SUCCEEDED && state != PickPublicationState.ABANDONED) fail("Invalid reconciled publication state");
                text(row, "reconciledBy", 128); text(row, "reconciliationReason", 1000); instant(row, "resolvedAt");
                if (!row.hasNonNull("completedAt") || Instant.parse(row.get("resolvedAt").asText()).isBefore(Instant.parse(row.get("completedAt").asText()))) fail("Publication resolution precedes completion");
            }
            if (state == PickPublicationState.ABANDONED && !reconciled) fail("Abandonment requires a recorded decision");
            if (state == PickPublicationState.FAILED || state == PickPublicationState.UNCERTAIN || row.hasNonNull("failureCode")) {
                if (!text(row, "failureCode", 64).matches("[A-Z0-9_-]{1,64}")) fail("Invalid publication outcome category");
            }
            if (state == PickPublicationState.SUCCEEDED) {
                text(row, "messageId", 64);
                if (!fields.isEmpty()) confirmed.add(item.getKey());
            }
        }
        var snapshotAttempts = new HashSet<String>();
        for (var row : index("pick_publication_snapshots").values()) {
            String attemptId = reference(row, "attempt", "pick_publication_attempts");
            var attempt = index("pick_publication_attempts").get(attemptId);
            if (!confirmed.contains(attemptId) || !snapshotAttempts.add(attemptId)) fail("Snapshot requires unique successful disclosed content");
            instant(row, "confirmedAt");
            String confirmedAt = attempt.hasNonNull("resolvedAt") ? attempt.get("resolvedAt").asText() : attempt.get("completedAt").asText();
            if (!Instant.parse(confirmedAt).equals(Instant.parse(row.get("confirmedAt").asText()))) fail("Snapshot confirmation differs from its successful outcome");
        }
        if (!snapshotAttempts.equals(confirmed)) fail("Confirmed publication is missing its disclosed snapshot");
    }

    private void validatePublicationSource(String raceId, long version, PickPublicationOrigin origin, PickPublicationContent content) {
        var pick = index("matchday_picks").values().stream().filter(row -> raceId.equals(uuid(row, "race"))).findFirst()
                .orElseThrow(() -> new ValidationException("Invalid pick publication source aggregate"));
        String pickId = uuid(pick, "id");
        var decision = index("matchday_pick_decisions").values().stream()
                .filter(row -> pickId.equals(uuid(row, "pick")) && nonnegativeLong(row, "aggregateVersion") == version).findFirst()
                .orElseThrow(() -> new ValidationException("Invalid pick publication source decision"));
        PickPayload payload;
        if (origin == PickPublicationOrigin.CORRECTION && "CORRECTION".equals(text(decision, "kind", 16))) {
            payload = PickPayloadReader.read(decision.get("correctionPayload"));
        } else if (origin == PickPublicationOrigin.APPROVAL && "APPROVE".equals(text(decision, "kind", 16))) {
            var revision = index("matchday_pick_revisions").values().stream().filter(row -> pickId.equals(uuid(row, "pick"))
                    && nonnegativeLong(row, "receiptVersion") < version).max(Comparator.comparingLong(row -> nonnegativeLong(row, "receiptVersion")))
                    .orElseThrow(() -> new ValidationException("Invalid pick publication source revision"));
            payload = PickPayloadReader.read(revision.get("payload"));
        } else throw new ValidationException("Invalid pick publication source origin");
        var captured = new PickSetupSnapshot(content.trackId(), content.carId(), content.carPoolEntryId(), content.settings());
        if (!captured.matches(payload)) fail("Published content differs from its immutable source");
    }

    private void validatePayload(PickPayload payload, String seasonId) {
        var season = index("seasons").get(seasonId);
        if (season == null || !index("cars").containsKey(payload.carId().toString()) || !index("tracks").containsKey(payload.trackId().toString())) fail("Missing payload reference");
        if (!contains(season.get("tracks"), payload.trackId().toString())) fail("Track is outside the pick season");
        if ("FIXED_CARS".equals(text(season, "carPoolMode", 24))) {
            if (payload.carPoolEntryId() != null || !contains(season.get("cars"), payload.carId().toString())) fail("Invalid fixed-pool payload reference");
        } else {
            String entryId = payload.carPoolEntryId() == null ? null : payload.carPoolEntryId().toString();
            var entry = entryId == null ? null : index("season_car_pool_entries").get(entryId);
            if (entry == null || !seasonId.equals(uuid(entry, "season")) || !"FROZEN".equals(text(season, "carPoolState", 16))) fail("Invalid historical pool entry");
            if (index("season_car_pool_members").values().stream().noneMatch(member -> entryId.equals(uuid(member, "entry")) && payload.carId().toString().equals(uuid(member, "car")))) fail("Payload car is not a frozen entry member");
        }
    }

    private void validateApproved(String raceId, JsonNode race, PickPayload payload) {
        if (!payload.carId().toString().equals(nullableUuid(race, "car")) || !payload.trackId().toString().equals(nullableUuid(race, "track"))
                || !Objects.equals(payload.carPoolEntryId() == null ? null : payload.carPoolEntryId().toString(), nullableUuid(race, "carPoolEntry"))) fail("Approved race selection differs from its history");
        var settings = index("race_settings").values().stream().filter(row -> raceId.equals(uuid(row, "race"))).toList();
        if (settings.size() != 1) fail("Approved race needs exactly one settings row");
        var value = RaceSettingsValues.from(payload);
        var row = settings.getFirst();
        equal(row, "numberOfLaps", value.laps()); equal(row, "tyreWearMultiplier", value.tyreWear());
        equal(row, "fuelConsumptionMultiplier", value.fuelConsumption()); equal(row, "refuelingSpeed", value.refuelingSpeed());
        equal(row, "initialFuel", value.initialFuel()); equal(row, "numberOfRequiredPitStops", value.requiredPitStops());
        equal(row, "timeProgressionMode", value.timeProgressionMode().name()); equal(row, "timeProgressionMultiplier", value.timeProgressionMultiplier());
        equal(row, "weather", value.weather()); equal(row, "timeOfDay", value.timeOfDay());
        equal(row, "availableTyres", value.availableTyres()); equal(row, "mandatoryTyres", value.mandatoryTyres());
    }

    private void validateReservations(List<Pending> pending) {
        var cars = new HashSet<String>();
        var tracks = new HashSet<String>();
        for (var reservation : pending) {
            String scope = reservation.season() + "/" + reservation.team() + "/";
            var payload = reservation.payload();
            String selection = payload.carPoolEntryId() == null ? payload.carId().toString() : payload.carPoolEntryId().toString();
            if (!cars.add(scope + selection) || !tracks.add(scope + payload.trackId())) fail("Conflicting pending reservations for race " + reservation.race());
            for (var item : index("races").entrySet()) {
                if (reservation.race().equals(item.getKey())) continue;
                var race = item.getValue();
                if (isBye(race)) continue;
                if (!reservation.season().equals(seasonOf(race)) || !reservation.team().equals(home(race))) continue;
                String used = nullableUuid(race, payload.carPoolEntryId() == null ? "car" : "carPoolEntry");
                if (selection.equals(used) || payload.trackId().toString().equals(nullableUuid(race, "track"))) fail("Pending reservation conflicts with another race");
            }
        }
    }

    private boolean isBye(JsonNode race) {
        if (race.hasNonNull("match")) {
            var match = index("matches").get(reference(race, "match", "matches"));
            if (match.path("bye").asBoolean()) return true;
        }
        if (race.hasNonNull("playoffMatchup")) {
            var matchup = index("playoff_matchups").get(reference(race, "playoffMatchup", "playoff_matchups"));
            if (matchup.path("bye").asBoolean()) return true;
        }
        return false;
    }

    private String seasonOf(JsonNode race) {
        var day = index("matchdays").get(reference(race, "matchday", "matchdays"));
        var phase = index("season_phases").get(reference(day, "phase", "season_phases"));
        return reference(phase, "season", "seasons");
    }

    private String home(JsonNode race) {
        if (race.hasNonNull("homeTeamOverride")) return uuid(race, "homeTeamOverride");
        if (race.hasNonNull("match")) return uuid(index("matches").get(reference(race, "match", "matches")), "homeTeam");
        if (race.hasNonNull("playoffMatchup")) return uuid(index("playoff_matchups").get(reference(race, "playoffMatchup", "playoff_matchups")), "team1");
        return null;
    }

    private String away(JsonNode race) {
        if (race.hasNonNull("awayTeamOverride")) return uuid(race, "awayTeamOverride");
        if (race.hasNonNull("match")) return uuid(index("matches").get(reference(race, "match", "matches")), "awayTeam");
        if (race.hasNonNull("playoffMatchup")) return uuid(index("playoff_matchups").get(reference(race, "playoffMatchup", "playoff_matchups")), "team2");
        return null;
    }

    private static void event(Map<String, SortedMap<Long, Event>> events, String owner, long version, Event event) {
        if (version < 1 || events.computeIfAbsent(owner, ignored -> new TreeMap<>()).putIfAbsent(version, event) != null) fail("Duplicate or invalid history version");
    }
    private void validateBindings() {
        var teamScopes = new HashSet<List<String>>();
        var channelScopes = new HashSet<List<String>>();
        var sharedChannels = new HashSet<String>();
        for (var match : index("matches").values()) {
            if (match.hasNonNull("discordChannelId")) sharedChannels.add(match.get("discordChannelId").asText());
        }
        for (var binding : index("team_pick_channel_bindings").values()) {
            var season = reference(binding, "season", "seasons");
            var team = reference(binding, "team", "teams");
            var guild = text(binding, "guildId", 32);
            var channel = text(binding, "channelId", 32);
            if (!guild.matches("[0-9]{1,32}") || !channel.matches("[0-9]{1,32}") || sharedChannels.contains(channel)
                    || !teamScopes.add(List.of(guild, season, team)) || !channelScopes.add(List.of(guild, season, channel))) {
                fail("Invalid or conflicting private team channel binding");
            }
        }
    }

    private Map<String, JsonNode> index(String table) { return rows.getOrDefault(table, Map.of()); }
    private String reference(JsonNode row, String field, String table) {
        String id = uuid(row, field);
        if (!index(table).containsKey(id)) fail("Missing " + field + " reference");
        return id;
    }
    private static boolean contains(JsonNode array, String id) {
        if (array == null || !array.isArray()) fail("Missing season pool");
        for (var value : array) if (parseUuid(value).equals(id)) return true;
        return false;
    }
    private static String text(JsonNode row, String field, int max) {
        var value = row == null ? null : row.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > max) fail("Invalid " + field);
        return value.textValue();
    }
    private static String uuid(JsonNode row, String field) { return parseUuid(row == null ? null : row.get(field)); }
    private static String nullableUuid(JsonNode row, String field) { return row.hasNonNull(field) ? uuid(row, field) : null; }
    private static String parseUuid(JsonNode value) {
        if (value == null || !value.isTextual() || value.textValue().length() != 36) throw new ValidationException("Invalid pick UUID");
        return UUID.fromString(value.textValue()).toString();
    }
    private static boolean bool(JsonNode row, String field) {
        if (row == null || !row.has(field) || !row.get(field).isBoolean()) fail("Invalid " + field);
        return row.get(field).booleanValue();
    }
    private static long nonnegativeLong(JsonNode row, String field) {
        var value = row.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) fail("Invalid " + field);
        return value.longValue();
    }
    private static Integer optionalInt(JsonNode row, String field) {
        if (!row.has(field)) fail("Missing " + field);
        if (row.get(field).isNull()) return null;
        return positiveInt(row, field);
    }
    private static int positiveInt(JsonNode row, String field) {
        var value = row.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1) fail("Invalid " + field);
        return value.intValue();
    }
    private static void instant(JsonNode row, String field) {
        var value = Instant.parse(text(row, field, 40));
        if (!value.equals(value.truncatedTo(ChronoUnit.MICROS))) fail("History timestamps require microsecond precision");
        validYear(LocalDateTime.ofInstant(value, ZoneOffset.UTC));
    }
    private static void validYear(LocalDateTime value) { if (value.getYear() < 1000 || value.getYear() > 9999) fail("Timestamp is outside the database range"); }
    private static void equal(JsonNode row, String field, Object expected) {
        var value = row.get(field);
        boolean valid = value != null && (expected == null ? value.isNull() : expected instanceof Integer number
                ? value.isIntegralNumber() && value.canConvertToInt() && value.intValue() == number
                : value.isTextual() && value.textValue().equals(expected));
        if (!valid) fail("Approved settings differ from immutable history");
    }
    private static ValidationException wrapped(RuntimeException exception) {
        return exception instanceof ValidationException validation ? validation : new ValidationException("Invalid pick data: " + exception.getMessage());
    }
    private static void fail(String message) { throw new ValidationException("Invalid pick data: " + message); }
    private record Event(PickDecisionKind kind, Integer revisionNumber, PickPayload payload) { }
    private record Pending(String race, String season, String team, PickPayload payload) { }
    public static final class ValidationException extends IllegalArgumentException {
        public ValidationException(String message) { super(message); }
    }
}
