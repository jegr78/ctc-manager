package org.ctc.gt7sync;

import static org.springframework.util.StringUtils.hasText;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.integration.OutboundPolicy;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class Gt7ScraperService {

	private final OutboundPolicy outboundPolicy;

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
		String html = externalConnection(CARS_PAGE).get().html();
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
		String html = externalConnection(TRACKS_PAGE).get().html();
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
		Pattern p = Pattern.compile("import\\(\\s*([\"'`])\\./([a-f0-9]{6}-[\\w-]+\\.js)\\1\\s*\\)");
		Matcher m = p.matcher(indexJs);
		while (m.find()) {
			map.put(m.group(2).substring(0, 6), m.group(2));
		}
		return map;
	}

	List<ScrapedTrack> resolveTrackImagesParallel(List<ScrapedTrack> tracks, Map<String, String> imageChunkMap) {
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
						String imageUrl = extractTrackImageUrl(baseId, chunkJs);
						if (imageUrl != null) imageUrlMap.put(baseId, imageUrl);
					} catch (IOException e) {
						log.warn("Failed to resolve image for baseId {}: {}", baseId, e.getMessage());
					}
				})
		).toArray(CompletableFuture[]::new);

		CompletableFuture.allOf(futures).join();
		log.info("Resolved {} track images in parallel", imageUrlMap.size());

		return tracks.stream()
				.map(t -> new ScrapedTrack(t.id(), t.name(), t.country(), t.baseId(),
                        t.baseId() == null ? null : imageUrlMap.get(t.baseId())))
				.toList();
	}

    String extractTrackImageUrl(String baseId, String chunkJs) {
        if (baseId == null || !baseId.matches("[a-f0-9]{6}")) return null;
        var pattern = Pattern.compile("([\"'`])(/common/dist/gt7/tracklist/assets/"
                + Pattern.quote(baseId) + "-[\\w-]+\\.png)\\1");
        var match = pattern.matcher(chunkJs);
        return match.find() ? BASE_URL + match.group(2) : null;
    }

    /** The official 2K car shots of one car, ordered by slot; an empty list when GT7 lists none. */
    public List<CarShot> scrapeCarShots(String gt7Id) throws IOException {
        return scrapeCarShots(carShotIndex(), gt7Id);
    }

    /** The GT7 car list bundle that names every car shot module, fetched once for several cars. */
    public CarShotIndex carShotIndex() throws IOException {
        String html = externalConnection(CARS_PAGE).get().html();
        String indexJs = fetchText(BASE_URL + extractScriptSrc(html, "/common/dist/gt7/carlist/assets/index-"));
        requireCarShotFormat(indexJs);
        return new CarShotIndex(indexJs);
    }

    /** The official 2K car shots of one car from an already fetched car list bundle. */
    public List<CarShot> scrapeCarShots(CarShotIndex index, String gt7Id) throws IOException {
        String indexJs = index.indexJs();
        var shots = new ArrayList<CarShot>();
        for (var module : carShotModules(indexJs, gt7Id).entrySet()) {
            String imageUrl = carShotImageUrl(fetchText(BASE_URL + "/common/dist/gt7/carlist/assets/" + module.getValue()), gt7Id, module.getKey());
            if (imageUrl == null) throw new IOException("GT7 car shot " + module.getKey() + " has no image");
            shots.add(new CarShot(module.getKey(), imageUrl));
        }
        return shots;
    }

    /** Whether the value has the stored GT7 car ID form such as {@code car365}. */
    public static boolean isCarId(String gt7Id) {
        return gt7Id != null && gt7Id.matches("car[1-9]\\d{0,9}");
    }

    /** Fails when the bundle lists no 2K car shot at all, so a format change is not reported as a car without shots. */
    void requireCarShotFormat(String indexJs) throws IOException {
        if (!Pattern.compile("car_shots/2k/car\\d+_\\d+_\\d+\\.jpg").matcher(indexJs).find()) {
            throw new IOException("The GT7 car list no longer lists car shots in the expected format");
        }
    }

    /** Slot such as {@code 2_01} to the module file of every 2K car shot of the car, in slot order. */
    Map<String, String> carShotModules(String indexJs, String gt7Id) {
        if (!isCarId(gt7Id)) throw new IllegalArgumentException("Invalid GT7 car ID: " + gt7Id);
        var pattern = Pattern.compile("car_shots/2k/" + gt7Id + "_(\\d+_\\d+)\\.jpg[\"'`]\\s*:\\s*\\(\\)\\s*=>\\s*[\\w$]+\\(\\s*\\(\\)\\s*=>\\s*import\\(\\s*([\"'`])\\./("
                + gt7Id + "_\\1-[\\w-]+\\.js)\\2");
        var modules = new TreeMap<String, String>();
        var match = pattern.matcher(indexJs);
        while (match.find()) modules.putIfAbsent(match.group(1), match.group(3));
        return modules;
    }

    String carShotImageUrl(String moduleJs, String gt7Id, String slot) {
        if (!isCarId(gt7Id) || slot == null || !slot.matches("\\d+_\\d+")) return null;
        var pattern = Pattern.compile("([\"'`])(/common/dist/gt7/carlist/assets/" + gt7Id + "_" + Pattern.quote(slot) + "-[\\w-]+\\.jpg)\\1");
        var match = pattern.matcher(moduleJs);
        return match.find() ? BASE_URL + match.group(2) : null;
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

			if (gt7Id == null || nameLong == null || nameShort == null
                    || !gt7Id.equals(blockMatcher.group(1)) || !gt7Id.matches("car[1-9]\\d{0,9}")
                    || Long.parseLong(gt7Id.substring(3)) > Integer.MAX_VALUE) {
				continue;
			}

			String manufacturer = manufacturerId != null ? manufacturerMap.get(manufacturerId) : null;
			if (!hasText(manufacturer)) {
				manufacturer = extractManufacturer(nameLong, nameShort);
			}

			String imageUrl = CAR_IMAGE_BASE + gt7Id + ".png";
			String category = extractField(block, "carClass");
            String drivetrain = extractField(block, "driveTrain");
            cars.add(new ScrapedCar(gt7Id, manufacturer, nameShort, imageUrl,
                    org.ctc.domain.model.CarCategory.fromSource(category), org.ctc.domain.model.Drivetrain.fromSource(drivetrain), category, drivetrain));
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
        Pattern p = Pattern.compile(Pattern.quote(fieldName) + "\\s*:\\s*([\"'`])((?:\\\\.|(?!\\1).)*)\\1", Pattern.DOTALL);
		Matcher m = p.matcher(block);
        return m.find() ? org.apache.commons.text.StringEscapeUtils.unescapeEcmaScript(m.group(2)) : null;
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

	private Connection externalConnection(String url) throws IOException {
		outboundPolicy.requireEnabled();
		return Jsoup.connect(url).timeout(10_000).maxBodySize(4 * 1024 * 1024);
	}

	String fetchText(String url) throws IOException {
		return externalConnection(url).ignoreContentType(true).execute().body();
	}

	public record ScrapedCar(String gt7Id, String manufacturer, String name, String imageUrl,
                             org.ctc.domain.model.CarCategory category, org.ctc.domain.model.Drivetrain drivetrain,
                             String categoryValue, String drivetrainValue) {
        public ScrapedCar(String gt7Id, String manufacturer, String name, String imageUrl) {
            this(gt7Id, manufacturer, name, imageUrl, null, null, null, null);
        }
	}

    public record CarShot(String slot, String imageUrl) {
    }

	public record ScrapedTrack(String id, String name, String country, String baseId, String imageUrl) {
	}

    public record CarShotIndex(String indexJs) { }
}
