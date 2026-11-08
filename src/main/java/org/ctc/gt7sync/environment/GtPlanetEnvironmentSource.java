package org.ctc.gt7sync.environment;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.TrackEnvironmentValues;
import org.ctc.domain.model.TrackRainCapability;
import org.ctc.domain.model.WeatherPreset;
import org.ctc.integration.OutboundPolicy;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class GtPlanetEnvironmentSource {
    public static final String URL = org.ctc.domain.model.TrackEnvironmentProvenance.SOURCE_URL;
    private static final int MAX = 1_048_576;
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final OutboundPolicy outbound;
    private final Clock clock;

    public Snapshot fetch() throws IOException {
        outbound.requireEnabled();
        var response = Jsoup.connect(URL).timeout(10_000).maxBodySize(MAX).followRedirects(false).execute();
        if (response.statusCode() != 200) throw new IOException("GTPlanet reference is unavailable");
        var snapshot = parse(response.body(), clock.instant());
        if (snapshot.profiles().size() < 40) throw new IOException("GTPlanet reference is unexpectedly incomplete; saved values are unchanged");
        return snapshot;
    }
    public static Snapshot bundled() throws IOException {
        try (var input = new ClassPathResource("gt7/track-environments-gtplanet.json").getInputStream()) {
            byte[] bytes = input.readNBytes(MAX);
            if (bytes.length >= MAX) throw new IOException("Bundled layout reference exceeds its size limit");
            return JSON.readValue(bytes, Snapshot.class);
        }
    }
    public static Snapshot parse(String html, Instant retrieved) throws IOException {
        if (html == null || html.length() >= MAX) throw new IOException("GTPlanet reference exceeds its size limit");
        try {
            var post = Jsoup.parse(html).selectFirst("article[data-content=post-14046048]");
            if (post == null) throw new IllegalArgumentException("Missing reference post");
            var edited = post.selectFirst(".message-lastEdit time");
            if (edited == null) throw new IllegalArgumentException("Missing source revision");
            String revision = OffsetDateTime.parse(edited.attr("datetime"), DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")).toInstant().toString();
            var profiles = new ArrayList<Profile>();
            var spoilers=post.select(".bbCodeSpoiler");
            if (spoilers.size()>160) throw new IllegalArgumentException("Too many source profiles");
            for (var spoiler : spoilers) {
                var lineage = new ArrayList<Element>(); lineage.add(spoiler);
                int depth=0;
                for (Element parent=spoiler.parent(); parent!=null; parent=parent.parent()) {
                    if (++depth>128) throw new IllegalArgumentException("Source nesting exceeds its limit");
                    if (parent.hasClass("bbCodeSpoiler")) lineage.add(parent);
                }
                if (lineage.size()>6) throw new IllegalArgumentException("Source profile nesting exceeds its limit");
                Collections.reverse(lineage);
                String key = String.join(" | ", lineage.stream().map(p -> {
                    var title = p.selectFirst(".bbCodeSpoiler-button-title");
                    if (title == null) throw new IllegalArgumentException("Missing profile title");
                    return title.text();
                }).toList());
                var content = spoiler.selectFirst(".bbCodeBlock-content");
                if (content == null) throw new IllegalArgumentException("Missing spoiler content");
                var sections = new LinkedHashMap<String,List<String>>();
                String section = "Layouts"; sections.put(section,new ArrayList<>());
                for (var child : content.children()) {
                    if (Set.of("b","strong").contains(child.normalName())) {
                        section = child.text().replace(":", "").strip(); sections.computeIfAbsent(section,k -> new ArrayList<>());
                    } else if (child.normalName().equals("ul")) {
                        for (var item : child.children()) {
                            if (!item.normalName().equals("li") || !item.select("ul,ol").isEmpty()) throw new IllegalArgumentException("Unexpected nested source list");
                            sections.get(section).add(item.text());
                        }
                    }
                }
                if (!sections.containsKey("Time of Day") && !sections.containsKey("Weather")) continue;
                var weather = sections.getOrDefault("Weather",List.of()).stream().map(text -> {
                    if (!text.matches("[SCR][0-9]{2} \\| .{1,250}")) throw new IllegalArgumentException("Invalid source weather entry");
                    return text.substring(0,3);
                }).toList();
                profiles.add(new Profile(key,sections.get("Layouts"),sections.getOrDefault("Time of Day",List.of()),weather));
            }
            return new Snapshot(revision,retrieved.toString(),hash(revision,profiles),profiles);
        } catch (IllegalArgumentException | org.ctc.domain.exception.BusinessRuleException invalid) {
            throw new IOException("GTPlanet reference has invalid or incomplete layout data",invalid);
        }
    }
    public static String hash(String edited, List<Profile> profiles) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(List.of(edited,profiles))));
        } catch (IOException | NoSuchAlgorithmException impossible) { throw new IllegalStateException("Cannot fingerprint layout reference",impossible); }
    }
    public record Snapshot(String sourceEditedAt,String retrievedAt,String sourceHash,List<Profile> profiles) {
        public Snapshot {
            Instant.parse(sourceEditedAt); Instant.parse(retrievedAt); profiles=List.copyOf(profiles);
            if (profiles.isEmpty() || profiles.size()>128 || profiles.stream().map(Profile::key).distinct().count()!=profiles.size()
                    || !hash(sourceEditedAt,profiles).equals(sourceHash)) throw new IllegalArgumentException("Invalid layout source snapshot");
        }
    }
    public record Profile(String key,List<String> layouts,List<String> timeOfDay,List<String> weatherCodes) {
        public Profile {
            if (key==null || key.isBlank() || !key.equals(key.strip()) || key.length()>300 || key.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid source profile key");
            layouts=List.copyOf(layouts); timeOfDay=List.copyOf(timeOfDay); weatherCodes=List.copyOf(weatherCodes);
            if (layouts.size()>50 || layouts.stream().anyMatch(l -> l.isBlank() || l.length()>100 || l.chars().anyMatch(Character::isISOControl)) || timeOfDay.isEmpty()) throw new IllegalArgumentException("Invalid source layout scope");
            var dry=WeatherPreset.available(TrackRainCapability.DRY_ONLY).stream().map(Enum::name).toList();
            var rain=WeatherPreset.available(TrackRainCapability.RAIN).stream().map(Enum::name).toList();
            if (!weatherCodes.equals(dry) && !weatherCodes.equals(rain)) throw new IllegalArgumentException("Source weather catalog is incomplete or unknown");
            new TrackEnvironmentValues(timeOfDay,weatherCodes.size()==32?TrackRainCapability.RAIN:TrackRainCapability.DRY_ONLY);
        }
        public TrackEnvironmentValues values() { return new TrackEnvironmentValues(timeOfDay,weatherCodes.size()==32?TrackRainCapability.RAIN:TrackRainCapability.DRY_ONLY); }
    }
}
