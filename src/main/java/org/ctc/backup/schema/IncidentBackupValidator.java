package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import org.ctc.domain.model.IncidentPayload;
import org.ctc.domain.service.IncidentThreadProjection;

public final class IncidentBackupValidator {
    private static final int MAX_VALUES = 100000;
    private static final Set<String> INCIDENT_TABLES = Set.of("incident_match_contexts", "incident_report_windows", "incident_reports", "incident_report_revisions", "incident_report_events", "incident_deliveries", "incident_thread_publications", "incident_rulings", "incident_ruling_revisions", "incident_ruling_publications");
    private static final Map<String, Set<String>> FIELDS = Map.ofEntries(
            Map.entry("incident_ruling_publications", Set.of("id", "revision", "threadPublication", "guildId", "channelId", "content", "previousContent", "previousRevisionNumber", "previousMessageId", "nonce", "requestedBy", "requestedAt", "state", "version", "attempts", "claimToken", "claimGeneration", "leaseUntil", "nextAttemptAt", "completedAt", "messageId", "failureCode", "reconciledBy", "reconciliationReason", "createdAt", "updatedAt")),
            Map.entry("incident_rulings", Set.of("id", "report", "version", "latestRevisionNumber", "requestedRevisionNumber", "publishedRevisionNumber", "publishedMessageId", "createdAt", "updatedAt")),
            Map.entry("incident_ruling_revisions", Set.of("id", "ruling", "revisionNumber", "text", "reason", "actorId", "acceptedAt", "createdAt", "updatedAt")),
            Map.entry("seasons", Set.of("id")), Map.entry("teams", Set.of("id")),
            Map.entry("season_phases", Set.of("id", "season")), Map.entry("matchdays", Set.of("id", "phase")),
            Map.entry("playoffs", Set.of("id", "phase")), Map.entry("playoff_rounds", Set.of("id", "playoff")),
            Map.entry("matches", Set.of("id", "matchday", "homeTeam", "awayTeam", "bye", "discordChannelId")),
            Map.entry("playoff_matchups", Set.of("id", "round", "team1", "team2", "bye")),
            Map.entry("races", Set.of("id", "matchday", "match", "playoffMatchup", "homeTeamOverride", "awayTeamOverride")),
            Map.entry("incident_match_contexts", Set.of("id", "match", "playoffMatchup", "season", "homeTeam", "awayTeam", "guildId", "channelId", "version", "boundBy", "boundAt", "createdAt", "updatedAt")),
            Map.entry("incident_report_windows", Set.of("id", "context", "version", "state", "plannedStartLocal", "plannedStart", "deadline", "openedAt", "openedBy", "closedAt", "createdAt", "updatedAt")),
            Map.entry("incident_reports", Set.of("id", "window", "race", "originalMatchday", "originalHomeTeam", "originalAwayTeam", "caseReference", "authorId", "party", "state", "version", "latestRevisionNumber", "frozenRevisionNumber", "createdAt", "updatedAt")),
            Map.entry("incident_report_revisions", Set.of("id", "report", "revisionNumber", "reportVersion", "payload", "actorId", "acceptedAt", "requestId", "createdAt", "updatedAt")),
            Map.entry("incident_deliveries", Set.of("id", "report", "revision", "operation", "operationKey", "guildId", "channelId", "nonce", "caseReference", "partyLabel", "pairingLabel", "raceLabel", "capturedAt", "state", "version", "attempts", "claimToken", "claimGeneration", "leaseUntil", "nextAttemptAt", "completedAt", "messageId", "failureCode", "reconciledBy", "reconciliationReason", "createdAt", "updatedAt")),
            Map.entry("discord_global_config",Set.of("stewardsChannelId","incidentStaffRoleIds","stewardsForumTagIds")),
            Map.entry("incident_thread_publications",Set.of("id","delivery","stewardsChannelId","staffRoleIds","forumTagIds","messageChunks","privateStarterId","privateThreadId","privateMessageIds","publicStarterId","publicThreadId","publicMessageIds","referenceMessageId","createdAt","updatedAt")),
            Map.entry("incident_report_events", Set.of("id", "window", "report", "eventKey", "kind", "windowVersion", "reportVersion", "revisionNumber", "actorKind", "actorId", "reason", "acceptedAt", "previousDeadline", "deadline", "requestId", "createdAt", "updatedAt")));
    private final int version;
    private final Map<String, Map<String, JsonNode>> rows = new HashMap<>();
    private int retained;
    private String subject = "Permanent incident history";

    public IncidentBackupValidator(int version) { this.version = version; }
    public static boolean supports(String table) { return FIELDS.containsKey(table); }
    public String subject() { return subject; }
    private void locate(String table, JsonNode row) {
        subject = table;
        if (row != null && row.path("id").isTextual()) {
            try { subject = table + " " + UUID.fromString(row.path("id").textValue()); }
            catch (IllegalArgumentException invalid) { subject = table + " with an invalid identity"; }
        }
    }

    public void accept(String table, JsonNode row) {
        if (!supports(table)) return;
        locate(table, row);
        if(table.equals("discord_global_config")){validateConfig(row);return;}
        if(table.equals("incident_thread_publications") && version<10)fail("Incident thread publication requires schema version 10");
        if (Set.of("incident_rulings", "incident_ruling_revisions", "incident_ruling_publications").contains(table) && version < 11) fail("Incident rulings require schema version 11");
        boolean incident = INCIDENT_TABLES.contains(table);
        if (table.equals("incident_deliveries") && version < 9) fail("Incident delivery requires schema version 9");
        if (version < 8) {
            if (incident) fail("Incident history requires schema version 8");
            return;
        }
        if (row == null || !row.isObject()) fail("An incident reference row must be an object");
        if (incident) {
            row.fieldNames().forEachRemaining(field -> { if (!FIELDS.get(table).contains(field)) fail("Unexpected incident history field"); });
            for (String field : FIELDS.get(table)) if (!row.has(field)
                    && !(table.equals("incident_deliveries") && version < 11 && Set.of("pairingLabel", "raceLabel").contains(field))) fail("Missing incident history field " + field);
            for (String audit : List.of("createdAt", "updatedAt")) if (row.hasNonNull(audit)) local(row, audit);
        }
        var projected = JsonNodeFactory.instance.objectNode();
        for (String field : FIELDS.get(table)) {
            var value = row.get(field);
            if (value == null) continue;
            if (field.equals("payload")) { validatePayload(value);projected.set(field,value.deepCopy());retained+=4;continue; }
            if (field.equals("party")) {
                if (!value.isObject() || value.size() != 4) fail("Invalid incident reporting-party snapshot");
                for (String key : List.of("type", "id", "label", "roleId")) if (!value.has(key)) fail("Incomplete incident reporting-party snapshot");
                text(value, "type", 16); uuid(value, "id"); text(value, "label", 255); snowflake(value, "roleId");
                retained += value.size();
            } else if (!value.isNull() && !(value.isBoolean() || value.isIntegralNumber()
                    || value.isTextual() && value.textValue().length() <= (table.equals("incident_ruling_publications") && Set.of("content", "previousContent").contains(field) ? 2000 : table.equals("incident_ruling_revisions") && field.equals("text") ? 1800 : table.equals("incident_thread_publications") && Set.of("staffRoleIds","privateMessageIds","publicMessageIds").contains(field)?2000:1000))) {
                fail("Invalid incident retained scalar " + field);
            }
            projected.set(field, value.deepCopy());
        }
        retained += projected.size();
        if (retained > MAX_VALUES) fail("Incident validation exceeds the 100000-reference budget");
        if (index(table).putIfAbsent(uuid(row, "id"), projected) != null) fail("Duplicate incident reference identity in " + table);
    }

    public void validate() {
        if (version < 8 || INCIDENT_TABLES.stream().allMatch(table -> index(table).isEmpty())) return;
        validateContexts();
        var windowEvents = new HashMap<String, SortedMap<Long, JsonNode>>();
        var reportEvents = new HashMap<String, SortedMap<Long, JsonNode>>();
        var keys = new HashSet<String>(); var requests = new HashSet<String>();
        for (var event : index("incident_report_events").values()) {
            locate("incident_report_events", event);
            String window = reference(event, "window", "incident_report_windows");
            long windowVersion = positive(event, "windowVersion"); String kind = text(event, "kind", 24);
            String key = text(event, "eventKey", 120); if (!keys.add(key)) fail("Duplicate incident event key");
            instant(event, "acceptedAt"); instant(event, "deadline"); text(event, "actorId", 128); optionalText(event, "reason", 1000);
            if (event.hasNonNull("report")) {
                String report = reference(event, "report", "incident_reports"); var owner = index("incident_reports").get(report);
                if (!uuid(owner, "window").equals(window) || !text(event, "actorKind", 16).equals("REPORTER")
                        || !text(event, "actorId", 128).equals(text(owner, "authorId", 32))) fail("Incident event ownership contradicts its original report");
                if (!Set.of("SUBMITTED", "EDITED", "WITHDRAWN").contains(kind)) fail("Invalid incident report decision");
                long reportVersion = positive(event, "reportVersion"); positive(event, "revisionNumber");
                if (!key.equals("report:" + report + ":" + reportVersion) || !requests.add(uuid(event, "requestId"))) fail("Invalid incident event key or duplicate durable request");
                if (reportEvents.computeIfAbsent(report, ignored -> new TreeMap<>()).putIfAbsent(reportVersion, event) != null) fail("Duplicate incident report version");
            } else {
                if (!Set.of("OPENED", "DEADLINE_CHANGED", "CLOSED").contains(kind) || event.hasNonNull("requestId")
                        || event.hasNonNull("reportVersion") || event.hasNonNull("revisionNumber")) fail("Invalid incident window decision shape");
                String actor = kind.equals("CLOSED") ? "SYSTEM" : "MANAGEMENT";
                if (!actor.equals(text(event, "actorKind", 16)) || !key.equals("window:" + window + ":" + windowVersion)) fail("Invalid incident window decision provenance");
                text(event, "reason", 1000);
                if (windowEvents.computeIfAbsent(window, ignored -> new TreeMap<>()).putIfAbsent(windowVersion, event) != null) fail("Duplicate incident window version");
            }
        }
        var byReport = new HashMap<String, SortedMap<Long, JsonNode>>(); var revisionRequests = new HashSet<String>();
        for (var revision : index("incident_report_revisions").values()) {
            locate("incident_report_revisions", revision);
            String report = reference(revision, "report", "incident_reports"); long number = positive(revision, "revisionNumber");
            positive(revision, "reportVersion"); instant(revision, "acceptedAt"); snowflake(revision, "actorId");
            if (!revisionRequests.add(uuid(revision, "requestId"))) fail("Duplicate incident revision request");
            if (byReport.computeIfAbsent(report, ignored -> new TreeMap<>()).putIfAbsent(number, revision) != null) fail("Duplicate incident revision number");
        }
        validateWindows(windowEvents);
        var references = new HashSet<String>();
        for (var report : index("incident_reports").values()) {
            locate("incident_reports", report);
            String id = uuid(report, "id"); var window = index("incident_report_windows").get(reference(report, "window", "incident_report_windows"));
            var context = index("incident_match_contexts").get(reference(window, "context", "incident_match_contexts"));
            String caseReference = text(report, "caseReference", 64);
            if (!caseReference.startsWith("INC-") || !references.add(caseReference)) fail("Invalid or duplicate permanent incident reference");
            snowflake(report, "authorId"); validateReportTarget(report, context);
            var history = reportEvents.getOrDefault(id, new TreeMap<>()); var revisions = byReport.getOrDefault(id, new TreeMap<>());
            long expectedVersion = 0, latest = 0; boolean withdrawn = false; Instant previous = null;
            for (var entry : history.entrySet()) {
                var event = entry.getValue(); String kind = text(event, "kind", 24); Instant at = instant(event, "acceptedAt");
                if (entry.getKey() != ++expectedVersion || withdrawn || previous != null && at.isBefore(previous)) fail("Incident report history is not sequential");
                validateReportTime(event, windowEvents.get(uuid(window, "id")));
                if (kind.equals("WITHDRAWN")) {
                    if (latest == 0) fail("An unsubmitted incident cannot be withdrawn"); withdrawn = true;
                } else {
                    if (expectedVersion == 1 && !kind.equals("SUBMITTED") || expectedVersion > 1 && !kind.equals("EDITED")) fail("Incident revision action contradicts its history");
                    var revision = revisions.get(++latest);
                    if (revision == null || positive(revision, "reportVersion") != expectedVersion
                            || !uuid(revision, "requestId").equals(uuid(event, "requestId"))
                            || !text(revision, "actorId", 32).equals(text(event, "actorId", 128))
                            || !instant(revision, "acceptedAt").equals(at)) fail("Incident revision contradicts its immutable decision");
                }
                if (positive(event, "revisionNumber") != latest) fail("Incident decision points to a different revision");
                previous = at;
            }
            if (latest == 0 || revisions.size() != latest || positive(report, "latestRevisionNumber") != latest
                    || positive(report, "version") != expectedVersion || !text(report, "state", 16).equals(withdrawn ? "WITHDRAWN" : "ACTIVE")) {
                fail("Incident workflow pointers contradict immutable history");
            }
            boolean frozen = !withdrawn && text(window, "state", 16).equals("CLOSED");
            if (frozen ? !report.hasNonNull("frozenRevisionNumber") || positive(report, "frozenRevisionNumber") != latest
                    : report.hasNonNull("frozenRevisionNumber")) fail("Incident frozen revision contradicts the closed window");
        }
        validatePublications();
        validateDeliveries();
        validateRulings();
        validateRulingPublications();
    }

    private void validateRulings() {
        var histories = new HashMap<String, SortedMap<Long, JsonNode>>();
        for (var revision : index("incident_ruling_revisions").values()) {
            locate("incident_ruling_revisions", revision);
            String owner = reference(revision, "ruling", "incident_rulings");
            long number = positive(revision, "revisionNumber");
            if (number > Integer.MAX_VALUE || histories.computeIfAbsent(owner, ignored -> new TreeMap<>()).putIfAbsent(number, revision) != null) {
                fail("Duplicate or invalid ruling revision number");
            }
            text(revision, "actorId", 128); instant(revision, "acceptedAt");
            if (optionalText(revision, "reason", 1000) == null) fail("Missing ruling revision reason");
            try { org.ctc.domain.model.IncidentRulingRevision.acceptedText(text(revision, "text", 1800)); }
            catch (org.ctc.domain.exception.BusinessRuleException invalid) { throw new ValidationException("Invalid ruling text"); }
        }
        var owners = new HashSet<String>();
        for (var ruling : index("incident_rulings").values()) {
            locate("incident_rulings", ruling);
            String reportId = reference(ruling, "report", "incident_reports");
            if (!owners.add(reportId)) fail("A permanent case has duplicate ruling histories");
            var history = histories.getOrDefault(uuid(ruling, "id"), new TreeMap<>());
            long latest = positive(ruling, "latestRevisionNumber");
            if (latest > Integer.MAX_VALUE || history.size() != latest || nonnegative(ruling, "version") < latest) fail("Ruling pointers contradict immutable history");
            long expected = 0;
            for (var entry : history.entrySet()) {
                if (entry.getKey() != ++expected) fail("Ruling history is not sequential");
            }
            Long requested = ruling.hasNonNull("requestedRevisionNumber") ? positive(ruling, "requestedRevisionNumber") : null;
            Long published = ruling.hasNonNull("publishedRevisionNumber") ? positive(ruling, "publishedRevisionNumber") : null;
            if (requested != null && requested > latest || published != null && (requested == null || published > requested)) fail("Ruling publication points outside immutable history");
            String message = optionalSnowflake(ruling, "publishedMessageId");
            if ((published == null) != (message == null)) fail("Confirmed ruling is missing its known Discord message");
            if (published != null && !hasPublicDetails(reportId)) fail("Confirmed ruling has no known incident thread");
            var publications = index("incident_ruling_publications").values().stream().filter(publication -> {
                var revision = index("incident_ruling_revisions").get(publication.path("revision").asText());
                return revision != null && uuid(ruling, "id").equals(revision.path("ruling").asText());
            }).toList();
            var sent = publications.stream().filter(publication -> "SENT".equals(publication.path("state").asText())).toList();
            if (nonnegative(ruling, "version") != latest + publications.size() + sent.size()) fail("Ruling aggregate version contradicts its recorded decisions");
            long highestRequest = publications.stream().mapToLong(publication -> positive(index("incident_ruling_revisions").get(publication.path("revision").asText()), "revisionNumber")).max().orElse(0);
            long highestSent = sent.stream().mapToLong(publication -> positive(index("incident_ruling_revisions").get(publication.path("revision").asText()), "revisionNumber")).max().orElse(0);
            if ((requested == null ? 0 : requested) != highestRequest || (published == null ? 0 : published) != highestSent) fail("Ruling confirmation contradicts durable publication evidence");
            if (published != null && sent.stream().noneMatch(publication ->
                    positive(index("incident_ruling_revisions").get(publication.path("revision").asText()), "revisionNumber") == published
                            && Objects.equals(message, optionalSnowflake(publication, "messageId")))) fail("Ruling confirmed message contradicts its exact publication");
            var orderedSent = sent.stream().sorted(Comparator.comparingLong(publication ->
                    positive(index("incident_ruling_revisions").get(publication.path("revision").asText()), "revisionNumber"))).toList();
            JsonNode previousSent = null;
            for (var confirmation : orderedSent) {
                if (previousSent == null) {
                    if (confirmation.hasNonNull("previousRevisionNumber") || confirmation.hasNonNull("previousContent") || confirmation.hasNonNull("previousMessageId")) fail("The initial confirmed ruling cannot claim a prior confirmation");
                } else {
                    long previousNumber = positive(index("incident_ruling_revisions").get(previousSent.path("revision").asText()), "revisionNumber");
                    if (!confirmation.hasNonNull("previousRevisionNumber") || positive(confirmation, "previousRevisionNumber") != previousNumber
                            || !Objects.equals(optionalSnowflake(confirmation, "previousMessageId"), optionalSnowflake(previousSent, "messageId"))
                            || !Objects.equals(optionalText(confirmation, "previousContent", 2000), text(previousSent, "content", 2000))) fail("Confirmed ruling correction does not preserve the preceding confirmed ruling");
                }
                previousSent = confirmation;
            }
        }
    }

    private void validateRulingPublications() {
        var revisions = new HashSet<String>(); var remoteOwners = new HashMap<String, String>();
        var occupied = new HashSet<String>();
        for (var delivery : index("incident_deliveries").values()) if (delivery.hasNonNull("messageId")) occupied.add(text(delivery, "messageId", 32));
        for (var thread : index("incident_thread_publications").values()) {
            for (String field : List.of("privateStarterId", "privateThreadId", "publicStarterId", "publicThreadId", "referenceMessageId"))
                if (thread.hasNonNull(field)) occupied.add(text(thread, field, 32));
            occupied.addAll(ids(thread, "privateMessageIds", 2000, 39, 1)); occupied.addAll(ids(thread, "publicMessageIds", 2000, 39, 1));
        }
        for (var publication : index("incident_ruling_publications").values()) {
            locate("incident_ruling_publications", publication);
            String revisionId = reference(publication, "revision", "incident_ruling_revisions");
            if (!revisions.add(revisionId)) fail("A ruling revision has duplicate publication work");
            var revision = index("incident_ruling_revisions").get(revisionId);
            var root = index("incident_rulings").get(reference(revision, "ruling", "incident_rulings"));
            String reportId = reference(root, "report", "incident_reports"); var report = index("incident_reports").get(reportId);
            var window = index("incident_report_windows").get(reference(report, "window", "incident_report_windows"));
            var thread = index("incident_thread_publications").get(reference(publication, "threadPublication", "incident_thread_publications"));
            var delivery = index("incident_deliveries").get(reference(thread, "delivery", "incident_deliveries"));
            if (!reportId.equals(uuid(delivery, "report")) || !"ACTIVE".equals(text(report, "state", 16))
                    || !"CLOSED".equals(text(window, "state", 16)) || !hasPublicDetails(reportId)
                    || !snowflake(publication, "guildId").equals(snowflake(delivery, "guildId"))
                    || !snowflake(publication, "channelId").equals(optionalSnowflake(thread, "publicThreadId"))) fail("Ruling publication contradicts its original incident destination");
            String content = text(publication, "content", 2000);
            if (!org.ctc.domain.service.IncidentRulingProjection.render(UUID.fromString(reportId), text(report, "caseReference", 64), text(revision, "text", 1800)).equals(content)
                    || !org.ctc.domain.model.IncidentRulingPublication.nonce(UUID.fromString(reportId), content).equals(text(publication, "nonce", 25))) fail("Ruling publication differs from its approved version");
            text(publication, "requestedBy", 128); var requested = instant(publication, "requestedAt");
            if (requested.isBefore(instant(revision, "acceptedAt")) || requested.isBefore(instant(window, "closedAt"))
                    || instant(publication, "nextAttemptAt").isBefore(requested)) fail("Ruling publication predates its confirmed source");
            String previousMessage = optionalSnowflake(publication, "previousMessageId");
            if (publication.hasNonNull("previousRevisionNumber")) {
                long previous = positive(publication, "previousRevisionNumber");
                var prior = index("incident_ruling_revisions").values().stream().filter(row -> uuid(row, "ruling").equals(uuid(root, "id"))
                        && positive(row, "revisionNumber") == previous).findFirst().orElseThrow(() -> new ValidationException("Missing previous ruling revision"));
                if (previous >= positive(revision, "revisionNumber") || previousMessage == null || text(revision, "reason", 1000).isBlank()
                        || !org.ctc.domain.service.IncidentRulingProjection.render(UUID.fromString(reportId), text(report, "caseReference", 64), text(prior, "text", 1800))
                        .equals(text(publication, "previousContent", 2000))) fail("Ruling correction does not preserve its prior confirmed content");
                boolean confirmed = index("incident_ruling_publications").values().stream().anyMatch(row -> uuid(prior, "id").equals(row.path("revision").asText())
                        && "SENT".equals(row.path("state").asText()) && previousMessage.equals(optionalSnowflake(row, "messageId")));
                if (!confirmed) fail("Ruling correction lacks prior message confirmation");
            } else if (previousMessage != null || publication.hasNonNull("previousContent")) fail("Initial ruling has correction-only metadata");
            String state = text(publication, "state", 16); long attempts = nonnegative(publication, "attempts");
            if (!Set.of("PENDING", "CLAIMED", "SENT", "FAILED", "UNCERTAIN", "SUPERSEDED").contains(state)
                    || attempts > Integer.MAX_VALUE || nonnegative(publication, "version") < attempts) fail("Invalid ruling delivery state or version");
            String message = optionalSnowflake(publication, "messageId"), code = optionalText(publication, "failureCode", 64);
            if (Set.of("PENDING", "CLAIMED", "FAILED", "UNCERTAIN").contains(state)) {
                boolean samePrevious = root.hasNonNull("publishedRevisionNumber") == publication.hasNonNull("previousRevisionNumber")
                        && (!root.hasNonNull("publishedRevisionNumber") || positive(root, "publishedRevisionNumber") == positive(publication, "previousRevisionNumber"));
                if (positive(root, "requestedRevisionNumber") != positive(revision, "revisionNumber") || !samePrevious
                        || !Objects.equals(optionalSnowflake(root, "publishedMessageId"), previousMessage)) fail("Unresolved ruling work contradicts the current approved or confirmed version");
            }
            if (message != null && occupied.contains(message)) fail("Ruling confirmation reuses an existing incident message or thread identity");
            if (code != null && !code.matches("[A-Z0-9_]{1,64}")) fail("Invalid ruling failure code");
            boolean claim = publication.hasNonNull("claimToken");
            if (claim) uuid(publication, "claimToken");
            if (claim != publication.hasNonNull("claimGeneration") || claim != publication.hasNonNull("leaseUntil")) fail("Incomplete ruling claim evidence");
            if (claim && (text(publication, "claimGeneration", 64).isBlank() || !instant(publication, "leaseUntil").isAfter(requested))) fail("Invalid ruling lease");
            if (publication.hasNonNull("completedAt") && instant(publication, "completedAt").isBefore(requested)) fail("Ruling completion predates its request");
            if (state.equals("CLAIMED") && (!claim || attempts == 0 || publication.hasNonNull("completedAt") || message != null || code != null)
                    || state.equals("SENT") && (attempts == 0 || !publication.hasNonNull("completedAt") || message == null || code != null || previousMessage != null && !previousMessage.equals(message))
                    || Set.of("FAILED", "UNCERTAIN").contains(state) && (attempts == 0 || !publication.hasNonNull("completedAt") || code == null || message != null)
                    || state.equals("PENDING") && (publication.hasNonNull("completedAt") || message != null || code != null)
                    || state.equals("SUPERSEDED") && (!publication.hasNonNull("completedAt") || message != null || code != null)) fail("Ruling delivery outcome contradicts its evidence");
            if (message != null) {
                var owner = remoteOwners.putIfAbsent(message, reportId);
                if (owner != null && !owner.equals(reportId)) fail("Ruling message belongs to another case");
            }
            boolean reviewed = publication.hasNonNull("reconciledBy");
            if (reviewed != publication.hasNonNull("reconciliationReason")) fail("Incomplete ruling management review");
            if (reviewed) { text(publication, "reconciledBy", 128); text(publication, "reconciliationReason", 1000); }
        }
    }

    private void validateDisclosureIdentity(JsonNode delivery) {
        String pairing = optionalText(delivery, "pairingLabel", 1000), race = optionalText(delivery, "raceLabel", 1000);
        if ((pairing == null) != (race == null)) fail("Incomplete frozen disclosure identity");
        if (pairing != null) {
            if (race == null) throw new ValidationException("Missing frozen race identity");
            if (version < 11 || !"DISCLOSURE".equals(text(delivery, "operation", 16)) || pairing.isBlank() || race.isBlank()) fail("Invalid frozen disclosure identity format");
            var report = index("incident_reports").get(reference(delivery, "report", "incident_reports"));
            if (!race.startsWith("Leg ") || !race.endsWith(" · Race " + uuid(report, "race"))) fail("Frozen disclosure identity belongs to another race");
        }
    }

    private void validateDeliveries() {
        var keys = new HashSet<String>();
        for (var delivery : index("incident_deliveries").values()) {
            locate("incident_deliveries", delivery);
            String reportId = reference(delivery, "report", "incident_reports"); var report = index("incident_reports").get(reportId);
            var revision = index("incident_report_revisions").get(reference(delivery, "revision", "incident_report_revisions"));
            if (!uuid(revision, "report").equals(reportId)) fail("Incident delivery references another report's private revision");
            var window = index("incident_report_windows").get(uuid(report, "window")); var context = index("incident_match_contexts").get(uuid(window, "context"));
            if (!snowflake(delivery, "guildId").equals(snowflake(context, "guildId")) || !snowflake(delivery, "channelId").equals(snowflake(context, "channelId"))) fail("Incident delivery destination contradicts its original context");
            validateDisclosureIdentity(delivery);
            String operation = text(delivery, "operation", 16), state = text(delivery, "state", 16);
            if (!Set.of("RECEIPT", "DISCLOSURE").contains(operation) || !Set.of("PENDING", "CLAIMED", "SENT", "FAILED", "UNCERTAIN", "SUPERSEDED").contains(state)) fail("Invalid incident delivery operation or state");
            String key = text(delivery, "operationKey", 64);
            if (!key.equals(reportId + ":" + operation) || !keys.add(key) || !text(delivery, "caseReference", 64).equals(text(report, "caseReference", 64))) fail("Invalid or duplicate incident delivery identity");
            var id = UUID.fromString(reportId);
            String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array()) + (operation.equals("RECEIPT") ? "R" : "D");
            if (!nonce.equals(text(delivery, "nonce", 25))) fail("Incident delivery nonce does not identify its stable operation");
            var captured = instant(delivery, "capturedAt"); instant(delivery, "nextAttemptAt");
            if (operation.equals("RECEIPT")) {
                if (positive(revision, "revisionNumber") != 1 || !captured.equals(instant(revision, "acceptedAt")) || delivery.hasNonNull("partyLabel")) fail("A neutral receipt must reference only the first committed submission");
            } else if (!text(window, "state", 16).equals("CLOSED") || !text(report, "state", 16).equals("ACTIVE")
                    || positive(report, "frozenRevisionNumber") != positive(revision, "revisionNumber") || !captured.equals(instant(window, "closedAt"))
                    || !text(delivery, "partyLabel", 255).equals(text(report.get("party"), "label", 255))) fail("Incident disclosure contradicts its final frozen revision");
            long attempts = nonnegative(delivery, "attempts"); if (nonnegative(delivery, "version") < attempts) fail("Incident delivery version is older than its claim attempts");
            if (delivery.hasNonNull("claimToken")) uuid(delivery, "claimToken");
            if (delivery.hasNonNull("claimGeneration")) text(delivery, "claimGeneration", 64);
            if (delivery.hasNonNull("leaseUntil") && !instant(delivery, "leaseUntil").isAfter(captured)) fail("Incident delivery lease predates its capture");
            if (delivery.hasNonNull("completedAt") && instant(delivery, "completedAt").isBefore(captured)) fail("Incident delivery completion predates its capture");
            if (delivery.hasNonNull("messageId")) snowflake(delivery, "messageId");
            if (delivery.hasNonNull("failureCode") && !text(delivery, "failureCode", 64).matches("[A-Z0-9_]+")) fail("Incident delivery failure code contains untrusted text");
            if (state.equals("CLAIMED") && (attempts == 0 || !delivery.hasNonNull("claimToken") || !delivery.hasNonNull("claimGeneration") || !delivery.hasNonNull("leaseUntil") || delivery.hasNonNull("completedAt") || delivery.hasNonNull("messageId"))) fail("Incident delivery has an incomplete active claim");
            if (state.equals("SENT") && (attempts == 0 || !delivery.hasNonNull("messageId") || !delivery.hasNonNull("completedAt") || delivery.hasNonNull("failureCode"))) fail("Incident delivery lacks confirmed remote evidence");
            if (Set.of("FAILED", "UNCERTAIN").contains(state) && (attempts == 0 || !delivery.hasNonNull("failureCode") || !delivery.hasNonNull("completedAt"))) fail("Incident delivery lacks a recorded failure outcome");
            if(delivery.hasNonNull("reconciledBy") || delivery.hasNonNull("reconciliationReason")) { text(delivery,"reconciledBy",128);text(delivery,"reconciliationReason",1000); }
            if(state.equals("SUPERSEDED") && (!operation.equals("RECEIPT") || index("incident_deliveries").values().stream().noneMatch(other -> reportId.equals(other.path("report").asText()) && "DISCLOSURE".equals(other.path("operation").asText()) && "SENT".equals(other.path("state").asText())) && !hasPublicDetails(reportId)))fail("A generic receipt was superseded without confirmed disclosure");
        }
    }

    private void validateConfig(JsonNode config) {
        if(config==null || !config.isObject())fail("Invalid incident configuration row");
        for(String field:FIELDS.get("discord_global_config")) {
            var value=config.get(field);
            if(version<10) {if(value!=null && !value.isNull() && (!value.isTextual() || !value.textValue().isEmpty()))fail("Incident destination configuration requires schema version 10");}
            else if(value==null || !value.isTextual())fail("Missing or invalid incident configuration field "+field);
        }
        if(version<10)return;
        String parent=optionalText(config,"stewardsChannelId",20);
        if(parent==null || !parent.isEmpty() && !parent.matches("[0-9]{17,20}"))fail("Invalid stewards destination ID");
        var staff=ids(config,"incidentStaffRoleIds",2000,60,17);var tags=ids(config,"stewardsForumTagIds",200,5,17);
        if(parent.isEmpty()!=staff.isEmpty() || !tags.isEmpty() && parent.isEmpty())fail("Incomplete incident destination configuration");
    }

    private void validatePublications() {
        var owners=new HashSet<String>();var remoteOwners=new HashMap<String,String>();
        for(var delivery:index("incident_deliveries").values()) {
            if("RECEIPT".equals(delivery.path("operation").asText()) && delivery.hasNonNull("messageId")) {
                String remote=snowflake(delivery,"messageId");
                if(remoteOwners.putIfAbsent(remote,uuid(delivery,"id"))!=null)fail("Duplicate neutral receipt identity");
            }
        }
        for(var publication:index("incident_thread_publications").values()) {
            locate("incident_thread_publications",publication);
            String owner=reference(publication,"delivery","incident_deliveries");var delivery=index("incident_deliveries").get(owner);
            if(!owners.add(owner) || !"DISCLOSURE".equals(text(delivery,"operation",16)))fail("Thread publication requires its own unique disclosure operation");
            String parent=snowflake(publication,"stewardsChannelId");
            if(!parent.matches("[0-9]{1,20}") || parent.equals(snowflake(delivery,"channelId")))fail("Private and match destinations must remain distinct");
            if(ids(publication,"staffRoleIds",2000,60,1).isEmpty())fail("Incident thread publication requires configured staff roles");
            ids(publication,"forumTagIds",200,5,1);
            long chunks=positive(publication,"messageChunks");
            var revision=index("incident_report_revisions").get(reference(delivery,"revision","incident_report_revisions"));var payload=revision.get("payload");
            validateDisclosureIdentity(delivery);
            var projection=IncidentThreadProjection.render(text(delivery,"caseReference",64),text(delivery,"partyLabel",255),
                    new IncidentPayload(text(payload,"involvedDrivers",2000),text(payload,"replayTimestamp",200),text(payload,"description",20000),optionalText(payload,"evidenceLinks",8000)),
                    optionalText(delivery,"pairingLabel",1000),optionalText(delivery,"raceLabel",1000));
            if(chunks>40 || chunks!=projection.messages().size())fail("Incident thread chunks contradict their frozen source facts");
            String privateStarter=optionalSnowflake(publication,"privateStarterId"),privateThread=optionalSnowflake(publication,"privateThreadId");
            String publicStarter=optionalSnowflake(publication,"publicStarterId"),publicThread=optionalSnowflake(publication,"publicThreadId"),reference=optionalSnowflake(publication,"referenceMessageId");
            var privateMessages=ids(publication,"privateMessageIds",2000,39,1);var publicMessages=ids(publication,"publicMessageIds",2000,39,1);
            boolean privateComplete=privateThread!=null && privateMessages.size()==chunks-1;
            boolean publicComplete=publicThread!=null && publicMessages.size()==chunks-1;
            if(privateMessages.size()>chunks-1 || publicMessages.size()>chunks-1
                    || privateThread!=null && privateStarter==null || !privateMessages.isEmpty() && privateThread==null
                    || publicStarter!=null && !privateComplete || publicThread!=null && !publicThread.equals(publicStarter)
                    || !publicMessages.isEmpty() && publicThread==null || reference!=null && !publicComplete)fail("Incident thread steps are out of order or incomplete");
            if(privateStarter!=null && nonnegative(delivery,"attempts")==0)fail("Incident remote success lacks delivery provenance");
            boolean sent="SENT".equals(text(delivery,"state",16));
            if(sent && (reference==null || !Objects.equals(publicThread,optionalText(delivery,"messageId",32))) || reference!=null && !sent)fail("Completed incident publication contradicts its delivery outcome");
            if(privateThread!=null && privateThread.equals(publicThread))fail("Private and public threads cannot share an identity");
            var messages=new ArrayList<String>();if(privateStarter!=null)messages.add(privateStarter);messages.addAll(privateMessages);
            if(publicStarter!=null)messages.add(publicStarter);messages.addAll(publicMessages);if(reference!=null)messages.add(reference);
            if(new HashSet<>(messages).size()!=messages.size())fail("Incident publication reused a successful message identity");
            var remotes=new HashSet<>(messages);if(privateThread!=null)remotes.add(privateThread);if(publicThread!=null)remotes.add(publicThread);
            for(var remote:remotes) {var previous=remoteOwners.putIfAbsent(remote,owner);if(previous!=null && !previous.equals(owner))fail("Incident publication reused another operation's remote identity");}
        }
    }
    private boolean hasPublicDetails(String reportId) {
        return index("incident_thread_publications").values().stream().anyMatch(publication -> {
            var delivery=index("incident_deliveries").get(publication.path("delivery").asText());
            return delivery!=null && reportId.equals(delivery.path("report").asText()) && publication.hasNonNull("publicThreadId")
                    && ids(publication,"publicMessageIds",2000,39,1).size()==positive(publication,"messageChunks")-1;
        });
    }
    private static String optionalSnowflake(JsonNode row,String field) {
        String value=optionalText(row,field,20);if(value!=null && !value.matches("[0-9]{1,20}"))fail("Invalid incident Discord reference "+field);return value;
    }
    private static List<String> ids(JsonNode row,String field,int maximum,int count,int minimumDigits) {
        String value=optionalText(row,field,maximum);if(value==null)fail("Missing incident reference list "+field);
        if(value.isEmpty())return List.of();var ids=List.of(value.split(",",-1));
        if(ids.size()>count || new HashSet<>(ids).size()!=ids.size() || ids.stream().anyMatch(id -> !id.matches("[0-9]{1,20}") || id.length()<minimumDigits))fail("Invalid incident reference list "+field);
        return ids;
    }

    private void validateContexts() {
        var targets = new HashSet<String>(); var channels = new HashSet<String>();
        for (var context : index("incident_match_contexts").values()) {
            locate("incident_match_contexts", context);
            String target = contextReference(context);
            if (!targets.add(target) || !channels.add(snowflake(context, "guildId") + ":" + snowflake(context, "channelId"))) fail("Incident context target or channel is not unique");
            String season = reference(context, "season", "seasons"), home = reference(context, "homeTeam", "teams"), away = reference(context, "awayTeam", "teams");
            if (home.equals(away) || positive(context, "version") != 1) fail("Invalid incident binding identity or version");
            text(context, "boundBy", 128); instant(context, "boundAt");
            JsonNode pairing;
            if (context.hasNonNull("match")) {
                String match = reference(context, "match", "matches"); pairing = index("matches").get(match);
                if (!canonicalMatch(match).equals(target) || !Objects.equals(optionalText(pairing, "discordChannelId", 32), snowflake(context, "channelId"))) fail("Incident regular binding contradicts its canonical channel");
                if (!season.equals(seasonForDay(reference(pairing, "matchday", "matchdays")))
                        || !home.equals(reference(pairing, "homeTeam", "teams")) || !away.equals(reference(pairing, "awayTeam", "teams"))) fail("Incident context contradicts its match season or participants");
            } else {
                pairing = index("playoff_matchups").get(reference(context, "playoffMatchup", "playoff_matchups"));
                var round = index("playoff_rounds").get(reference(pairing, "round", "playoff_rounds"));
                var playoff = index("playoffs").get(reference(round, "playoff", "playoffs"));
                var phase = index("season_phases").get(reference(playoff, "phase", "season_phases"));
                if (!season.equals(reference(phase, "season", "seasons")) || !home.equals(reference(pairing, "team1", "teams"))
                        || !away.equals(reference(pairing, "team2", "teams"))) fail("Incident context contradicts its playoff season or participants");
            }
            if (pairing.path("bye").asBoolean(false)) fail("Incident context requires a contested pairing");
            var associated = index("races").values().stream().filter(race -> context.hasNonNull("match")
                    ? Objects.equals(nullableUuid(race, "match"), uuid(context, "match"))
                    : Objects.equals(nullableUuid(race, "playoffMatchup"), uuid(context, "playoffMatchup"))).toList();
            if (associated.isEmpty()) fail("Incident context has no reportable race");
            for (var race : associated) {
                String day = reference(race, "matchday", "matchdays");
                if (!seasonForDay(day).equals(season) || !raceReference(race).equals(target)
                        || !new HashSet<>(raceParticipants(race)).equals(Set.of(home, away))) {
                    fail("Incident context race contradicts its season or participants");
                }
            }
            for (var match : index("matches").values()) {
                if (Objects.equals(optionalText(match, "discordChannelId", 32), snowflake(context, "channelId"))
                        && !canonicalMatch(uuid(match, "id")).equals(target)) fail("The incident channel has ambiguous legacy match bindings");
            }
        }
    }

    private void validateWindows(Map<String, SortedMap<Long, JsonNode>> histories) {
        var contexts = new HashSet<String>();
        for (var window : index("incident_report_windows").values()) {
            locate("incident_report_windows", window);
            String context = reference(window, "context", "incident_match_contexts");
            if (!contexts.add(context)) fail("An incident context has multiple reporting windows");
            var history = histories.getOrDefault(uuid(window, "id"), new TreeMap<>());
            String state = text(window, "state", 16); long version = nonnegative(window, "version");
            if (state.equals("DRAFT")) {
                if (version != 0 || !history.isEmpty()) fail("Draft incident window has confirmed decisions");
                for (String field : List.of("deadline", "plannedStartLocal", "plannedStart", "openedAt", "openedBy", "closedAt")) if (window.hasNonNull(field)) fail("Draft incident window contains confirmed metadata");
                continue;
            }
            if (history.isEmpty()) fail("Incident window has no immutable opening decision");
            long expected = 0; Instant cutoff = null, previous = null; boolean closed = false;
            for (var entry : history.entrySet()) {
                var event = entry.getValue(); String kind = text(event, "kind", 24); Instant at = instant(event, "acceptedAt");
                if (entry.getKey() != ++expected || closed || previous != null && at.isBefore(previous)) fail("Incident window history is not sequential");
                Instant chosen = instant(event, "deadline");
                if (expected == 1) {
                    if (!kind.equals("OPENED") || event.hasNonNull("previousDeadline") || !at.isBefore(chosen)
                            || !at.equals(instant(window, "openedAt")) || !text(event, "actorId", 128).equals(text(window, "openedBy", 128))
                            || at.isBefore(instant(index("incident_match_contexts").get(context), "boundAt"))) fail("Invalid incident window opening evidence");
                } else if (kind.equals("DEADLINE_CHANGED")) {
                    if (!instant(event, "previousDeadline").equals(cutoff) || !at.isBefore(cutoff) || !at.isBefore(chosen)) fail("Incident deadline changed after cutoff or from an unproven value");
                } else if (kind.equals("CLOSED")) {
                    if (!instant(event, "previousDeadline").equals(cutoff) || !chosen.equals(cutoff) || at.isBefore(cutoff)
                            || !at.equals(instant(window, "closedAt"))) fail("Incident window closure contradicts its cutoff");
                    closed = true;
                } else fail("Incident window was reopened or has an invalid decision");
                cutoff = chosen; previous = at;
            }
            if (version != expected || !instant(window, "deadline").equals(cutoff) || !state.equals(closed ? "CLOSED" : "OPEN")
                    || !closed && window.hasNonNull("closedAt")) fail("Incident window state contradicts immutable decisions");
            if (window.hasNonNull("plannedStart")) {
                LocalDateTime local = local(window, "plannedStartLocal"); var offsets = ZoneId.of("Europe/Berlin").getRules().getValidOffsets(local);
                if (offsets.size() != 1 || !local.toInstant(offsets.getFirst()).equals(instant(window, "plannedStart"))) fail("Incident planned-start snapshot has an invalid Berlin offset");
            } else if (window.hasNonNull("plannedStartLocal")) {
                var offsets = ZoneId.of("Europe/Berlin").getRules().getValidOffsets(local(window, "plannedStartLocal"));
                if (offsets.size() == 1) fail("An unambiguous incident planned start lacks its confirmed instant");
            }
        }
        if (contexts.size() != index("incident_match_contexts").size()) fail("An incident context is missing its reporting window");
    }

    private void validateReportTime(JsonNode event, SortedMap<Long, JsonNode> history) {
        long version = positive(event, "windowVersion"); var opening = history == null ? null : history.get(version);
        if (opening == null || text(opening, "kind", 24).equals("CLOSED")) fail("Incident mutation references an unavailable reporting phase");
        Instant at = instant(event, "acceptedAt"), cutoff = instant(opening, "deadline");
        if (at.isBefore(instant(opening, "acceptedAt")) || !at.isBefore(cutoff) || !instant(event, "deadline").equals(cutoff)) fail("Incident mutation was accepted outside its confirmed reporting cutoff");
        var next = history.tailMap(version + 1);
        if (!next.isEmpty() && at.isAfter(instant(next.get(next.firstKey()), "acceptedAt"))) fail("Incident mutation uses a superseded reporting phase");
    }

    private void validateReportTarget(JsonNode report, JsonNode context) {
        var race = index("races").get(reference(report, "race", "races"));
        String day = reference(race, "matchday", "matchdays");
        if (!reference(report, "originalMatchday", "matchdays").equals(day) || !seasonForDay(day).equals(uuid(context, "season"))
                || !raceReference(race).equals(contextReference(context))) fail("Incident report belongs to a different pairing, day or season");
        var participants = raceParticipants(race); String home = participants.get(0), away = participants.get(1);
        if (home.equals(away) || !reference(report, "originalHomeTeam", "teams").equals(home) || !reference(report, "originalAwayTeam", "teams").equals(away)
                || !Set.of(uuid(context, "homeTeam"), uuid(context, "awayTeam")).equals(Set.of(home, away))) fail("Incident report no longer identifies its original race participants");
        var party = report.get("party"); String type = text(party, "type", 16), id = reference(party, "id", "teams");
        if (!Set.of("TEAM", "ORGANIZATION").contains(type) || type.equals("TEAM") && !Set.of(home, away).contains(id)) fail("Incident reporting party does not match its frozen participants");
    }

    private List<String> raceParticipants(JsonNode race) {
        var pair = race.hasNonNull("match") ? index("matches").get(reference(race, "match", "matches"))
                : index("playoff_matchups").get(reference(race, "playoffMatchup", "playoff_matchups"));
        boolean regular = race.hasNonNull("match");
        String home = race.hasNonNull("homeTeamOverride") ? reference(race, "homeTeamOverride", "teams") : reference(pair, regular ? "homeTeam" : "team1", "teams");
        String away = race.hasNonNull("awayTeamOverride") ? reference(race, "awayTeamOverride", "teams") : reference(pair, regular ? "awayTeam" : "team2", "teams");
        return List.of(home, away);
    }

    private String canonicalMatch(String matchId) {
        var associated = index("races").values().stream().filter(race -> Objects.equals(nullableUuid(race, "match"), matchId)).toList();
        var playoffs = associated.stream().map(race -> nullableUuid(race, "playoffMatchup")).filter(Objects::nonNull).distinct().toList();
        if (playoffs.isEmpty()) return "match:" + matchId;
        if (playoffs.size() != 1 || associated.stream().anyMatch(race -> !race.hasNonNull("playoffMatchup"))) fail("Incident legacy match has ambiguous canonical ownership");
        if (!index("playoff_matchups").containsKey(playoffs.getFirst())) fail("Incident canonical playoff reference is missing");
        return "playoff:" + playoffs.getFirst();
    }
    private String raceReference(JsonNode race) {
        if (race.hasNonNull("playoffMatchup")) return "playoff:" + reference(race, "playoffMatchup", "playoff_matchups");
        return "match:" + reference(race, "match", "matches");
    }
    private static String contextReference(JsonNode context) {
        if (context.hasNonNull("match") == context.hasNonNull("playoffMatchup")) fail("Incident context must have exactly one pairing target");
        return context.hasNonNull("match") ? "match:" + uuid(context, "match") : "playoff:" + uuid(context, "playoffMatchup");
    }
    private String seasonForDay(String id) {
        var day = index("matchdays").get(id); var phase = index("season_phases").get(reference(day, "phase", "season_phases"));
        return reference(phase, "season", "seasons");
    }
    private String reference(JsonNode row, String field, String table) {
        String id = uuid(row, field); if (!index(table).containsKey(id)) fail("Missing incident ownership reference " + field); return id;
    }
    private Map<String, JsonNode> index(String table) { return rows.computeIfAbsent(table, ignored -> new LinkedHashMap<>()); }
    private static String nullableUuid(JsonNode row, String field) { return row.hasNonNull(field) ? uuid(row, field) : null; }
    private static String uuid(JsonNode row, String field) {
        String value = text(row, field, 36);
        try {
            String canonical = UUID.fromString(value).toString();
            if (!canonical.equalsIgnoreCase(value)) fail("Invalid incident reference " + field);
            return canonical;
        } catch (IllegalArgumentException invalid) { throw new ValidationException("Invalid incident reference " + field); }
    }
    private static String text(JsonNode row, String field, int maximum) {
        String value = optionalText(row, field, maximum); if (value == null || value.isBlank()) fail("Missing incident text " + field); return value;
    }
    private static String optionalText(JsonNode row, String field, int maximum) {
        var value = row == null ? null : row.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual() || value.textValue().length() > maximum) fail("Invalid incident text " + field);
        return value.textValue();
    }
    private static String snowflake(JsonNode row, String field) {
        String value = text(row, field, 32); if (!value.matches("[0-9]{1,32}")) fail("Invalid incident Discord reference " + field); return value;
    }
    private static long nonnegative(JsonNode row, String field) {
        var value = row == null ? null : row.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) fail("Invalid incident version " + field);
        return value.longValue();
    }
    private static long positive(JsonNode row, String field) { long value = nonnegative(row, field); if (value == 0) fail("Invalid incident pointer " + field); return value; }
    private static Instant instant(JsonNode row, String field) {
        try {
            var value = Instant.parse(text(row, field, 40)); int year = LocalDateTime.ofInstant(value, ZoneOffset.UTC).getYear();
            if (year < 1 || year > 9999 || value.getNano() % 1000 != 0) fail("Invalid incident timestamp " + field);
            return value;
        } catch (java.time.DateTimeException invalid) { throw new ValidationException("Invalid incident timestamp " + field); }
    }
    private static LocalDateTime local(JsonNode row, String field) {
        try {
            var value = LocalDateTime.parse(text(row, field, 40));
            if (value.getYear() < 1 || value.getYear() > 9999) fail("Invalid incident local timestamp " + field);
            return value;
        } catch (java.time.DateTimeException invalid) { throw new ValidationException("Invalid incident local timestamp " + field); }
    }
    private static void validatePayload(JsonNode payload) {
        if (payload == null || !payload.isObject() || payload.size() != 4) fail("Incomplete incident payload");
        for (String field : List.of("involvedDrivers", "replayTimestamp", "description", "evidenceLinks")) if (!payload.has(field)) fail("Incomplete incident payload");
        text(payload, "involvedDrivers", 2000); text(payload, "replayTimestamp", 200); text(payload, "description", 20000);
        if (optionalText(payload, "evidenceLinks", 8000) == null) fail("Missing incident evidence references");
    }
    private static void fail(String message) { throw new ValidationException(message); }
    public static final class ValidationException extends IllegalArgumentException {
        public ValidationException(String message) { super(message); }
    }
}
