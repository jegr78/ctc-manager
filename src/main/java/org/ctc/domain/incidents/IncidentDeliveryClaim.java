package org.ctc.domain.incidents;

import java.util.UUID;
import org.ctc.domain.model.IncidentDeliveryOperation;

public record IncidentDeliveryClaim(UUID id, UUID windowId, UUID token, long version, String generation,
                                    IncidentDeliveryOperation operation, String guildId, String channelId, String nonce) {
    @Override public String toString() { return "IncidentDeliveryClaim[opaque]"; }
}
