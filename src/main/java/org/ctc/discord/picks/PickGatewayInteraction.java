package org.ctc.discord.picks;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record PickGatewayInteraction(String id, String guildId, String channelId, String userId, Kind kind,
                                     String action, Map<String, List<String>> values, Instant createdAt,
                                     PickGatewayResponse response) {
    public PickGatewayInteraction {
        var copied = new HashMap<String, List<String>>();
        values.forEach((key, value) -> copied.put(key, List.copyOf(value)));
        values = Map.copyOf(copied);
    }
    @Override public String toString() { return "PickGatewayInteraction[id=" + id + ", kind=" + kind + "]"; }
    public enum Kind { COMMAND, BUTTON, SELECT, MODAL }
}
