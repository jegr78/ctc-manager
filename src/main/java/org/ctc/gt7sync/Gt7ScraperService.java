package org.ctc.gt7sync;

import static org.springframework.util.StringUtils.hasText;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class Gt7ScraperService {

	private static final String BASE_URL = "https://www.gran-turismo.com";
	static final String CAR_IMAGE_BASE = BASE_URL + "/common/dist/gt7/carlist/car_thumbnails/";
	private static final String CARS_PAGE = BASE_URL + "/gb/gt7/carlist/";
	private static final String TRACKS_PAGE = BASE_URL + "/gb/gt7/tracklist/";

	static String extractManufacturer(String nameLong, String nameShort) {
		if (nameLong.equals(nameShort)) {
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

	public List<ScrapedCar> scrapeCars() throws IOException {
		String html = Jsoup.connect(CARS_PAGE).get().html();
		String indexJsPath = extractScriptSrc(html, "/common/dist/gt7/carlist/assets/index-");

		String indexJs = fetchText(BASE_URL + indexJsPath);
		String carsChunkName = extractChunkFilename(indexJs, "cars.gb");
		String tunersChunkName = extractChunkFilename(indexJs, "tuners.gb");

		String tunersJs = fetchText(BASE_URL + "/common/dist/gt7/carlist/assets/" + tunersChunkName);
		Map<String, String> manufacturerMap = parseTunersJs(tunersJs);

		String carsJs = fetchText(BASE_URL + "/common/dist/gt7/carlist/assets/" + carsChunkName);
		return parseCarsJs(carsJs, manufacturerMap);
	}

	public List<ScrapedTrack> scrapeTracks() throws IOException {
		return scrapeTracks(false);
	}

	public List<ScrapedTrack> scrapeTracks(boolean resolveImages) throws IOException {
		String html = Jsoup.connect(TRACKS_PAGE).get().html();
		String indexJsPath = extractScriptSrc(html, "/common/dist/gt7/tracklist/assets/index-");

		String indexJs = fetchText(BASE_URL + indexJsPath);
		String tracksChunkName = extractChunkFilename(indexJs, "tracks.gb");

		String tracksJs = fetchText(BASE_URL + "/common/dist/gt7/tracklist/assets/" + tracksChunkName);
		var tracks = parseTracksJs(tracksJs);

		if (resolveImages) {
			Map<String, String> imageChunkMap = extractBaseIdChunks(indexJs);
			return resolveTrackImagesParallel(tracks, imageChunkMap);
		}
		return tracks;
	}

	Map<String, String> extractBaseIdChunks(String indexJs) {
		var map = new HashMap<String, String>();
		Pattern p = Pattern.compile("\"\\./([a-f0-9]{6})-([^\"]+\\.js)\"");
		Matcher m = p.matcher(indexJs);
		while (m.find()) {
			map.put(m.group(1), m.group(1) + "-" + m.group(2));
		}
		return map;
	}

	private List<ScrapedTrack> resolveTrackImagesParallel(List<ScrapedTrack> tracks, Map<String, String> imageChunkMap) {
		var baseIdsToResolve = tracks.stream()
				.map(ScrapedTrack::baseId)
				.filter(Objects::nonNull)
				.filter(imageChunkMap::containsKey)
				.distinct()
				.toList();

		var imageUrlMap = new ConcurrentHashMap<String, String>();
		var futures = baseIdsToResolve.stream().map(baseId ->
				CompletableFuture.runAsync(() -> {
					try {
						String chunkJs = fetchText(BASE_URL + "/common/dist/gt7/tracklist/assets/" + imageChunkMap.get(baseId));
						Matcher m = Pattern.compile("\"(/common/dist/gt7/tracklist/assets/[^\"]+\\.png)\"").matcher(chunkJs);
						if (m.find()) {
							imageUrlMap.put(baseId, BASE_URL + m.group(1));
						}
					} catch (IOException e) {
						log.warn("Failed to resolve image for baseId {}: {}", baseId, e.getMessage());
					}
				})
		).toArray(CompletableFuture[]::new);

		CompletableFuture.allOf(futures).join();
		log.info("Resolved {} track images in parallel", imageUrlMap.size());

		return tracks.stream()
				.map(t -> new ScrapedTrack(t.id(), t.name(), t.country(), t.baseId(), imageUrlMap.get(t.baseId())))
				.toList();
	}

	public Map<String, String> parseTunersJs(String tunersJs) {
		var map = new HashMap<String, String>();
        Pattern p = Pattern.compile("(tnr\\d+):\\{([^}]*)\\}");
        Matcher m = p.matcher(tunersJs);
        while (m.find()) {
            String name = extractField(m.group(2), "name");
            if (name != null) map.put(m.group(1), name.trim());
        }
		log.info("Parsed {} manufacturers from GT7 tuners data", map.size());
		return map;
	}

	public List<ScrapedCar> parseCarsJs(String carsJs, Map<String, String> manufacturerMap) {
		var cars = new ArrayList<ScrapedCar>();

		Pattern blockPattern = Pattern.compile("(car\\d+):\\{[^}]*\\}");
		Matcher blockMatcher = blockPattern.matcher(carsJs);

		while (blockMatcher.find()) {
			String block = blockMatcher.group();
			String gt7Id = extractField(block, "id");
			String nameLong = extractField(block, "nameLong");
			String nameShort = extractField(block, "nameShort");
			String manufacturerId = extractField(block, "manufacturerId");

			if (gt7Id == null || nameLong == null || nameShort == null) {
				continue;
			}

			String manufacturer = manufacturerId != null ? manufacturerMap.get(manufacturerId) : null;
			if (!hasText(manufacturer)) {
				manufacturer = extractManufacturer(nameLong, nameShort);
			}

			String imageUrl = CAR_IMAGE_BASE + gt7Id + ".png";
			cars.add(new ScrapedCar(gt7Id, manufacturer, nameShort, imageUrl));
		}

		log.info("Parsed {} cars from GT7 data", cars.size());
		return cars;
	}

	public List<ScrapedCar> parseCarsJs(String carsJs) {
		return parseCarsJs(carsJs, Map.of());
	}

	public List<ScrapedTrack> parseTracksJs(String tracksJs) {
		var tracks = new ArrayList<ScrapedTrack>();

        Pattern blockPattern = Pattern.compile("\\{[^{}]*?\\}");
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

	private String extractField(String block, String fieldName) {
        Pattern p = Pattern.compile(Pattern.quote(fieldName) + ":([\"'`])(.*?)\\1");
		Matcher m = p.matcher(block);
        return m.find() ? m.group(2) : null;
	}

	String extractScriptSrc(String html, String pathPrefix) {
		Pattern p = Pattern.compile("src=\"(" + Pattern.quote(pathPrefix) + "[^\"]+)\"");
		Matcher m = p.matcher(html);
		if (!m.find()) {
			throw new IllegalStateException("Could not find script with prefix: " + pathPrefix);
		}
		return m.group(1);
	}

    String extractChunkFilename(String indexJs, String prefix) {
        return extractPattern(indexJs, "[\"'`]\\.\\/?(" + Pattern.quote(prefix) + "-[\\w-]+\\.js)[\"'`]");
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

	public record ScrapedCar(String gt7Id, String manufacturer, String name, String imageUrl) {
	}

	public record ScrapedTrack(String id, String name, String country, String baseId, String imageUrl) {
	}
}
