package org.ctc.domain.picks;

import java.util.Set;
import java.util.UUID;

public record ChannelPickActor(String guildId, String channelId, String userId, Set<Binding> bindings) {
    public ChannelPickActor { bindings = Set.copyOf(bindings); }
    public record Binding(UUID seasonId, UUID teamId) { }
}
