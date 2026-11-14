package org.ctc.domain.picks;

import java.util.UUID;

public record AuthorizedPickActor(UUID raceId, UUID seasonId, UUID teamId, String userId, ChannelContext channel) {
    public AuthorizedPickActor(UUID raceId, UUID seasonId, UUID teamId, String userId) {
        this(raceId, seasonId, teamId, userId, null);
    }
    public record ChannelContext(String guildId, String channelId, long generation, UUID bindingTeamId) { }
}
