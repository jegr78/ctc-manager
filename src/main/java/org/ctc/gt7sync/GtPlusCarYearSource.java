package org.ctc.gt7sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.ctc.integration.OutboundPolicy;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GtPlusCarYearSource {
    private static final String URL = "https://gtplus.app/gt7/cars";
    private static final int MAX_LENGTH = 4 * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern FRAME = Pattern.compile("self\\.__next_f\\.push\\s*\\(");
    private final OutboundPolicy outboundPolicy;

    public Map<String, YearCandidate> fetchCandidates() throws IOException {
        outboundPolicy.requireEnabled();
        var response = Jsoup.connect(URL).timeout(10_000).maxBodySize(MAX_LENGTH)
                .followRedirects(false).execute();
        if (response.statusCode() != 200) throw new IOException("GTPlus catalog is unavailable");
        return parseCandidates(response.body());
    }

    public static Map<String, YearCandidate> parseCandidates(String html) throws IOException {
        if (html.length() >= MAX_LENGTH) throw new IOException("GTPlus data layout exceeds its size limit");
        var flight = new StringBuilder();
        int frames = 0;
        for (var script : Jsoup.parse(html).select("script")) {
            String text = script.data();
            var matcher = FRAME.matcher(text);
            while (matcher.find()) {
                if (++frames > 2048) throw new IOException("GTPlus data layout has too many frames");
                try (var parser = JSON.createParser(text.substring(matcher.end()))) {
                    JsonNode value = JSON.readTree(parser);
                    if (value != null && value.isArray() && value.size() == 2
                            && value.get(0).isIntegralNumber() && value.get(0).intValue() == 1
                            && value.get(1).isTextual()) {
                        flight.append(value.get(1).textValue());
                    }
                }
            }
        }
        if (flight.isEmpty()) throw new IOException("GTPlus data layout has no catalog frames");
        return parseFlight(flight.toString());
    }

    public static Map<String, YearCandidate> parseFlight(String payload) throws IOException {
        if (payload.length() >= MAX_LENGTH) throw new IOException("GTPlus data layout exceeds its size limit");
        var candidates = new LinkedHashMap<String, YearCandidate>();
        for (String line : payload.split("\n")) {
            int separator = line.indexOf(':');
            if (separator < 0) continue;
            String value = line.substring(separator + 1).stripLeading();
            if (value.startsWith("{") || value.startsWith("[")) {
                try (var parser = JSON.createParser(value)) {
                    collect(JSON.readTree(parser), candidates);
                }
            }
        }
        if (candidates.isEmpty()) throw new IOException("GTPlus data layout has no stock cars");
        return Map.copyOf(candidates);
    }

    private static void collect(JsonNode node, Map<String, YearCandidate> candidates) {
        if (node == null) return;
        if (node.isObject() && "car".equals(node.path("entity").asText()) && !isSwap(node)) {
            JsonNode code = node.path("code");
            if (code.isIntegralNumber() && code.canConvertToInt() && code.intValue() > 0) {
                String id = "car" + code.intValue();
                JsonNode raw = node.path("year");
                boolean unknown = raw.isNull() || raw.isMissingNode();
                boolean valid = raw.isIntegralNumber() && raw.canConvertToInt()
                        && raw.intValue() >= 1000 && raw.intValue() <= 9999;
                String evidence = URL + " code=" + code.intValue() + " year="
                        + (unknown ? "unknown" : raw.toString().substring(0, Math.min(80, raw.toString().length())));
                var candidate = new YearCandidate(valid ? raw.intValue() : null, evidence,
                        unknown || valid ? null : "GTPlus year must be a four-digit integer");
                candidates.merge(id, candidate, (a, b) ->
                        Objects.equals(a.year(), b.year()) && Objects.equals(a.problem(), b.problem()) ? a
                                : new YearCandidate(null, conflictEvidence(a.evidence(), b.evidence()), "Conflicting GTPlus stock records for " + id));
            }
        }
        if (node.isContainerNode()) node.forEach(child -> collect(child, candidates));
    }

    private static String conflictEvidence(String first, String second) {
        String combined = first + "; " + second;
        return combined.substring(0, Math.min(1000, combined.length()));
    }

    private static boolean isSwap(JsonNode node) {
        JsonNode id = node.path("engineSwapId");
        return node.path("id").asText().contains("-engine-swap-")
                || (!id.isMissingNode() && !id.isNull() && !"$undefined".equals(id.asText()) && !id.asText().isBlank());
    }

    public record YearCandidate(Integer year, String evidence, String problem) {}
}
