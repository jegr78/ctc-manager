package org.ctc.domain.incidents;

import java.util.List;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.ctc.domain.model.IncidentThreadStage;

public record IncidentThreadClaim(UUID id, UUID windowId, UUID token, long version, String generation, Instant validUntil,
                                  Destination destination, Work work) {
    public record Destination(String guildId, String matchChannelId, String stewardsChannelId,
                              List<String> staffRoles, List<String> forumTags, Set<String> participantRoles) {
        public Destination {staffRoles=List.copyOf(staffRoles);forumTags=List.copyOf(forumTags);participantRoles=Set.copyOf(participantRoles);}
        @Override public String toString() {return "IncidentThreadDestination[private]";}
    }
    public record Work(IncidentThreadStage stage, int chunkIndex, String caseReference,String marker,String title, String content,
                       String starterContent, String nonce, String starterId, String threadId) {
        @Override public String toString() {return "IncidentThreadWork[private]";}
    }
    @Override public String toString() {return "IncidentThreadClaim[private]";}
}
