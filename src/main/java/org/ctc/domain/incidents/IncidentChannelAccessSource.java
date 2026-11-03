package org.ctc.domain.incidents;

import java.util.Set;

@FunctionalInterface
public interface IncidentChannelAccessSource {
    Set<String> currentRoles(String guildId, String channelId, String userId);
}
