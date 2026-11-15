package org.ctc.discord.picks;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One Discord interaction. Commands and autocomplete carry their option values in {@code values}, the chosen
 * subcommand in {@code subcommand} and, for autocomplete, the option being typed in {@code focused}.
 */
public record PickGatewayInteraction(String id, String guildId, String channelId, String userId, Kind kind,
                                     String action, Map<String, List<String>> values, Instant createdAt,
                                     PickGatewayResponse response, String subcommand, String focused, String userName) {
    public PickGatewayInteraction(String id, String guildId, String channelId, String userId, Kind kind,
                                  String action, Map<String, List<String>> values, Instant createdAt, PickGatewayResponse response) {
        this(id, guildId, channelId, userId, kind, action, values, createdAt, response, null, null, null);
    }
    public PickGatewayInteraction {
        var copied = new HashMap<String, List<String>>();
        values.forEach((key, value) -> copied.put(key, List.copyOf(value)));
        values = Map.copyOf(copied);
    }
    @Override public String toString() { return "PickGatewayInteraction[id=" + id + ", kind=" + kind + "]"; }
    /** The single value of an option, or null when it is absent or blank. */
    public String option(String name) {
        var value = values.get(name);
        return value == null || value.isEmpty() || value.getFirst() == null || value.getFirst().isBlank() ? null : value.getFirst();
    }
    public enum Kind { COMMAND, BUTTON, SELECT, MODAL, AUTOCOMPLETE }
}
