package org.ctc.domain.model;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.persistence.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.ctc.domain.exception.BusinessRuleException;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

@Entity
@Table(name = "pick_publication_attempts")
@DynamicUpdate
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PickPublicationAttempt extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, updatable = false) private UUID batchId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "race_id", nullable = false, updatable = false) private Race race;
    @Column(nullable = false, updatable = false) private UUID seasonId;
    @Column(nullable = false, updatable = false) private UUID originalMatchdayId;
    @Column(nullable = false, updatable = false) private UUID originalHomeTeamId;
    @Column(nullable = false, updatable = false) private UUID originalAwayTeamId;
    @Column(nullable = false, updatable = false) private long contentVersion;
    @Column(nullable = false, updatable = false) private long sourceVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16, updatable = false) private PickPublicationOrigin origin;
    @Enumerated(EnumType.STRING) @Column(name = "output_type", nullable = false, length = 24, updatable = false) private PickPublicationOutput output;
    @Embedded private PickPublicationContent content;
    @Column(nullable = false, length = 64, updatable = false) private String contentHash;
    @Column(nullable = false, updatable = false) private int fieldMask;
    @Column(nullable = false, length = 128, updatable = false) private String actorId;
    @Column(nullable = false, length = 64, updatable = false) private String channelId;
    @Column(nullable = false, length = 64, updatable = false) private String webhookId;
    @Column(length = 64, updatable = false) private String targetMessageId;
    @Column(nullable = false, length = 64, updatable = false) private String templateHash;
    @Column(nullable = false, columnDefinition = "TEXT", updatable = false) private String requestManifest;
    @Column(nullable = false, length = 64, updatable = false) private String requestHash;
    @Column(nullable = false, length = 64, updatable = false) private String runtimeGeneration;
    @Convert(converter = UtcInstantConverter.class) @Column(nullable = false, updatable = false) private Instant capturedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private PickPublicationState state;
    @Column(unique = true) private UUID activeRaceId;
    @Column(length = 64) private String messageId;
    @Convert(converter = UtcInstantConverter.class) private Instant completedAt;
    @Column(length = 64) private String failureCode;
    @Column(length = 128) private String reconciledBy;
    @Column(length = 1000) private String reconciliationReason;
    @Convert(converter = UtcInstantConverter.class) private Instant resolvedAt;

    public record Capture(UUID batchId, Race race, long contentVersion, long sourceVersion, PickPublicationOrigin origin,
                          PickPublicationOutput output, PickPublicationContent content, Set<PickDisclosureField> fields,
                          String actorId, String channelId, String webhookId, String targetMessageId, String templateHash,
                          String requestManifest, String runtimeGeneration, Instant capturedAt) { }

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "JPA requires proxyable entities; this constructor owns no resources and neither this entity nor its base defines a finalizer.")
    public PickPublicationAttempt(Capture capture) {
        if (capture == null) throw new BusinessRuleException("Publication requires complete capture metadata");
        require(capture.batchId() != null && capture.race() != null && capture.race().getId() != null
                && capture.content() != null && capture.origin() != null && capture.output() != null && capture.fields() != null
                && capture.capturedAt() != null, "requires complete capture metadata");
        var day = capture.race().getMatchday();
        require(day != null && day.getId() != null && day.getSeason() != null && day.getSeason().getId() != null
                && capture.race().getHomeTeam() != null && capture.race().getHomeTeam().getId() != null
                && capture.race().getAwayTeam() != null && capture.race().getAwayTeam().getId() != null, "requires an exact target identity");
        require(capture.contentVersion() > 0, "requires a positive content version");
        require(capture.origin() == PickPublicationOrigin.LEGACY ? capture.sourceVersion() == 0 : capture.sourceVersion() > 0,
                "requires an exact source version");
        require(capture.output() != PickPublicationOutput.LOBBY_SETTINGS || !capture.fields().contains(PickDisclosureField.CAR),
                "lobby output cannot release car choices");
        require(text(capture.actorId(), 128) && text(capture.channelId(), 64) && text(capture.webhookId(), 64), "requires server actor and output identity");
        require(capture.targetMessageId() == null || text(capture.targetMessageId(), 64), "has an invalid prior message identity");
        require(capture.templateHash() != null && capture.templateHash().matches("[0-9a-f]{64}"), "requires an exact captured template");
        require(text(capture.requestManifest(), 16000), "requires a bounded output manifest");
        require(capture.runtimeGeneration() != null && capture.runtimeGeneration().length() <= 64
                && capture.runtimeGeneration().matches("[0-9a-f-]{36}:[0-9]+"), "requires current runtime context");
        batchId = capture.batchId(); race = capture.race(); seasonId = race.getMatchday().getSeason().getId();
        originalMatchdayId = race.getMatchday().getId(); originalHomeTeamId = race.getHomeTeam().getId(); originalAwayTeamId = race.getAwayTeam().getId();
        contentVersion = capture.contentVersion(); sourceVersion = capture.sourceVersion(); origin = capture.origin(); output = capture.output();
        content = capture.content(); contentHash = content.fingerprint(); fieldMask = PickDisclosureField.mask(capture.fields());
        actorId = capture.actorId(); channelId = capture.channelId(); webhookId = capture.webhookId(); targetMessageId = capture.targetMessageId();
        templateHash = capture.templateHash(); requestManifest = capture.requestManifest(); requestHash = manifestHash(requestManifest); runtimeGeneration = capture.runtimeGeneration();
        capturedAt = capture.capturedAt().truncatedTo(ChronoUnit.MICROS); state = PickPublicationState.PREPARED; activeRaceId = race.getId();
    }

    public Set<PickDisclosureField> fields() { return PickDisclosureField.fields(fieldMask); }

    public boolean matchesTarget(Race target) {
        return target != null && race.getId().equals(target.getId()) && target.getMatchday() != null
                && originalMatchdayId.equals(target.getMatchday().getId()) && target.getMatchday().getSeason() != null
                && seasonId.equals(target.getMatchday().getSeason().getId()) && target.getHomeTeam() != null && target.getAwayTeam() != null
                && originalHomeTeamId.equals(target.getHomeTeam().getId()) && originalAwayTeamId.equals(target.getAwayTeam().getId());
    }

    public void confirm(String message, Instant at) {
        require(state == PickPublicationState.PREPARED, "requires explicit reconciliation of an uncertain outcome");
        require(text(message, 64), "requires a confirmed message identity");
        var time = outcomeTime(at);
        messageId = message; completedAt = time; state = PickPublicationState.SUCCEEDED; activeRaceId = null;
    }

    public void fail(String code, Instant at) {
        require(state == PickPublicationState.PREPARED, "outcome is already final or uncertain");
        require(code != null && code.matches("[A-Z0-9_-]{1,64}"), "requires a bounded failure category");
        var time = outcomeTime(at);
        failureCode = code; completedAt = time; state = PickPublicationState.FAILED; activeRaceId = null;
    }

    public void uncertain(String code, Instant at) {
        uncertain(code, null, at);
    }

    public void uncertain(String code, String possibleMessageId, Instant at) {
        require(possibleMessageId == null || text(possibleMessageId, 64), "has an invalid possible message identity");
        require(state == PickPublicationState.PREPARED, "outcome is already final or uncertain");
        require(code != null && code.matches("[A-Z0-9_-]{1,64}"), "requires a bounded uncertainty category");
        var time = outcomeTime(at);
        failureCode = code; completedAt = time; messageId = possibleMessageId; state = PickPublicationState.UNCERTAIN;
    }

    public void reconcile(String message, String actor, String reason, Instant at) {
        require(text(message, 64), "requires a verified message identity");
        var time = reconciliationTime(actor, reason, at);
        messageId = message; reconciledBy = actor; reconciliationReason = reason; resolvedAt = time;
        if (completedAt == null) completedAt = time;
        state = PickPublicationState.SUCCEEDED; activeRaceId = null;
    }

    public void abandon(String actor, String reason, Instant at) {
        var time = reconciliationTime(actor, reason, at);
        reconciledBy = actor; reconciliationReason = reason; resolvedAt = time;
        if (completedAt == null) completedAt = time;
        state = PickPublicationState.ABANDONED; activeRaceId = null;
    }

    private Instant reconciliationTime(String actor, String reason, Instant at) {
        require(state == PickPublicationState.PREPARED || state == PickPublicationState.UNCERTAIN, "outcome is already final");
        require(text(actor, 128) && text(reason, 1000), "reconciliation requires a management actor and reason");
        var time = outcomeTime(at);
        require(completedAt == null || !time.isBefore(completedAt), "reconciliation cannot precede the delivery outcome");
        return time;
    }

    private Instant outcomeTime(Instant at) {
        if (at == null || at.isBefore(capturedAt)) throw new BusinessRuleException("Publication outcome cannot precede captured input");
        return at.truncatedTo(ChronoUnit.MICROS);
    }

    public static boolean hashMatches(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    public static String manifestHash(String manifest) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("Required SHA-256 support is unavailable", unavailable);
        }
    }

    private static boolean text(String value, int max) { return value != null && !value.isBlank() && value.length() <= max; }
    private static void require(boolean condition, String reason) { if (!condition) throw new BusinessRuleException("Publication " + reason); }
}
