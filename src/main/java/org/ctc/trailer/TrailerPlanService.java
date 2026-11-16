package org.ctc.trailer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.MatchdayGraphicData;
import org.ctc.admin.service.MatchdayOverviewGraphicService;
import org.ctc.admin.service.PlayoffRoundOverviewGraphicService;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.CarImageKind;
import org.ctc.domain.model.GalleryImage;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Track;
import org.ctc.domain.model.TrackImageKind;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PlayoffRoundRepository;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.service.CatalogImageService;
import org.ctc.domain.service.FileStorageService;
import org.ctc.domain.service.MusicLibraryService;
import org.ctc.domain.service.PickPublicationService;
import org.ctc.media.cutout.CutoutModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the scene plan of a trailer from published picks and lists every open precondition. Car and track come from
 * the published content, never from the race's current selection.
 */
@Service
@RequiredArgsConstructor
public class TrailerPlanService {

    public static final Duration INTRO = Duration.ofSeconds(4);
    public static final Duration OVERVIEW = Duration.ofSeconds(8);
    public static final Duration RACE_VISUAL = Duration.ofSeconds(6);
    public static final Duration RACE_CAR = Duration.ofSeconds(4);
    public static final Duration RACE_SETTINGS = Duration.ofSeconds(8);
    public static final Duration OUTRO = Duration.ofSeconds(5);

    private final MatchdayRepository matchdays;
    private final PlayoffRoundRepository rounds;
    private final MatchdayOverviewGraphicService matchdayOverview;
    private final PlayoffRoundOverviewGraphicService roundOverview;
    private final PickPublicationService publications;
    private final CatalogImageService gallery;
    private final TrackRepository tracks;
    private final CarRepository cars;
    private final MusicLibraryService music;
    private final CutoutModel model;
    private final FileStorageService files;

    @Transactional(readOnly = true)
    public TrailerPlan plan(TrailerScope scope) {
        return build(scope, true);
    }

    /** The same blockers and races as {@link #plan}, without the overview graphic data (logos, standings), for pages. */
    @Transactional(readOnly = true)
    public TrailerPlan checklist(TrailerScope scope) {
        return build(scope, false);
    }

    private record Pairing(String homeShort, String awayShort, String homeName, String awayName, String homeLogo, String awayLogo,
                           String homeStanding, String awayStanding, List<Race> races) {
    }

    private TrailerPlan build(TrailerScope scope, boolean full) {
        MatchdayGraphicData overview = null;
        String title;
        String seasonLabel;
        var pairings = new ArrayList<Pairing>();
        switch (scope) {
            case TrailerScope.MatchdayScope matchday -> {
                var day = matchdays.findById(matchday.matchdayId())
                        .orElseThrow(() -> new EntityNotFoundException("Matchday", matchday.matchdayId()));
                var played = day.getMatches().stream().filter(match -> !match.isBye() && match.getWalkoverTeam() == null).toList();
                if (full) {
                    overview = matchdayOverview.prepareBaseContext(day);
                    Map<UUID, List<Race>> races = new LinkedHashMap<>();
                    played.forEach(match -> races.put(match.getId(), match.getRaces()));
                    addRows(overview, races, pairings);
                } else {
                    played.forEach(match -> pairings.add(light(match.getHomeTeam().getShortName(), match.getAwayTeam().getShortName(),
                            match.getRaces())));
                }
                title = day.getLabel();
                seasonLabel = day.getSeason().getName();
            }
            case TrailerScope.PlayoffRoundScope round -> {
                var playoffRound = rounds.findById(round.roundId())
                        .orElseThrow(() -> new EntityNotFoundException("PlayoffRound", round.roundId()));
                var played = playoffRound.getMatchups().stream()
                        .filter(matchup -> matchup.getTeam1() != null && matchup.getTeam2() != null && matchup.getWalkoverTeam() == null)
                        .toList();
                if (full) {
                    overview = roundOverview.prepareBaseContext(playoffRound);
                    Map<UUID, List<Race>> races = new LinkedHashMap<>();
                    played.forEach(matchup -> races.put(matchup.getId(), matchup.getRaces()));
                    addRows(overview, races, pairings);
                } else {
                    played.forEach(matchup -> pairings.add(light(matchup.getTeam1().getShortName(), matchup.getTeam2().getShortName(),
                            matchup.getRaces())));
                }
                title = playoffRound.getLabel();
                seasonLabel = playoffRound.getPlayoff().getName();
            }
        }
        if (overview != null) {
            title = overview.matchdayLabel();
            seasonLabel = overview.seasonName();
        }
        var blockers = new ArrayList<TrailerChecklistItem>();
        var scenes = new ArrayList<TrailerRaceScene>();
        Set<UUID> needingCutout = new LinkedHashSet<>();
        Set<UUID> checkedTracks = new LinkedHashSet<>();
        Set<UUID> checkedCars = new LinkedHashSet<>();
        for (var row : pairings) {
            var races = row.races();
            String pairing = row.homeShort() + " vs " + row.awayShort();
            TrailerRaceScene previous = null;
            for (int i = 0; i < races.size(); i++) {
                var race = races.get(i);
                int leg = i + 1;
                var released = publications.releasedContent(race);
                if (released.isEmpty()) {
                    blockers.add(new TrailerChecklistItem("Setup of " + pairing + " leg " + leg + " is not published",
                            "/admin/races/" + race.getId()));
                    previous = null;
                    continue;
                }
                var content = released.get();
                if (previous != null && previous.content().fingerprint().equals(content.fingerprint())) {
                    var legs = new ArrayList<>(previous.legNumbers());
                    legs.add(leg);
                    var merged = withLegs(previous, legs);
                    scenes.set(scenes.size() - 1, merged);
                    previous = merged;
                    continue;
                }
                if (content.trackId() == null || content.carId() == null) {
                    blockers.add(new TrailerChecklistItem("Setup of " + pairing + " leg " + leg + " has no track or car",
                            "/admin/races/" + race.getId()));
                    previous = null;
                    continue;
                }
                var track = tracks.findById(content.trackId()).orElse(null);
                var car = cars.findById(content.carId()).orElse(null);
                if (track == null || car == null) {
                    blockers.add(new TrailerChecklistItem("Published track or car of " + pairing + " leg " + leg + " no longer exists",
                            "/admin/races/" + race.getId()));
                    previous = null;
                    continue;
                }
                if (checkedTracks.add(track.getId())) checkTrack(track, blockers);
                if (checkedCars.add(car.getId())) checkCar(car, blockers, needingCutout);
                previous = new TrailerRaceScene(race.getId(), pairing, row.homeName(), row.awayName(), row.homeLogo(), row.awayLogo(),
                        List.of(leg), content, track.getId(), track.getName(), track.getDisplayShortName(), car.getId(), car.getName(),
                        row.homeStanding(), row.awayStanding());
                scenes.add(previous);
            }
        }
        if (scenes.isEmpty() && blockers.isEmpty()) blockers.add(new TrailerChecklistItem("No races to show", null));
        if (!needingCutout.isEmpty() && !model.isAvailable()) blockers.add(new TrailerChecklistItem("Cutout model not installed", null));
        if (music.list().isEmpty()) blockers.add(new TrailerChecklistItem("No music in the library", "/admin/music"));
        return new TrailerPlan(scope, title, seasonLabel, overview, scenes, blockers, needingCutout);
    }

    private static void addRows(MatchdayGraphicData overview, Map<UUID, List<Race>> races, List<Pairing> pairings) {
        for (var row : overview.matches()) {
            var legs = races.get(row.pairingId());
            if (legs == null) continue;
            pairings.add(new Pairing(row.homeTeamShortName(), row.awayTeamShortName(), row.homeTeamName(), row.awayTeamName(),
                    row.homeLogoBase64(), row.awayLogoBase64(), standing(row.homeSeed(), row.homeRecord()),
                    standing(row.awaySeed(), row.awayRecord()), legs));
        }
    }

    private static Pairing light(String homeShort, String awayShort, List<Race> races) {
        return new Pairing(homeShort, awayShort, homeShort, awayShort, null, null, null, null, races);
    }

    /** "SEED 1 · 4-1-0"; parts without a seed or a record are left out, and null means neither is known. */
    static String standing(int seed, String record) {
        boolean hasRecord = record != null && !record.isBlank() && !"—".equals(record);
        if (seed <= 0) return hasRecord ? record : null;
        return hasRecord ? "SEED " + seed + " · " + record : "SEED " + seed;
    }

    public Duration estimatedLength(TrailerPlan plan) {
        return INTRO.plus(OVERVIEW).plus(OUTRO).plus(RACE_VISUAL.plus(RACE_CAR).plus(RACE_SETTINGS).multipliedBy(plan.races().size()));
    }

    private void checkTrack(Track track, List<TrailerChecklistItem> blockers) {
        String fix = "/admin/tracks/" + track.getId() + "/edit#track-images";
        String name = "Track " + track.getName();
        if (!files.exists(track.getImageUrl())) blockers.add(new TrailerChecklistItem(name + " has no logo", fix));
        if (!stored(gallery.firstTrackImage(track.getId(), TrackImageKind.BACKGROUND))) {
            blockers.add(new TrailerChecklistItem(name + " has no background image", fix));
        }
        if (!stored(gallery.firstTrackImage(track.getId(), TrackImageKind.LAYOUT))) {
            blockers.add(new TrailerChecklistItem(name + " has no layout map", fix));
        }
    }

    private void checkCar(Car car, List<TrailerChecklistItem> blockers, Set<UUID> needingCutout) {
        if (!stored(gallery.firstCarImage(car.getId(), CarImageKind.SCENE))) {
            blockers.add(new TrailerChecklistItem("Car " + car.getManufacturer() + " " + car.getName() + " has no scene image",
                    "/admin/cars/" + car.getId() + "/edit#car-images"));
        }
        if (stored(gallery.firstCarImage(car.getId(), CarImageKind.CUTOUT))) return;
        if (stored(gallery.firstCarImage(car.getId(), CarImageKind.STUDIO))) {
            needingCutout.add(car.getId());
            return;
        }
        blockers.add(new TrailerChecklistItem("Car " + car.getManufacturer() + " " + car.getName() + " has no studio image or cutout",
                "/admin/cars/" + car.getId() + "/edit#car-images"));
    }

    /** A gallery image counts only when its file is stored; the audit reports rows whose file is missing. */
    private boolean stored(Optional<? extends GalleryImage> image) {
        return image.isPresent() && files.exists(image.get().getImagePath());
    }

    private static TrailerRaceScene withLegs(TrailerRaceScene scene, List<Integer> legs) {
        return new TrailerRaceScene(scene.raceId(), scene.pairingLabel(), scene.homeTeam(), scene.awayTeam(), scene.homeLogo(),
                scene.awayLogo(), List.copyOf(legs), scene.content(), scene.trackId(), scene.trackName(), scene.trackShortName(),
                scene.carId(), scene.carName(), scene.homeStanding(), scene.awayStanding());
    }
}
