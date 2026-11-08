package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class IncidentBackupValidatorTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String SEASON = id(), PHASE = id(), DAY = id(), HOME = id(), AWAY = id(), MATCH = id(), RACE = id(),
            CONTEXT = id(), WINDOW = id(), REPORT = id(), FIRST = id(), SECOND = id();
    final Map<String, List<ObjectNode>> data = new LinkedHashMap<>();

    @BeforeEach
    void fixture() {
        row("seasons", "id", SEASON); row("teams", "id", HOME); row("teams", "id", AWAY);
        row("season_phases", "id", PHASE, "season", SEASON); row("matchdays", "id", DAY, "phase", PHASE);
        row("matches", "id", MATCH, "matchday", DAY, "homeTeam", HOME, "awayTeam", AWAY, "bye", false, "discordChannelId", "200");
        row("races", "id", RACE, "matchday", DAY, "match", MATCH, "playoffMatchup", null, "homeTeamOverride", null, "awayTeamOverride", null);
        row("incident_match_contexts", "id", CONTEXT, "match", MATCH, "playoffMatchup", null, "season", SEASON,
                "homeTeam", HOME, "awayTeam", AWAY, "guildId", "100", "channelId", "200", "version", 1,
                "boundBy", "test-manager", "boundAt", "2026-11-03T11:00:00Z");
        row("incident_report_windows", "id", WINDOW, "context", CONTEXT, "version", 1, "state", "OPEN",
                "plannedStartLocal", "2026-11-02T20:00:00", "plannedStart", "2026-11-02T19:00:00Z",
                "deadline", "2026-11-03T13:00:00Z", "openedAt", "2026-11-03T11:01:00Z", "openedBy", "test-manager", "closedAt", null);
        row("incident_reports", "id", REPORT, "window", WINDOW, "race", RACE, "originalMatchday", DAY,
                "originalHomeTeam", HOME, "originalAwayTeam", AWAY, "caseReference", "INC-TEST", "authorId", "400",
                "party", JSON.createObjectNode().put("type", "TEAM").put("id", HOME).put("label", "Test home").put("roleId", "300"),
                "state", "ACTIVE", "version", 2, "latestRevisionNumber", 2, "frozenRevisionNumber", null);
        revision(1, 1, FIRST, "2026-11-03T12:00:00Z"); revision(2, 2, SECOND, "2026-11-03T12:01:00Z");
        event(null, "OPENED", 1, null, null, null, "2026-11-03T11:01:00Z", null);
        event(REPORT, "SUBMITTED", 1, 1L, 1, FIRST, "2026-11-03T12:00:00Z", null);
        event(REPORT, "EDITED", 1, 2L, 2, SECOND, "2026-11-03T12:01:00Z", null);
    }

    @Test
    void givenCompleteHistoryAndCurrentRoleChanges_whenValidating_thenKeepExactOriginalCaseAttribution() {
        data.get("teams").getFirst().put("discordRoleId", "999");
        assertThatCode(() -> validate(8)).doesNotThrowAnyException();
        assertThat(data.get("incident_reports").getFirst().path("party").path("roleId").asText()).isEqualTo("300");
    }

    @Test
    void givenWithdrawnCase_whenValidating_thenRetainItsLastRevisionAndWithdrawalWithoutExpiry() {
        var report = first("incident_reports"); report.put("state", "WITHDRAWN"); report.put("version", 3);
        event(REPORT, "WITHDRAWN", 1, 3L, 2, id(), "2026-11-03T12:02:00Z", null);
        assertThatCode(() -> validate(8)).doesNotThrowAnyException();
    }

    @Test
    void givenClosedWindowAndFrozenLatestRevision_whenValidating_thenPreserveClosureWithoutNewDeadline() {
        close(); assertThatCode(() -> validate(8)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"two-targets", "season", "participants", "shared-legacy-channel", "latest-pointer", "revision-gap",
            "duplicate-request", "foreign-race", "unrelated-party", "blank-payload", "late-revision", "author", "window-event",
            "frozen-pointer", "unknown-field", "missing-field"})
    void givenMalformedIncidentContract_whenValidating_thenRejectWithoutIncludingPrivatePayload(String corruption) {
        switch (corruption) {
            case "two-targets" -> first("incident_match_contexts").put("playoffMatchup", id());
            case "season" -> { String other = id(); row("seasons", "id", other); first("incident_match_contexts").put("season", other); }
            case "participants" -> first("incident_match_contexts").put("homeTeam", AWAY);
            case "shared-legacy-channel" -> row("matches", "id", id(), "matchday", DAY, "homeTeam", HOME, "awayTeam", AWAY, "bye", false, "discordChannelId", "200");
            case "latest-pointer" -> first("incident_reports").put("latestRevisionNumber", 99);
            case "revision-gap" -> data.get("incident_report_revisions").removeFirst();
            case "duplicate-request" -> data.get("incident_report_revisions").getLast().put("requestId", FIRST);
            case "foreign-race" -> {
                String match = id(), race = id(); row("matches", "id", match, "matchday", DAY, "homeTeam", HOME, "awayTeam", AWAY, "bye", false, "discordChannelId", "201");
                row("races", "id", race, "matchday", DAY, "match", match, "playoffMatchup", null, "homeTeamOverride", null, "awayTeamOverride", null);
                first("incident_reports").put("race", race);
            }
            case "unrelated-party" -> { String team = id(); row("teams", "id", team); ((ObjectNode) first("incident_reports").get("party")).put("id", team); }
            case "blank-payload" -> ((ObjectNode) first("incident_report_revisions").get("payload")).put("description", " ");
            case "late-revision" -> {
                data.get("incident_report_revisions").getLast().put("acceptedAt", "2026-11-03T13:00:00Z");
                data.get("incident_report_events").getLast().put("acceptedAt", "2026-11-03T13:00:00Z");
            }
            case "author" -> first("incident_report_revisions").put("actorId", "401");
            case "window-event" -> first("incident_report_events").put("deadline", "2026-11-03T14:00:00Z");
            case "frozen-pointer" -> { close(); first("incident_reports").put("frozenRevisionNumber", 1); }
            case "unknown-field" -> first("incident_reports").put("untrustedExtraField", "private payload must stay hidden");
            case "missing-field" -> first("incident_report_windows").remove("closedAt");
            default -> throw new IllegalArgumentException(corruption);
        }
        assertThatThrownBy(() -> validate(8)).isInstanceOf(IncidentBackupValidator.ValidationException.class)
                .hasMessageNotContaining("Test private incident facts").hasMessageNotContaining("private payload must stay hidden");
    }

    @Test
    void givenIncidentRowsDisguisedAsOlderSchema_whenValidating_thenRejectInsteadOfGrantingLegacyDefaults() {
        assertThatThrownBy(() -> validate(7)).isInstanceOf(IncidentBackupValidator.ValidationException.class).hasMessageContaining("schema");
    }

    @Test
    void givenSupportedOldArchiveWithoutIncidentRows_whenValidating_thenKeepIncidentStateAbsent() {
        data.keySet().removeIf(table -> table.startsWith("incident_"));
        assertThatCode(() -> validate(7)).doesNotThrowAnyException();
    }

    @Test
    void givenReferenceBudgetExceeded_whenReading_thenRejectBeforeRetainingUnboundedArchiveMetadata() {
        var validator = new IncidentBackupValidator(8);
        assertThatThrownBy(() -> {
            for (int i = 0; i <= 100000; i++) validator.accept("teams", JSON.createObjectNode().put("id", id()));
        }).isInstanceOf(IncidentBackupValidator.ValidationException.class).hasMessageContaining("100000");
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-races", "bye", "foreign-season", "participants"})
    void givenContextWithoutReports_whenItsReportableRaceIdentityIsInvalid_thenRejectBeforeRestore(String corruption) {
        data.get("incident_reports").clear(); data.get("incident_report_revisions").clear();
        data.get("incident_report_events").removeIf(event -> event.hasNonNull("report"));
        switch (corruption) {
            case "no-races" -> data.get("races").clear();
            case "bye" -> first("matches").put("bye", true);
            case "foreign-season" -> {
                String season = id(), phase = id(), day = id(); row("seasons", "id", season);
                row("season_phases", "id", phase, "season", season); row("matchdays", "id", day, "phase", phase);
                first("races").put("matchday", day);
            }
            case "participants" -> first("races").put("awayTeamOverride", HOME);
            default -> throw new IllegalArgumentException(corruption);
        }
        assertThatThrownBy(() -> validate(8)).isInstanceOf(IncidentBackupValidator.ValidationException.class);
    }

    private ObjectNode delivery(String operation) {
        var revision=data.get("incident_report_revisions").get(operation.equals("RECEIPT")?0:1);
        var report=UUID.fromString(REPORT);
        String nonce=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(java.nio.ByteBuffer.allocate(16).putLong(report.getMostSignificantBits()).putLong(report.getLeastSignificantBits()).array())+(operation.equals("RECEIPT")?"R":"D");
        return row("incident_deliveries","id",id(),"report",REPORT,"revision",revision.path("id").asText(),"operation",operation,"operationKey",REPORT+":"+operation,"guildId","100","channelId","200","nonce",nonce,"caseReference","INC-TEST","partyLabel",operation.equals("RECEIPT")?null:"Test home","capturedAt",operation.equals("RECEIPT")?"2026-11-03T12:00:00Z":"2026-11-03T13:00:00Z","state","PENDING","version",4,"attempts",2,"claimToken",null,"claimGeneration",null,"leaseUntil",null,"nextAttemptAt","2026-11-03T13:00:00Z","completedAt",null,"messageId",null,"failureCode",null,"reconciledBy",null,"reconciliationReason",null);
    }
    private ObjectNode publication() {
        close();var delivery=delivery("DISCLOSURE");
        return row("incident_thread_publications","id",id(),"delivery",delivery.path("id").asText(),"stewardsChannelId","600","staffRoleIds","500","forumTagIds","","messageChunks",1,"privateStarterId","701","privateThreadId","701","privateMessageIds","","publicStarterId",null,"publicThreadId",null,"publicMessageIds","","referenceMessageId",null);
    }
    @Test void givenAStoredPartialThreadPair_whenValidating_thenRetainItsOwnFrozenDeliveryAndExactReferences() {
        publication();assertThatCode(() -> validate(10)).doesNotThrowAnyException();
    }

    @Test void givenReadableDisclosure_whenValidating_thenPreserveItsVersionAndChunkContract() {
        publication();
        var delivery = first("incident_deliveries");
        delivery.put("presentationVersion", 1).put("pairingLabel", "Test home vs Test away")
                .put("raceLabel", "Leg 1 · Test season · Test day");
        assertThatCode(() -> validate(21)).doesNotThrowAnyException();
    }

    @ParameterizedTest @ValueSource(strings = {"unsupported", "fraction", "text", "receipt", "missing-captions", "legacy-schema", "missing-version"})
    void givenUntrustedPresentationMetadata_whenValidating_thenRejectBeforeRestore(String corruption) {
        publication();
        var delivery = first("incident_deliveries");
        delivery.put("presentationVersion", 1).put("pairingLabel", "Test home vs Test away")
                .put("raceLabel", "Leg 1 · Test season · Test day");
        switch (corruption) {
            case "unsupported" -> delivery.put("presentationVersion", 2);
            case "fraction" -> delivery.put("presentationVersion", 1.1);
            case "text" -> delivery.put("presentationVersion", "1");
            case "receipt" -> delivery.put("operation", "RECEIPT");
            case "missing-captions" -> delivery.putNull("pairingLabel").putNull("raceLabel");
            case "missing-version" -> delivery.remove("presentationVersion");
            case "legacy-schema" -> { }
            default -> throw new AssertionError(corruption);
        }
        assertThatThrownBy(() -> validate(corruption.equals("legacy-schema") ? 20 : 21))
                .isInstanceOf(IncidentBackupValidator.ValidationException.class).hasMessageNotContaining("Test private incident facts");
    }

    @ParameterizedTest @ValueSource(strings = {"unsupported", "legacy-schema", "missing-captions"})
    void givenDeliveryBeforeAnyRemoteMapping_whenPresentationIsInvalid_thenRejectAtTheEnvelopeBoundary(String corruption) {
        close();
        var delivery = delivery("DISCLOSURE");
        delivery.put("presentationVersion", corruption.equals("unsupported") ? 2 : 1)
                .put("pairingLabel", "Test home vs Test away").put("raceLabel", "Leg 1 · Test season · Test day");
        if (corruption.equals("missing-captions")) delivery.putNull("pairingLabel").putNull("raceLabel");
        assertThatThrownBy(() -> validate(corruption.equals("legacy-schema") ? 20 : 21))
                .isInstanceOf(IncidentBackupValidator.ValidationException.class).hasMessageNotContaining("Test private incident facts");
    }
    @ParameterizedTest @ValueSource(strings={"owner","receipt","destination","staff","tags","chunks","order","thread","reference","complete","review-grant","duplicate","shared-remote"})
    void givenContradictoryThreadHistory_whenValidating_thenRejectBeforeRestoreWithoutLeakingFacts(String cause) {
        var publication=publication();
        switch(cause) {
            case "owner" -> publication.put("delivery",id());
            case "receipt" -> publication.put("delivery",delivery("RECEIPT").path("id").asText());
            case "destination" -> publication.put("stewardsChannelId","200");
            case "staff" -> publication.put("staffRoleIds","");
            case "tags" -> publication.put("forumTagIds","900,900");
            case "chunks" -> publication.put("messageChunks",2);
            case "order" -> {publication.putNull("privateThreadId");publication.put("publicStarterId","801");}
            case "thread" -> {publication.put("publicStarterId","801");publication.put("publicThreadId","802");}
            case "reference" -> publication.put("referenceMessageId","702");
            case "complete" -> {var delivery=first("incident_deliveries");delivery.put("state","SENT");delivery.put("messageId","801");delivery.put("completedAt","2026-11-03T13:00:00Z");}
            case "review-grant" -> publication.put("restoreReviewed",true);
            case "duplicate" -> {var copy=publication.deepCopy();copy.put("id",id());data.get("incident_thread_publications").add(copy);}
            case "shared-remote" -> {publication.put("publicStarterId","701");publication.put("publicThreadId","701");}
            default -> throw new AssertionError(cause);
        }
        assertThatThrownBy(() -> validate(10)).isInstanceOf(IncidentBackupValidator.ValidationException.class).hasMessageNotContaining("Test private incident facts");
    }
    @Test void givenThreadDataDisguisedAsSchemaNine_whenValidating_thenRejectInsteadOfInventingLegacyAuthority() {
        publication();assertThatThrownBy(() -> validate(9)).isInstanceOf(IncidentBackupValidator.ValidationException.class);
    }
    @Test void givenPublicDetailsCompleteButPrivateReferencePending_whenValidatingASupersededReceipt_thenAcceptTheDurablePublicEvidence() {
        var publication=publication();publication.put("publicStarterId","801");publication.put("publicThreadId","801");
        var receipt=delivery("RECEIPT");receipt.put("state","SUPERSEDED");receipt.put("completedAt","2026-11-03T13:00:00Z");
        assertThatCode(() -> validate(10)).doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(strings={"type","parent","staff","tags","duplicate","incomplete","legacy"})
    void givenUnverifiedIncidentConfiguration_whenValidating_thenRejectItWithoutSilentlyReplacingFields(String cause) {
        var config=row("discord_global_config","id",1,"guildId","100","stewardsChannelId","600000000000000006","incidentStaffRoleIds","500000000000000005","stewardsForumTagIds","");
        switch(cause) {
            case "type" -> config.put("stewardsChannelId",600);
            case "parent" -> config.put("stewardsChannelId","private-stewards");
            case "staff" -> config.put("incidentStaffRoleIds","@everyone");
            case "tags" -> config.put("stewardsForumTagIds","unknown-tag");
            case "duplicate" -> config.put("incidentStaffRoleIds","500000000000000005,500000000000000005");
            case "incomplete" -> config.put("incidentStaffRoleIds","");
            case "legacy" -> { }
            default -> throw new AssertionError(cause);
        }
        assertThatThrownBy(() -> validate(cause.equals("legacy")?9:10)).isInstanceOf(IncidentBackupValidator.ValidationException.class);
    }

    @Test
    void givenPermanentRulingDraft_whenValidating_thenRetainExactTextAndTrustedAuthor() {
        rulingDraft();
        assertThatCode(() -> validate(11)).doesNotThrowAnyException();
        assertThat(first("incident_ruling_revisions").path("text").asText()).isEqualTo("Test steward decision <script>literal</script>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"legacy", "foreign-report", "duplicate-owner", "foreign-root", "gap", "pointer", "version", "blank-text", "oversized", "actor", "future-pointer", "unknown-field"})
    void givenMalformedRulingArchive_whenValidating_thenRejectBeforeRestoreWithoutLeakingTheDecision(String corruption) {
        rulingDraft();
        var ruling = first("incident_rulings"); var revision = first("incident_ruling_revisions");
        switch (corruption) {
            case "legacy" -> { }
            case "foreign-report" -> ruling.put("report", id());
            case "duplicate-owner" -> { var copy = ruling.deepCopy(); copy.put("id", id()); data.get("incident_rulings").add(copy); }
            case "foreign-root" -> revision.put("ruling", id());
            case "gap" -> revision.put("revisionNumber", 2);
            case "pointer" -> ruling.put("latestRevisionNumber", 2);
            case "version" -> ruling.put("version", 0);
            case "blank-text" -> revision.put("text", " ");
            case "oversized" -> revision.put("text", "x".repeat(1801));
            case "actor" -> revision.put("actorId", " ");
            case "future-pointer" -> ruling.put("requestedRevisionNumber", 2);
            case "unknown-field" -> revision.put("forgedAuthority", true);
            default -> throw new AssertionError(corruption);
        }
        assertThatThrownBy(() -> validate(corruption.equals("legacy") ? 10 : 11))
                .isInstanceOf(IncidentBackupValidator.ValidationException.class)
                .hasMessageNotContaining("Test steward decision");
    }

    @Test
    void givenExactApprovedRulingWork_whenValidating_thenRetainTheCapturedVersionAndKnownThread() {
        rulingPublication(); assertThatCode(() -> validate(11)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"revision", "thread", "guild", "channel", "content", "nonce", "claimed", "sent", "state", "review-grant", "aggregate", "duplicate"})
    void givenForgedRulingPublicationEvidence_whenValidating_thenRejectBeforeWiping(String corruption) {
        var publication = rulingPublication();
        switch (corruption) {
            case "revision" -> publication.put("revision", id());
            case "thread" -> publication.put("threadPublication", id());
            case "guild" -> publication.put("guildId", "999");
            case "channel" -> publication.put("channelId", "999");
            case "content" -> publication.put("content", "Test substituted public judgment");
            case "nonce" -> publication.put("nonce", "forged-nonce");
            case "claimed" -> publication.put("state", "CLAIMED");
            case "sent" -> publication.put("state", "SENT");
            case "state" -> publication.put("state", "UNKNOWN");
            case "review-grant" -> publication.put("restoreReviewed", true);
            case "aggregate" -> first("incident_rulings").put("version", 20);
            case "duplicate" -> { var copy = publication.deepCopy(); copy.put("id", id()); data.get("incident_ruling_publications").add(copy); first("incident_rulings").put("version", 3); }
            default -> throw new AssertionError(corruption);
        }
        assertThatThrownBy(() -> validate(11)).isInstanceOf(IncidentBackupValidator.ValidationException.class)
                .hasMessageNotContaining("Test steward decision");
    }

    @Test void aCurrentCorrectionCanCaptureTheLatestConfirmedRulingInAThreeVersionHistory() {
        threeRulingVersions(); assertThatCode(() -> validate(11)).doesNotThrowAnyException();
    }
    @Test void firstConfirmationMaySkipSupersededWorkAndEarlierDrafts() {
        threeRulingVersions(); var work = data.get("incident_ruling_publications");
        var superseded = work.getFirst(); superseded.put("state", "SUPERSEDED"); superseded.put("version", 3); superseded.putNull("messageId");
        superseded.put("completedAt", "2026-11-03T13:01:00Z"); superseded.put("nextAttemptAt", "2026-11-03T13:01:00Z");
        for (String field : List.of("previousRevisionNumber", "previousContent", "previousMessageId")) work.get(1).putNull(field);
        first("incident_rulings").put("version", 7);
        assertThatCode(() -> validate(11)).doesNotThrowAnyException();
    }

    @ParameterizedTest @ValueSource(strings = {"stale-previous", "older-unresolved"})
    void contradictoryCurrentOrUnreplacedRulingWorkMustRejectBeforeRestore(String cause) {
        threeRulingVersions(); var publications = data.get("incident_ruling_publications");
        var current = publications.get(2); current.put("previousRevisionNumber", 1); current.put("previousContent", publications.getFirst().path("content").asText());
        if (cause.equals("older-unresolved")) {
            var previous = publications.get(1); previous.put("state", "FAILED"); previous.putNull("messageId"); previous.put("failureCode", "DESTINATION");
            first("incident_rulings").put("publishedRevisionNumber", 1); first("incident_rulings").put("version", 7);
        }
        assertThatThrownBy(() -> validate(11)).isInstanceOf(IncidentBackupValidator.ValidationException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"701", "801"})
    void aConfirmedRulingCannotClaimAnAlreadyRecordedIncidentMessageOrThread(String occupied) {
        var publication = rulingPublication(); var root = first("incident_rulings");
        publication.put("state", "SENT"); publication.put("version", 2); publication.put("attempts", 1);
        publication.put("messageId", occupied); publication.put("completedAt", "2026-11-03T13:00:00Z");
        publication.put("claimToken", id()); publication.put("claimGeneration", "test-runtime"); publication.put("leaseUntil", "2026-11-03T13:10:00Z");
        root.put("publishedRevisionNumber", 1); root.put("publishedMessageId", occupied); root.put("version", 3);
        assertThatThrownBy(() -> validate(11)).isInstanceOf(IncidentBackupValidator.ValidationException.class);
    }
    @Test void aHistoricalConfirmedCorrectionCannotEraseItsPrecedingConfirmedVersion() {
        threeRulingVersions(); var correction = data.get("incident_ruling_publications").get(1);
        for (String field : List.of("previousRevisionNumber", "previousContent", "previousMessageId")) correction.putNull(field);
        assertThatThrownBy(() -> validate(11)).isInstanceOf(IncidentBackupValidator.ValidationException.class);
    }

    private void threeRulingVersions() {
        var first = rulingPublication(); var root = first("incident_rulings"); var revision = first("incident_ruling_revisions");
        first.put("state", "SENT"); first.put("version", 2); first.put("attempts", 1); first.put("messageId", "950"); first.put("completedAt", "2026-11-03T13:00:00Z");
        first.put("claimToken", id()); first.put("claimGeneration", "test-runtime"); first.put("leaseUntil", "2026-11-03T13:10:00Z");
        ObjectNode previous = first;
        for (int number = 2; number <= 3; number++) {
            var nextRevision = revision.deepCopy(); nextRevision.put("id", id()); nextRevision.put("revisionNumber", number);
            nextRevision.put("text", "Test ruling version " + number); nextRevision.put("reason", "Test reviewed correction " + number);
            String at = number == 2 ? "2026-11-03T13:01:00Z" : "2026-11-03T13:02:00Z"; nextRevision.put("acceptedAt", at);
            data.get("incident_ruling_revisions").add(nextRevision);
            var next = first.deepCopy(); next.put("id", id()); next.put("revision", nextRevision.path("id").asText());
            String content = org.ctc.domain.service.IncidentRulingProjection.render(UUID.fromString(REPORT), "INC-TEST", nextRevision.path("text").asText());
            next.put("content", content); next.put("nonce", org.ctc.domain.model.IncidentRulingPublication.nonce(UUID.fromString(REPORT), content));
            next.put("requestedAt", at); next.put("nextAttemptAt", at); next.put("previousRevisionNumber", number - 1);
            next.put("previousContent", previous.path("content").asText()); next.put("previousMessageId", "950");
            if (number == 3) {
                next.put("state", "PENDING"); next.put("version", 0); next.put("attempts", 0);
                for (String field : List.of("claimToken", "claimGeneration", "leaseUntil", "completedAt", "messageId")) next.putNull(field);
            } else { next.put("completedAt", at); next.put("claimToken", id()); next.put("leaseUntil", "2026-11-03T13:11:00Z"); }
            data.get("incident_ruling_publications").add(next); previous = next;
        }
        root.put("latestRevisionNumber", 3); root.put("requestedRevisionNumber", 3); root.put("publishedRevisionNumber", 2);
        root.put("publishedMessageId", "950"); root.put("version", 8);
    }

    private ObjectNode rulingPublication() {
        rulingDraft(); var thread = publication(); thread.put("publicStarterId", "801"); thread.put("publicThreadId", "801");
        first("incident_rulings").put("requestedRevisionNumber", 1); first("incident_rulings").put("version", 2);
        var revision = first("incident_ruling_revisions");
        String content = org.ctc.domain.service.IncidentRulingProjection.render(UUID.fromString(REPORT), "INC-TEST", revision.path("text").asText());
        return row("incident_ruling_publications", "id", id(), "revision", revision.path("id").asText(), "threadPublication", thread.path("id").asText(),
                "guildId", "100", "channelId", "801", "content", content, "previousContent", null, "previousRevisionNumber", null, "previousMessageId", null,
                "nonce", org.ctc.domain.model.IncidentRulingPublication.nonce(UUID.fromString(REPORT), content), "requestedBy", "test-steward",
                "requestedAt", "2026-11-03T13:00:00Z", "state", "PENDING", "version", 0, "attempts", 0,
                "claimToken", null, "claimGeneration", null, "leaseUntil", null, "nextAttemptAt", "2026-11-03T13:00:00Z",
                "completedAt", null, "messageId", null, "failureCode", null, "reconciledBy", null, "reconciliationReason", null);
    }

    private void rulingDraft() {
        String ruling = id();
        row("incident_rulings", "id", ruling, "report", REPORT, "version", 1, "latestRevisionNumber", 1,
                "requestedRevisionNumber", null, "publishedRevisionNumber", null, "publishedMessageId", null);
        row("incident_ruling_revisions", "id", id(), "ruling", ruling, "revisionNumber", 1,
                "text", "Test steward decision <script>literal</script>", "reason", "", "actorId", "test-steward",
                "acceptedAt", "2026-11-03T12:02:00Z");
    }

    private void validate(int version) {
        var validator = new IncidentBackupValidator(version);
        data.forEach((table, rows) -> rows.forEach(row -> validator.accept(table, row)));
        validator.validate();
    }
    private void close() {
        var window = first("incident_report_windows"); window.put("state", "CLOSED"); window.put("version", 2); window.put("closedAt", "2026-11-03T13:00:00Z");
        first("incident_reports").put("frozenRevisionNumber", 2);
        event(null, "CLOSED", 2, null, null, null, "2026-11-03T13:00:00Z", "2026-11-03T13:00:00Z");
    }
    private void revision(int number, long version, String request, String at) {
        row("incident_report_revisions", "id", id(), "report", REPORT, "revisionNumber", number, "reportVersion", version,
                "payload", JSON.createObjectNode().put("involvedDrivers", "Test driver and guest").put("replayTimestamp", "00:00")
                        .put("description", "Test private incident facts").put("evidenceLinks", "https://example.invalid/footage"),
                "actorId", "400", "acceptedAt", at, "requestId", request);
    }
    private void event(String report, String kind, long windowVersion, Long reportVersion, Integer revision, String request, String at, String previous) {
        row("incident_report_events", "id", id(), "window", WINDOW, "report", report,
                "eventKey", report == null ? "window:" + WINDOW + ":" + windowVersion : "report:" + report + ":" + reportVersion,
                "kind", kind, "windowVersion", windowVersion, "reportVersion", reportVersion, "revisionNumber", revision,
                "actorKind", report != null ? "REPORTER" : kind.equals("CLOSED") ? "SYSTEM" : "MANAGEMENT",
                "actorId", report != null ? "400" : "test-manager", "reason", report == null ? "Test explicit window decision" : "",
                "acceptedAt", at, "previousDeadline", previous, "deadline", "2026-11-03T13:00:00Z", "requestId", request);
    }
    private ObjectNode first(String table) { return data.get(table).getFirst(); }
    private ObjectNode row(String table, Object... values) {
        var node = JSON.createObjectNode();
        for (int i = 0; i < values.length; i += 2) node.set((String) values[i], JSON.valueToTree(values[i + 1]));
        if (table.startsWith("incident_")) { node.putNull("createdAt"); node.putNull("updatedAt"); }
        if (table.equals("incident_deliveries")) { node.putNull("pairingLabel"); node.putNull("raceLabel"); node.putNull("presentationVersion"); }
        data.computeIfAbsent(table, ignored -> new ArrayList<>()).add(node); return node;
    }
    private static String id() { return UUID.randomUUID().toString(); }
}
