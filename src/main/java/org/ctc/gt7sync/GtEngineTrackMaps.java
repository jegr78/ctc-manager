package org.ctc.gt7sync;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.springframework.core.io.ClassPathResource;

/**
 * The reviewed GT-Engine layout list bundled as {@code gt7/track-maps-gtengine.json}: every layout slug with its title,
 * and an explicit mapping from catalog track names to slugs. Download URLs are built from reviewed slugs only.
 */
public record GtEngineTrackMaps(String source, String imageBase, String gameVersion, String retrievedOn, List<Layout> layouts,
                                List<Mapping> mappings, List<String> unmappedLayouts, List<String> unmappedTracks) {
    public static final String PAGE_URL = "https://gt-engine.com/gt7/tracks/track-maps.html";
    public static final String IMAGE_BASE = "https://gt-engine.com/gt7/tracks/images/md/";
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern IMAGE_HREF = Pattern.compile("images/md/(" + SLUG.pattern() + ")\\.png");
    private static final Pattern VERSION = Pattern.compile("\\(Ver\\.\\s*([0-9]+(?:\\.[0-9]+)*)\\)");
    private static final int MAX_LAYOUTS = 500;
    private static final int MAX_SLUG = 80;
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static volatile GtEngineTrackMaps bundled;

    public GtEngineTrackMaps {
        layouts = List.copyOf(layouts);
        mappings = List.copyOf(mappings);
        unmappedLayouts = List.copyOf(unmappedLayouts);
        unmappedTracks = List.copyOf(unmappedTracks);
        if (!PAGE_URL.equals(source) || !IMAGE_BASE.equals(imageBase)) throw new IllegalArgumentException("Unexpected GT-Engine source");
        if (gameVersion == null || gameVersion.isBlank()) throw new IllegalArgumentException("Missing GT-Engine game version");
        var slugs = new HashMap<String, Layout>();
        for (var layout : layouts) {
            if (!validSlug(layout.slug()) || layout.title() == null || layout.title().isBlank() || slugs.putIfAbsent(layout.slug(), layout) != null) {
                throw new IllegalArgumentException("Invalid or duplicate GT-Engine layout " + layout.slug());
            }
        }
        var names = new HashSet<String>();
        var mapped = new HashSet<String>();
        for (var mapping : mappings) {
            String name = mapping.trackName() == null ? "" : mapping.trackName().strip();
            if (name.isEmpty() || !names.add(name) || !slugs.containsKey(mapping.slug())) {
                throw new IllegalArgumentException("Invalid or duplicate GT-Engine mapping for " + mapping.trackName());
            }
            mapped.add(mapping.slug());
        }
        var unused = new TreeSet<>(slugs.keySet());
        unused.removeAll(mapped);
        if (!unused.equals(new TreeSet<>(unmappedLayouts))) throw new IllegalArgumentException("Unmapped GT-Engine layouts are not listed exactly: " + unused);
        for (var track : unmappedTracks) {
            if (track == null || track.isBlank() || names.contains(track.strip())) throw new IllegalArgumentException("Invalid unmapped track " + track);
        }
    }

    /** The reviewed list shipped with the application. */
    public static GtEngineTrackMaps bundled() {
        var current = bundled;
        if (current != null) return current;
        try (var input = new ClassPathResource("gt7/track-maps-gtengine.json").getInputStream()) {
            current = JSON.readValue(input, GtEngineTrackMaps.class);
        } catch (IOException failure) {
            throw new UncheckedIOException("The bundled GT-Engine layout list is not readable", failure);
        }
        bundled = current;
        return current;
    }

    public Optional<Layout> layoutFor(String trackName) {
        if (trackName == null) return Optional.empty();
        String name = trackName.strip();
        var slug = mappings.stream().filter(mapping -> mapping.trackName().strip().equals(name)).map(Mapping::slug).findFirst();
        return slug.flatMap(this::layout);
    }

    public Optional<Layout> layout(String slug) {
        return layouts.stream().filter(layout -> layout.slug().equals(slug)).findFirst();
    }

    public String imageUrl(Layout layout) {
        if (!validSlug(layout.slug()) || layout(layout.slug()).isEmpty()) throw new IllegalArgumentException("Unknown GT-Engine layout");
        return IMAGE_BASE + layout.slug() + ".png";
    }

    /** Reads the layouts and game version from the GT-Engine track map page, in page order. */
    public static Page parsePage(String html) {
        var document = Jsoup.parse(html == null ? "" : html);
        var version = VERSION.matcher(document.text());
        var layouts = new ArrayList<Layout>();
        var seen = new LinkedHashSet<String>();
        for (var anchor : document.select("a[data-lightbox=track_maps][href]")) {
            var href = IMAGE_HREF.matcher(anchor.attr("href"));
            if (!href.matches()) throw new IllegalArgumentException("Unexpected GT-Engine map link " + anchor.attr("href"));
            String slug = href.group(1);
            if (slug.length() > MAX_SLUG) throw new IllegalArgumentException("GT-Engine layout slug is too long");
            if (seen.add(slug)) layouts.add(new Layout(slug, anchor.attr("data-title").strip()));
            if (layouts.size() > MAX_LAYOUTS) throw new IllegalArgumentException("GT-Engine lists more layouts than expected");
        }
        if (layouts.isEmpty()) throw new IllegalArgumentException("The GT-Engine page lists no track maps");
        return new Page(version.find() ? version.group(1) : null, List.copyOf(layouts));
    }

    /** Differences between the live page and this reviewed list; no data changes. */
    public Comparison compare(Page page) {
        var reviewed = new TreeSet<String>();
        layouts.forEach(layout -> reviewed.add(layout.slug()));
        var live = new TreeSet<String>();
        page.layouts().forEach(layout -> live.add(layout.slug()));
        var added = new TreeSet<>(live);
        added.removeAll(reviewed);
        var removed = new TreeSet<>(reviewed);
        removed.removeAll(live);
        return new Comparison(gameVersion, page.gameVersion(), List.copyOf(added), List.copyOf(removed));
    }

    private static boolean validSlug(String slug) {
        return slug != null && slug.length() <= MAX_SLUG && SLUG.matcher(slug).matches();
    }

    public record Layout(String slug, String title) { }

    public record Mapping(String trackName, String slug) { }

    public record Page(String gameVersion, List<Layout> layouts) { }

    public record Comparison(String reviewedVersion, String liveVersion, List<String> added, List<String> removed) {
        public boolean unchanged() {
            return added.isEmpty() && removed.isEmpty() && reviewedVersion.equals(liveVersion);
        }

        public String message() {
            if (unchanged()) return "GT-Engine still lists the reviewed " + reviewedVersion + " layouts.";
            return "GT-Engine changed: game version " + (liveVersion == null ? "unknown" : liveVersion) + " (reviewed " + reviewedVersion
                    + "); new layouts " + list(added) + "; removed layouts " + list(removed) + ". Update gt7/track-maps-gtengine.json after review.";
        }

        private static String list(List<String> slugs) { return slugs.isEmpty() ? "none" : String.join(", ", slugs); }
    }
}
