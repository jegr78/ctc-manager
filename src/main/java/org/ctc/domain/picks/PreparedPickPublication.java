package org.ctc.domain.picks;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.ctc.domain.model.PickDisclosureField;

/** A rendered physical output. Delivery closes over its already frozen bytes, never live entities. */
public record PreparedPickPublication<T>(String channelId, String webhookId, String targetMessageId,
        String templateHash, String requestManifest, Map<UUID, Set<PickDisclosureField>> fields,
        Delivery delivery, Function<String, T> persistence) {
    public PreparedPickPublication {
        fields = fields.entrySet().stream().collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                entry -> Set.copyOf(entry.getValue())));
    }
    @FunctionalInterface public interface Delivery { String send() throws IOException; }
    @Override public String toString() { return "PreparedPickPublication[internal]"; }
}
