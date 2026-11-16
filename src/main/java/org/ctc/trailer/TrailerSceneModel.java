package org.ctc.trailer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.CarImageKind;
import org.ctc.domain.model.GalleryImage;
import org.ctc.domain.model.PickPublicationContent;
import org.ctc.domain.model.TimeProgressionMode;
import org.ctc.domain.model.TrackImageKind;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.service.CatalogImageService;
import org.ctc.domain.service.FileStorageService;
import org.ctc.domain.service.ImageContent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Prepares the template variables of every trailer scene; images and fonts become data URIs for the offline renderer. */
@Component
@RequiredArgsConstructor
public class TrailerSceneModel {

    public static final List<String> PLAIN_LAYERS = List.of("background", "foreground");
    public static final List<String> CAR_LAYERS = List.of("background", "car", "foreground");
    private static final String MISSING = "–";

    private final CatalogImageService gallery;
    private final TrackRepository tracks;
    private final FileStorageService files;

    public record Tile(String label, String value, int span) {
    }

    @Transactional(readOnly = true)
    public List<TrailerScene> scenes(TrailerPlan plan) {
        var common = common(plan);
        var scenes = new ArrayList<TrailerScene>();
        scenes.add(scene("intro", scenes.size(), TrailerPlanService.INTRO, PLAIN_LAYERS, common, Map.of()));
        var overview = new HashMap<String, Object>();
        overview.put("pairings", plan.overview().matches());
        int count = plan.overview().matches().size();
        overview.put("pairingsSize", count <= 4 ? "rows-few" : count <= 8 ? "rows-many" : "rows-dense");
        scenes.add(scene("overview", scenes.size(), TrailerPlanService.OVERVIEW, PLAIN_LAYERS, common, overview));
        for (var race : plan.races()) {
            var shared = raceVariables(race);
            scenes.add(scene("race-visual", scenes.size(), TrailerPlanService.RACE_VISUAL, CAR_LAYERS, common, shared));
            var settings = new HashMap<>(shared);
            settings.put("tiles", tiles(race.content()));
            settings.put("carName", race.content().carName());
            settings.put("teamSize", fit(Math.max(race.homeTeam().length(), race.awayTeam().length()), 20, 32));
            scenes.add(scene("race-settings", scenes.size(), TrailerPlanService.RACE_SETTINGS, PLAIN_LAYERS, common, settings));
        }
        scenes.add(scene("outro", scenes.size(), TrailerPlanService.OUTRO, PLAIN_LAYERS, common, Map.of()));
        return List.copyOf(scenes);
    }

    private Map<String, Object> common(TrailerPlan plan) {
        var vars = new HashMap<String, Object>();
        vars.put("title", plan.title());
        vars.put("seasonLabel", plan.seasonLabel());
        vars.put("ctcLogo", classpath("static/admin/img/ctc-logo-white.png", "image/png"));
        vars.put("fontConthrax", classpath("static/admin/fonts/ConthraxSb.woff2", "font/woff2"));
        vars.put("fontBarlow500", classpath("static/admin/fonts/Barlow-Medium.woff2", "font/woff2"));
        vars.put("fontBarlow600", classpath("static/admin/fonts/Barlow-SemiBold.woff2", "font/woff2"));
        vars.put("fontCondensed500", classpath("static/admin/fonts/BarlowCondensed-Medium.woff2", "font/woff2"));
        vars.put("fontCondensed600", classpath("static/admin/fonts/BarlowCondensed-SemiBold.woff2", "font/woff2"));
        vars.put("fontCondensed700", classpath("static/admin/fonts/BarlowCondensed-Bold.woff2", "font/woff2"));
        return vars;
    }

    private Map<String, Object> raceVariables(TrailerRaceScene race) {
        var vars = new HashMap<String, Object>();
        boolean shortName = race.trackShortName() != null && !race.trackShortName().isBlank();
        String headline = shortName ? race.trackShortName() : race.trackName();
        vars.put("headline", headline);
        vars.put("subline", shortName ? race.trackName() : null);
        vars.put("headlineSize", fit(headline.length(), 8, 14, 22));
        vars.put("carName", race.carName());
        vars.put("carNameSize", fit(race.carName().length(), 24, 34));
        vars.put("pairing", race.pairingLabel());
        var shorts = race.pairingLabel().split(" vs ", 2);
        vars.put("homeShort", shorts[0]);
        vars.put("awayShort", shorts.length > 1 ? shorts[1] : "");
        vars.put("homeTeam", race.homeTeam());
        vars.put("awayTeam", race.awayTeam());
        vars.put("homeLogo", race.homeLogo());
        vars.put("awayLogo", race.awayLogo());
        vars.put("homeStanding", race.homeStanding());
        vars.put("awayStanding", race.awayStanding());
        var legs = race.legNumbers();
        vars.put("legLabel", legs.size() == 1 ? "LEG " + legs.getFirst()
                : "LEGS " + String.join(" & ", legs.stream().map(String::valueOf).toList()));
        var track = tracks.findById(race.trackId()).orElseThrow();
        vars.put("trackLogo", upload(track.getImageUrl()));
        vars.put("trackBackground", gallery.firstTrackImage(race.trackId(), TrackImageKind.BACKGROUND).map(this::upload).orElse(null));
        vars.put("trackLayout", gallery.firstTrackImage(race.trackId(), TrackImageKind.LAYOUT).map(this::upload).orElse(null));
        vars.put("carCutout", gallery.firstCarImage(race.carId(), CarImageKind.CUTOUT).map(this::upload).orElse(null));
        return vars;
    }

    static List<Tile> tiles(PickPublicationContent content) {
        String progression = content.timeProgressionMode() == TimeProgressionMode.EQUAL_CONDITIONS ? "Equal Conditions"
                : content.timeProgressionMultiplier() == null ? MISSING : content.timeProgressionMultiplier() + "x";
        return List.of(
                new Tile("Laps", text(content.laps()), 1),
                new Tile("Pit stops", text(content.requiredPitStops()), 1),
                new Tile("Tyre wear", content.tyreWear() == null ? MISSING : "x" + content.tyreWear(), 1),
                new Tile("Fuel use", content.fuelConsumption() == null ? MISSING : "x" + content.fuelConsumption(), 1),
                new Tile("Refuel", content.refuelingSpeed() == null ? MISSING : content.refuelingSpeed() + " l/s", 1),
                new Tile("Initial fuel", text(content.initialFuel()), 1),
                new Tile("Time of day", text(content.timeOfDay()), 1),
                new Tile("Progression", progression, 1),
                new Tile("Slipstream", content.slipstream() == null ? MISSING : content.slipstream().getLabel(), 1),
                new Tile("Mandatory", text(content.mandatoryTyres()), 1),
                new Tile("Start type", content.startType() == null ? MISSING : content.startType().getLabel(), 2),
                new Tile("Tyres", text(content.availableTyres()), 3),
                new Tile("Weather", text(content.weather()), 5));
    }

    static String fit(int length, int... limits) {
        String[] sizes = limits.length == 3 ? new String[] {"fit-xl", "fit-l", "fit-m", "fit-s"} : new String[] {"fit-l", "fit-m", "fit-s"};
        for (int i = 0; i < limits.length; i++) {
            if (length <= limits[i]) return sizes[i];
        }
        return sizes[sizes.length - 1];
    }

    private static String text(Object value) {
        return value == null || value.toString().isBlank() ? MISSING : value.toString();
    }

    private static TrailerScene scene(String name, int index, java.time.Duration length, List<String> layers,
                                      Map<String, Object> common, Map<String, Object> own) {
        var vars = new HashMap<>(common);
        vars.putAll(own);
        return new TrailerScene(name, index, length, layers, vars);
    }

    private String upload(GalleryImage image) {
        return upload(image.getImagePath());
    }

    private String upload(String url) {
        if (url == null) return null;
        try {
            byte[] data = Files.readAllBytes(files.resolve(url));
            var format = ImageContent.detect(data);
            if (format == null) return null;
            String mime = switch (format) {
                case PNG -> "image/png";
                case JPEG -> "image/jpeg";
                case WEBP -> "image/webp";
            };
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(data);
        } catch (IOException | IllegalArgumentException unreadable) {
            return null;
        }
    }

    private static String classpath(String location, String mime) {
        try (InputStream in = TrailerSceneModel.class.getClassLoader().getResourceAsStream(location)) {
            if (in == null) throw new IllegalStateException("Missing classpath resource " + location);
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
