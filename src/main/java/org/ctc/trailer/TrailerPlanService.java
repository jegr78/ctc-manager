package org.ctc.trailer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.admin.dto.MatchdayGraphicData;
import org.ctc.admin.service.MatchdayOverviewGraphicService;
import org.ctc.admin.service.PlayoffRoundOverviewGraphicService;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.CarImageKind;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Track;
import org.ctc.domain.model.TrackImageKind;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PlayoffRoundRepository;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.service.CatalogImageService;
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

    @Transactional(readOnly = true)
    public TrailerPlan plan(TrailerScope scope) {
        MatchdayGraphicData overview;
        Map<UUID, List<Race>> racesByPairing = new LinkedHashMap<>();
        switch (scope) {
            case TrailerScope.MatchdayScope matchday -> {
                var day = matchdays.findById(matchday.matchdayId())
                        .orElseThrow(() -> new EntityNotFoundException("Matchday", matchday.matchdayId()));
                overview = matchdayOverview.prepareBaseContext(day);
                day.getMatches().stream().filter(match -> !match.isBye())
                        .forEach(match -> racesByPairing.put(match.getId(), match.getRaces()));
            }
            case TrailerScope.PlayoffRoundScope round -> {
                var playoffRound = rounds.findById(round.roundId())
                        .orElseThrow(() -> new EntityNotFoundException("PlayoffRound", round.roundId()));
                overview = roundOverview.prepareBaseContext(playoffRound);
                playoffRound.getMatchups().forEach(matchup -> racesByPairing.put(matchup.getId(), matchup.getRaces()));
            }
        }
        var blockers = new ArrayList<TrailerChecklistItem>();
        var scenes = new ArrayList<TrailerRaceScene>();
        Set<UUID> needingCutout = new LinkedHashSet<>();
        Set<UUID> checkedTracks = new LinkedHashSet<>();
        Set<UUID> checkedCars = new LinkedHashSet<>();
        for (var row : overview.matches()) {
            var races = racesByPairing.getOrDefault(row.pairingId(), List.of());
            String pairing = row.homeTeamShortName() + " vs " + row.awayTeamShortName();
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
                var track = tracks.findById(content.trackId()).orElseThrow(() -> new EntityNotFoundException("Track", content.trackId()));
                var car = cars.findById(content.carId()).orElseThrow(() -> new EntityNotFoundException("Car", content.carId()));
                if (checkedTracks.add(track.getId())) checkTrack(track, blockers);
                if (checkedCars.add(car.getId())) checkCar(car, blockers, needingCutout);
                previous = new TrailerRaceScene(race.getId(), pairing, row.homeTeamName(), row.awayTeamName(),
                        row.homeLogoBase64(), row.awayLogoBase64(), List.of(leg), content, track.getId(), track.getName(),
                        track.getDisplayShortName(), car.getId(), car.getName());
                scenes.add(previous);
            }
        }
        if (scenes.isEmpty() && blockers.isEmpty()) blockers.add(new TrailerChecklistItem("No races to show", null));
        if (!needingCutout.isEmpty() && !model.isAvailable()) blockers.add(new TrailerChecklistItem("Cutout model not installed", null));
        if (music.list().isEmpty()) blockers.add(new TrailerChecklistItem("No music in the library", "/admin/music"));
        return new TrailerPlan(scope, overview.matchdayLabel(), overview.seasonName(), overview, List.copyOf(scenes),
                List.copyOf(blockers), Set.copyOf(needingCutout));
    }

    public Duration estimatedLength(TrailerPlan plan) {
        return INTRO.plus(OVERVIEW).plus(OUTRO).plus(RACE_VISUAL.plus(RACE_SETTINGS).multipliedBy(plan.races().size()));
    }

    private void checkTrack(Track track, List<TrailerChecklistItem> blockers) {
        String fix = "/admin/tracks/" + track.getId() + "/edit#track-images";
        String name = "Track " + track.getName();
        if (track.getImageUrl() == null || track.getImageUrl().isBlank()) blockers.add(new TrailerChecklistItem(name + " has no logo", fix));
        if (gallery.firstTrackImage(track.getId(), TrackImageKind.BACKGROUND).isEmpty()) {
            blockers.add(new TrailerChecklistItem(name + " has no background image", fix));
        }
        if (gallery.firstTrackImage(track.getId(), TrackImageKind.LAYOUT).isEmpty()) {
            blockers.add(new TrailerChecklistItem(name + " has no layout map", fix));
        }
    }

    private void checkCar(Car car, List<TrailerChecklistItem> blockers, Set<UUID> needingCutout) {
        if (gallery.firstCarImage(car.getId(), CarImageKind.CUTOUT).isPresent()) return;
        if (gallery.firstCarImage(car.getId(), CarImageKind.STUDIO).isPresent()) {
            needingCutout.add(car.getId());
            return;
        }
        blockers.add(new TrailerChecklistItem("Car " + car.getManufacturer() + " " + car.getName() + " has no studio image or cutout",
                "/admin/cars/" + car.getId() + "/edit#car-images"));
    }

    private static TrailerRaceScene withLegs(TrailerRaceScene scene, List<Integer> legs) {
        return new TrailerRaceScene(scene.raceId(), scene.pairingLabel(), scene.homeTeam(), scene.awayTeam(), scene.homeLogo(),
                scene.awayLogo(), List.copyOf(legs), scene.content(), scene.trackId(), scene.trackName(), scene.trackShortName(),
                scene.carId(), scene.carName());
    }
}
