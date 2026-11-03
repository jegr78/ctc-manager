package org.ctc.domain.incidents;

import java.util.UUID;
import org.ctc.domain.model.IncidentParty;

public record AuthorizedIncidentActor(UUID contextId, String guildId, String channelId, String userId, IncidentParty party) {
    @Override public String toString() { return "AuthorizedIncidentActor[redacted]"; }
}
