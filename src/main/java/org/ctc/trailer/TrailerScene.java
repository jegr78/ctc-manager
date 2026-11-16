package org.ctc.trailer;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** One scene of the trailer: its template name, position, length, layer names and template variables. */
public record TrailerScene(String name, int index, Duration length, List<String> layers, Map<String, Object> variables) {
}
