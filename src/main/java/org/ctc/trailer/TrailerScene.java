package org.ctc.trailer;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One scene of the trailer: its template name, position, length, layer names and template variables. */
public record TrailerScene(String name, int index, Duration length, List<String> layers, Map<String, Object> variables) {

    /** Variables may hold null values for missing optional images, so they are copied without {@code Map.copyOf}. */
    public TrailerScene {
        layers = List.copyOf(layers);
        variables = Collections.unmodifiableMap(new LinkedHashMap<>(variables));
    }
}
