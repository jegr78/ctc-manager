package de.ctc.gt7sync;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;

@Slf4j
@Service
public class Gt7ScraperService {

    private static final String BASE_URL = "https://www.gran-turismo.com";
    private static final String CARS_PAGE = BASE_URL + "/gb/gt7/carlist/";
    private static final String TRACKS_PAGE = BASE_URL + "/gb/gt7/tracklist/";
    static final String CAR_IMAGE_BASE = BASE_URL + "/common/dist/gt7/carlist/car_thumbnails/";

    public record ScrapedCar(String gt7Id, String manufacturer, String name, String imageUrl) {}
    public record ScrapedTrack(String id, String name, String country, String baseId, String imageUrl) {}

    public List<ScrapedCar> scrapeCars() throws IOException {
        // Step 1: Find main bundle URL from HTML page
        String html = Jsoup.connect(CARS_PAGE).get().html();
        String indexJsPath = extractScriptSrc(html, "/common/dist/gt7/carlist/assets/index-");

        // Step 2: Find chunk filenames from index JS
        String indexJs = fetchText(BASE_URL + indexJsPath);
        String carsChunkName = extractPattern(indexJs, "\"\\.\\/?(cars\\.gb-[^\"]+\\.js)\"");
        String tunersChunkName = extractPattern(indexJs, "\"\\.\\/?(tuners\\.gb-[^\"]+\\.js)\"");

        // Step 3: Fetch manufacturer lookup table (tuners = manufacturers in GT7)
        String tunersJs = fetchText(BASE_URL + "/common/dist/gt7/carlist/assets/" + tunersChunkName);
        Map<String, String> manufacturerMap = parseTunersJs(tunersJs);

        // Step 4: Fetch and parse cars data
        String carsJs = fetchText(BASE_URL + "/common/dist/gt7/carlist/assets/" + carsChunkName);
        return parseCarsJs(carsJs, manufacturerMap);
    }

    public List<ScrapedTrack> scrapeTracks() throws IOException {
        String html = Jsoup.connect(TRACKS_PAGE).get().html();
        String indexJsPath = extractScriptSrc(html, "/common/dist/gt7/tracklist/assets/index-");

        String indexJs = fetchText(BASE_URL + indexJsPath);
        String tracksChunkName = extractPattern(indexJs, "\"\\.\\/?(tracks\\.gb-[^\"]+\\.js)\"");

        // Build baseId → image chunk filename mapping from index JS
        Map<String, String> imageChunkMap = extractBaseIdChunks(indexJs);

        String tracksJs = fetchText(BASE_URL + "/common/dist/gt7/tracklist/assets/" + tracksChunkName);
        var tracks = parseTracksJs(tracksJs);

        // Resolve image URLs by fetching each baseId's chunk
        return resolveTrackImages(tracks, imageChunkMap);
    }

    /**
     * Extract baseId → chunk filename mapping from the tracklist index JS.
     * Pattern: "./baseId-hash.js" where baseId is a hex string (not "tracks", "localize", etc.)
     */
    Map<String, String> extractBaseIdChunks(String indexJs) {
        var map = new HashMap<String, String>();
        Pattern p = Pattern.compile("\"\\./([a-f0-9]{6})-([^\"]+\\.js)\"");
        Matcher m = p.matcher(indexJs);
        while (m.find()) {
            map.put(m.group(1), m.group(1) + "-" + m.group(2));
        }
        return map;
    }

    /**
     * Resolve track image URLs by fetching individual baseId JS chunks.
     * Each chunk exports a PNG path like: const t="/common/dist/gt7/tracklist/assets/xxx.png";
     */
    private List<ScrapedTrack> resolveTrackImages(List<ScrapedTrack> tracks, Map<String, String> imageChunkMap) {
        var result = new ArrayList<ScrapedTrack>();
        for (var track : tracks) {
            String imageUrl = null;
            if (track.baseId() != null && imageChunkMap.containsKey(track.baseId())) {
                try {
                    String chunkJs = fetchText(BASE_URL + "/common/dist/gt7/tracklist/assets/" + imageChunkMap.get(track.baseId()));
                    Matcher m = Pattern.compile("\"(/common/dist/gt7/tracklist/assets/[^\"]+\\.png)\"").matcher(chunkJs);
                    if (m.find()) {
                        imageUrl = BASE_URL + m.group(1);
                    }
                } catch (Exception e) {
                    log.warn("Failed to resolve image for track {}: {}", track.name(), e.getMessage());
                }
            }
            result.add(new ScrapedTrack(track.id(), track.name(), track.country(), track.baseId(), imageUrl));
        }
        return result;
    }

    /**
     * Parse manufacturer lookup table from GT7 tuners JavaScript chunk.
     * Format: const n={tnr10:{id:"tnr10",name:"Daihatsu"},tnr13:{id:"tnr13",name:"Ford"},...};
     */
    public Map<String, String> parseTunersJs(String tunersJs) {
        var map = new HashMap<String, String>();
        Pattern p = Pattern.compile("(tnr\\d+):\\{[^}]*name:\"([^\"]*)\"");
        Matcher m = p.matcher(tunersJs);
        while (m.find()) {
            map.put(m.group(1), m.group(2).trim());
        }
        log.info("Parsed {} manufacturers from GT7 tuners data", map.size());
        return map;
    }

    /**
     * Parse car data from the GT7 JavaScript chunk.
     * Uses manufacturer lookup table for reliable manufacturer names.
     */
    public List<ScrapedCar> parseCarsJs(String carsJs, Map<String, String> manufacturerMap) {
        var cars = new ArrayList<ScrapedCar>();

        // Match individual car blocks delimited by car ID keys.
        // Fields are NOT adjacent — there are many fields between id, nameLong, nameShort.
        Pattern blockPattern = Pattern.compile("(car\\d+):\\{[^}]*\\}");
        Matcher blockMatcher = blockPattern.matcher(carsJs);

        while (blockMatcher.find()) {
            String block = blockMatcher.group();
            String gt7Id = extractField(block, "id");
            String nameLong = extractField(block, "nameLong");
            String nameShort = extractField(block, "nameShort");
            String manufacturerId = extractField(block, "manufacturerId");

            if (gt7Id == null || nameLong == null || nameShort == null) continue;

            // Primary: look up manufacturer by ID from tuners table
            String manufacturer = manufacturerId != null ? manufacturerMap.get(manufacturerId) : null;
            // Fallback: extract from nameLong minus nameShort
            if (manufacturer == null || manufacturer.isBlank()) {
                manufacturer = extractManufacturer(nameLong, nameShort);
            }

            String imageUrl = CAR_IMAGE_BASE + gt7Id + ".png";
            cars.add(new ScrapedCar(gt7Id, manufacturer, nameShort, imageUrl));
        }

        log.info("Parsed {} cars from GT7 data", cars.size());
        return cars;
    }

    /** Convenience overload for testing without manufacturer map — uses name extraction fallback. */
    public List<ScrapedCar> parseCarsJs(String carsJs) {
        return parseCarsJs(carsJs, Map.of());
    }

    /**
     * Parse track data from the GT7 JavaScript chunk.
     * Format: const e={"02e205":{baseId:"...",id:"02e205",nameLong:"...",countryName:"...",...},...};
     */
    public List<ScrapedTrack> parseTracksJs(String tracksJs) {
        var tracks = new ArrayList<ScrapedTrack>();

        // Match track objects — fields may appear in varying order within each block
        // We look for blocks delimited by { and } that contain the required fields
        Pattern blockPattern = Pattern.compile("\\{[^{}]*?id:\"([^\"]+)\"[^{}]*?\\}");
        Matcher blockMatcher = blockPattern.matcher(tracksJs);

        while (blockMatcher.find()) {
            String block = blockMatcher.group();
            String id = extractField(block, "id");
            String nameLong = extractField(block, "nameLong");
            String countryName = extractField(block, "countryName");
            String baseId = extractField(block, "baseId");

            if (id != null && nameLong != null && countryName != null) {
                tracks.add(new ScrapedTrack(id, nameLong, countryName, baseId, null));
            }
        }

        log.info("Parsed {} tracks from GT7 data", tracks.size());
        return tracks;
    }

    /**
     * Extract manufacturer from nameLong by removing nameShort.
     * E.g. "Nissan Skyline GTS-R (R31) '87" minus "Skyline GTS-R (R31) '87" = "Nissan"
     * Special case: if nameLong == nameShort, use first word of nameLong.
     */
    static String extractManufacturer(String nameLong, String nameShort) {
        if (nameLong.equals(nameShort)) {
            // Take first word as manufacturer
            int space = nameLong.indexOf(' ');
            return space > 0 ? nameLong.substring(0, space) : nameLong;
        }
        String manufacturer = nameLong.replace(nameShort, "").trim();
        if (manufacturer.isEmpty()) {
            int space = nameLong.indexOf(' ');
            return space > 0 ? nameLong.substring(0, space) : nameLong;
        }
        return manufacturer;
    }

    private String extractField(String block, String fieldName) {
        Pattern p = Pattern.compile(fieldName + ":\"([^\"]*)\"");
        Matcher m = p.matcher(block);
        return m.find() ? m.group(1) : null;
    }

    String extractScriptSrc(String html, String pathPrefix) {
        Pattern p = Pattern.compile("src=\"(" + Pattern.quote(pathPrefix) + "[^\"]+)\"");
        Matcher m = p.matcher(html);
        if (!m.find()) {
            throw new IllegalStateException("Could not find script with prefix: " + pathPrefix);
        }
        return m.group(1);
    }

    String extractPattern(String text, String regex) {
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(text);
        if (!m.find()) {
            throw new IllegalStateException("Could not find pattern: " + regex);
        }
        return m.group(1);
    }

    private String fetchText(String url) throws IOException {
        return Jsoup.connect(url).ignoreContentType(true).execute().body();
    }
}
