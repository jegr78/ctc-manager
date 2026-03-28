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
    public record ScrapedTrack(String id, String name, String country, String baseId) {}

    public List<ScrapedCar> scrapeCars() throws IOException {
        // Step 1: Find main bundle URL from HTML page
        String html = Jsoup.connect(CARS_PAGE).get().html();
        String indexJsPath = extractScriptSrc(html, "/common/dist/gt7/carlist/assets/index-");

        // Step 2: Find cars.gb chunk filename from index JS
        String indexJs = fetchText(BASE_URL + indexJsPath);
        String carsChunkName = extractPattern(indexJs, "\"\\.\\/?(cars\\.gb-[^\"]+\\.js)\"");

        // Step 3: Fetch and parse cars data
        String carsJs = fetchText(BASE_URL + "/common/dist/gt7/carlist/assets/" + carsChunkName);
        return parseCarsJs(carsJs);
    }

    public List<ScrapedTrack> scrapeTracks() throws IOException {
        String html = Jsoup.connect(TRACKS_PAGE).get().html();
        String indexJsPath = extractScriptSrc(html, "/common/dist/gt7/tracklist/assets/index-");

        String indexJs = fetchText(BASE_URL + indexJsPath);
        String tracksChunkName = extractPattern(indexJs, "\"\\.\\/?(tracks\\.gb-[^\"]+\\.js)\"");

        String tracksJs = fetchText(BASE_URL + "/common/dist/gt7/tracklist/assets/" + tracksChunkName);
        return parseTracksJs(tracksJs);
    }

    /**
     * Parse car data from the GT7 JavaScript chunk.
     * Format: const r={car102:{id:"car102",nameLong:"...",nameShort:"...",...},...};
     */
    public List<ScrapedCar> parseCarsJs(String carsJs) {
        var cars = new ArrayList<ScrapedCar>();

        // Match individual car objects: id:"carXXX" ... nameLong:"..." ... nameShort:"..."
        // The JS is minified, so fields appear in order within each car block.
        Pattern carPattern = Pattern.compile(
                "id:\"(car\\d+)\",nameLong:\"([^\"]*)\",nameShort:\"([^\"]*)\"");
        Matcher m = carPattern.matcher(carsJs);

        while (m.find()) {
            String gt7Id = m.group(1);
            String nameLong = m.group(2);
            String nameShort = m.group(3);

            String manufacturer = extractManufacturer(nameLong, nameShort);
            String imageUrl = CAR_IMAGE_BASE + gt7Id + ".png";

            cars.add(new ScrapedCar(gt7Id, manufacturer, nameShort, imageUrl));
        }

        log.info("Parsed {} cars from GT7 data", cars.size());
        return cars;
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
                tracks.add(new ScrapedTrack(id, nameLong, countryName, baseId));
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
