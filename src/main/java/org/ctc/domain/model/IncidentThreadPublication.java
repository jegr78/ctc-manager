package org.ctc.domain.model;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;

@Entity
@Table(name = "incident_thread_publications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentThreadPublication extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(nullable = false, unique = true, updatable = false)
    private IncidentDelivery delivery;
    @Column(nullable = false, length = 32, updatable = false) private String stewardsChannelId;
    @Column(nullable = false, length = 2000, updatable = false) private String staffRoleIds;
    @Column(nullable = false, length = 200, updatable = false) private String forumTagIds;
    @Column(nullable = false, updatable = false) private int messageChunks;
    @Column(length = 32) private String privateStarterId;
    @Column(length = 32) private String privateThreadId;
    @Column(nullable = false, length = 2000) private String privateMessageIds = "";
    @Column(length = 32) private String publicStarterId;
    @Column(length = 32) private String publicThreadId;
    @Column(nullable = false, length = 2000) private String publicMessageIds = "";
    @Column(length = 32) private String referenceMessageId;

    public static IncidentThreadPublication capture(IncidentDelivery delivery, String privateParent, String staff, String tags, int chunks) {
        if (!(delivery != null && delivery.getId() != null && delivery.getOperation() == IncidentDeliveryOperation.DISCLOSURE
                && delivery.getReport().getState() == IncidentReportState.ACTIVE
                && delivery.getReport().getWindow().getState() == IncidentWindowState.CLOSED
                && delivery.getReport().getFrozenRevisionNumber() != null
                && delivery.getReport().getFrozenRevisionNumber() == delivery.getRevision().getRevisionNumber())) throw unavailable();
        require(snowflake(privateParent) && !privateParent.equals(delivery.getChannelId()));
        if (!(staff != null && staff.length() <= 2000 && !ids(staff, 60).isEmpty())) throw unavailable();
        if (!(tags != null && tags.length() <= 200)) throw unavailable();ids(tags, 5);
        require(chunks >= 1 && chunks <= 40);
        var row = new IncidentThreadPublication();row.delivery = delivery;row.stewardsChannelId = privateParent;
        row.staffRoleIds = staff;row.forumTagIds = tags;row.messageChunks = chunks;
        return row;
    }

    public IncidentThreadStage stage() {
        if (privateStarterId == null) return IncidentThreadStage.PRIVATE_STARTER;
        if (privateThreadId == null) return IncidentThreadStage.PRIVATE_THREAD;
        if (privateMessages().size() < messageChunks - 1) return IncidentThreadStage.PRIVATE_BODY;
        if (publicStarterId == null) return IncidentThreadStage.PUBLIC_STARTER;
        if (publicThreadId == null) return IncidentThreadStage.PUBLIC_THREAD;
        if (publicMessages().size() < messageChunks - 1) return IncidentThreadStage.PUBLIC_BODY;
        return referenceMessageId == null ? IncidentThreadStage.PRIVATE_REFERENCE : IncidentThreadStage.COMPLETE;
    }

    public int chunkIndex() {
        return switch (stage()) {
            case PRIVATE_BODY -> privateMessages().size() + 1;
            case PUBLIC_BODY -> publicMessages().size() + 1;
            default -> 0;
        };
    }

    public void accept(IncidentThreadStage expected, int index, String messageId, String threadId) {
        if (!(expected != null && expected == stage() && expected != IncidentThreadStage.COMPLETE && index == chunkIndex())) throw unavailable();
        require(snowflake(messageId) && (threadId == null || snowflake(threadId)));
        switch (expected) {
            case PRIVATE_STARTER -> {privateStarterId = messageId;privateThreadId = threadId;}
            case PUBLIC_STARTER -> {require(threadId == null);publicStarterId = messageId;}
            case PRIVATE_THREAD -> {require(messageId.equals(privateStarterId) && messageId.equals(threadId));privateThreadId = threadId;}
            case PUBLIC_THREAD -> {require(messageId.equals(publicStarterId) && messageId.equals(threadId));publicThreadId = threadId;}
            case PRIVATE_BODY -> {require(threadId == null && !messageId.equals(privateStarterId) && !privateMessages().contains(messageId));privateMessageIds = append(privateMessageIds, messageId);}
            case PUBLIC_BODY -> {require(threadId == null && !messageId.equals(publicStarterId) && !publicMessages().contains(messageId));publicMessageIds = append(publicMessageIds, messageId);}
            case PRIVATE_REFERENCE -> {require(threadId == null && !messageId.equals(privateStarterId) && !privateMessages().contains(messageId));referenceMessageId = messageId;}
            default -> throw unavailable();
        }
    }

    public List<String> privateMessages() { return ids(privateMessageIds, 39); }
    public List<String> publicMessages() { return ids(publicMessageIds, 39); }
    public boolean publicComplete() { return publicThreadId != null && publicMessages().size() == messageChunks - 1; }

    public static List<String> ids(String value, int maximum) {
        if (value == null) throw unavailable();
        if (value.isEmpty()) return List.of();
        var ids = List.of(value.split(",", -1));
        require(ids.size() <= maximum && ids.stream().allMatch(IncidentThreadPublication::snowflake) && new HashSet<>(ids).size() == ids.size());
        return ids;
    }
    private static String append(String values, String id) { return values.isEmpty() ? id : values + "," + id; }
    private static boolean snowflake(String value) { return value != null && value.matches("[0-9]{1,20}"); }
    private static void require(boolean condition) { if (!condition) throw unavailable(); }
    private static BusinessRuleException unavailable() { return new BusinessRuleException("Incident thread publication changed or has invalid step evidence"); }
    @Override public String toString() { return "IncidentThreadPublication[private]"; }
}
