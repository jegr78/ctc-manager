package org.ctc.trailer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.admin.dto.MatchdayGraphicData;
import org.ctc.admin.service.MatchdayOverviewGraphicService;
import org.ctc.admin.service.PlayoffRoundOverviewGraphicService;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PlayoffRoundRepository;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.service.CatalogImageService;
import org.ctc.domain.service.MusicLibraryService;
import org.ctc.domain.service.PhaseTestFixtures;
import org.ctc.domain.service.PickPublicationService;
import org.ctc.media.cutout.CutoutModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TrailerPlanServiceTest {

    final MatchdayRepository matchdays = mock(MatchdayRepository.class);
    final PlayoffRoundRepository rounds = mock(PlayoffRoundRepository.class);
    final MatchdayOverviewGraphicService matchdayOverview = mock(MatchdayOverviewGraphicService.class);
    final PlayoffRoundOverviewGraphicService roundOverview = mock(PlayoffRoundOverviewGraphicService.class);
    final PickPublicationService publications = mock(PickPublicationService.class);
    final CatalogImageService gallery = mock(CatalogImageService.class);
    final TrackRepository tracks = mock(TrackRepository.class);
    final CarRepository cars = mock(CarRepository.class);
    final MusicLibraryService music = mock(MusicLibraryService.class);
    final CutoutModel model = mock(CutoutModel.class);
    final TrailerPlanService service = new TrailerPlanService(matchdays, rounds, matchdayOverview, roundOverview, publications,
            gallery, tracks, cars, music, model);

    Matchday matchday;
    Track spa;
    Track fuji;
    Car gtr;
    final List<MatchdayGraphicData.MatchGraphicRow> rows = new ArrayList<>();

    @BeforeEach
    void setUp() {
        var season = new Season("Test_Trailer Season");
        season.setId(UUID.randomUUID());
        matchday = PhaseTestFixtures.matchdayInRegularPhase(season, "Test_Trailer MD1", 0);
        matchday.setId(UUID.randomUUID());
        when(matchdays.findById(matchday.getId())).thenReturn(Optional.of(matchday));
        when(matchdayOverview.prepareBaseContext(matchday)).thenAnswer(inv ->
                new MatchdayGraphicData("Test_Trailer MD1", "Test_Trailer Season", "2027", "logo", "font", List.copyOf(rows)));
        spa = track("Circuit de Spa-Francorchamps", "SPA");
        fuji = track("Fuji International Speedway", null);
        gtr = car("Nissan", "GT-R NISMO GT3 '18");
        lenient().when(music.list()).thenReturn(List.of(new MusicTrack()));
        lenient().when(model.isAvailable()).thenReturn(true);
        lenient().when(gallery.firstTrackImage(any(), any())).thenReturn(Optional.of(new TrackImage()));
        lenient().when(gallery.firstCarImage(any(), any())).thenReturn(Optional.of(new CarImage()));
    }

    @Test
    void givenTwoLegsWithEqualContentFingerprint_whenPlan_thenOneSceneWithLegs1And2() {
        // given
        var match = match("HOM", "AWY");
        released(race(match, spa, gtr, "S01"));
        released(race(match, spa, gtr, "S01"));

        // when
        var plan = service.plan(new TrailerScope.MatchdayScope(matchday.getId()));

        // then
        assertThat(plan.races()).singleElement().satisfies(scene -> {
            assertThat(scene.legNumbers()).containsExactly(1, 2);
            assertThat(scene.trackShortName()).isEqualTo("SPA");
            assertThat(scene.carName()).isEqualTo("GT-R NISMO GT3 '18");
        });
        assertThat(plan.blockers()).isEmpty();
    }

    @Test
    void givenTwoLegsWithDifferentTrack_whenPlan_thenTwoScenes() {
        // given
        var match = match("HOM", "AWY");
        released(race(match, spa, gtr, "S01"));
        released(race(match, fuji, gtr, "S01"));

        // when
        var plan = service.plan(new TrailerScope.MatchdayScope(matchday.getId()));

        // then
        assertThat(plan.races()).extracting(TrailerRaceScene::legNumbers).containsExactly(List.of(1), List.of(2));
        assertThat(plan.races().get(1).trackShortName()).as("no short name stored").isNull();
    }

    @Test
    void givenByeMatch_whenPlan_thenExcluded() {
        // given
        released(race(match("HOM", "AWY"), spa, gtr, "S01"));
        var bye = match("BYE", "NONE");
        bye.setBye(true);
        race(bye, spa, gtr, "S01");

        // when
        var plan = service.plan(new TrailerScope.MatchdayScope(matchday.getId()));

        // then
        assertThat(plan.races()).hasSize(1);
    }

    @Test
    void givenOnlyByes_whenPlan_thenBlocker() {
        // given
        var bye = match("BYE", "NONE");
        bye.setBye(true);

        // when
        var plan = service.plan(new TrailerScope.MatchdayScope(matchday.getId()));

        // then
        assertThat(plan.blockers()).extracting(TrailerChecklistItem::message).containsExactly("No races to show");
    }

    @Test
    void givenUnreleasedRace_whenPlan_thenBlockerWithRaceLink() {
        // given
        var match = match("HOM", "AWY");
        released(race(match, spa, gtr, "S01"));
        var second = race(match, spa, gtr, "S01");
        when(publications.releasedContent(second)).thenReturn(Optional.empty());

        // when
        var plan = service.plan(new TrailerScope.MatchdayScope(matchday.getId()));

        // then
        assertThat(plan.blockers()).singleElement().satisfies(item -> {
            assertThat(item.message()).isEqualTo("Setup of HOM vs AWY leg 2 is not published");
            assertThat(item.fixUrl()).isEqualTo("/admin/races/" + second.getId());
        });
    }

    @Test
    void givenTrackWithoutBackgroundLayoutOrLogo_whenPlan_thenThreeBlockersWithEditorLinks() {
        // given
        spa.setImageUrl(null);
        when(gallery.firstTrackImage(spa.getId(), TrackImageKind.BACKGROUND)).thenReturn(Optional.empty());
        when(gallery.firstTrackImage(spa.getId(), TrackImageKind.LAYOUT)).thenReturn(Optional.empty());
        released(race(match("HOM", "AWY"), spa, gtr, "S01"));

        // when
        var plan = service.plan(new TrailerScope.MatchdayScope(matchday.getId()));

        // then
        assertThat(plan.blockers()).extracting(TrailerChecklistItem::message).containsExactly(
                "Track Circuit de Spa-Francorchamps has no logo",
                "Track Circuit de Spa-Francorchamps has no background image",
                "Track Circuit de Spa-Francorchamps has no layout map");
        assertThat(plan.blockers()).extracting(TrailerChecklistItem::fixUrl)
                .containsOnly("/admin/tracks/" + spa.getId() + "/edit#track-images");
    }

    @Test
    void givenCarWithoutStudioOrCutout_whenPlan_thenBlocker() {
        // given
        when(gallery.firstCarImage(gtr.getId(), CarImageKind.CUTOUT)).thenReturn(Optional.empty());
        when(gallery.firstCarImage(gtr.getId(), CarImageKind.STUDIO)).thenReturn(Optional.empty());
        released(race(match("HOM", "AWY"), spa, gtr, "S01"));

        // when
        var plan = service.plan(new TrailerScope.MatchdayScope(matchday.getId()));

        // then
        assertThat(plan.blockers()).singleElement().satisfies(item -> {
            assertThat(item.message()).isEqualTo("Car Nissan GT-R NISMO GT3 '18 has no studio image or cutout");
            assertThat(item.fixUrl()).isEqualTo("/admin/cars/" + gtr.getId() + "/edit#car-images");
        });
    }

    @Test
    void givenCarWithStudioOnly_whenPlan_thenInCarsNeedingCutoutAndNoBlocker() {
        // given
        when(gallery.firstCarImage(gtr.getId(), CarImageKind.CUTOUT)).thenReturn(Optional.empty());
        released(race(match("HOM", "AWY"), spa, gtr, "S01"));

        // when
        var plan = service.plan(new TrailerScope.MatchdayScope(matchday.getId()));

        // then
        assertThat(plan.carsNeedingCutout()).containsExactly(gtr.getId());
        assertThat(plan.blockers()).isEmpty();
    }

    @Test
    void givenCarsNeedingCutoutAndModelMissing_whenPlan_thenBlocker() {
        // given
        when(gallery.firstCarImage(gtr.getId(), CarImageKind.CUTOUT)).thenReturn(Optional.empty());
        when(model.isAvailable()).thenReturn(false);
        released(race(match("HOM", "AWY"), spa, gtr, "S01"));

        // when
        var plan = service.plan(new TrailerScope.MatchdayScope(matchday.getId()));

        // then
        assertThat(plan.blockers()).extracting(TrailerChecklistItem::message).containsExactly("Cutout model not installed");
    }

    @Test
    void givenEmptyMusicLibrary_whenPlan_thenBlockerWithMusicLink() {
        // given
        when(music.list()).thenReturn(List.of());
        released(race(match("HOM", "AWY"), spa, gtr, "S01"));

        // when
        var plan = service.plan(new TrailerScope.MatchdayScope(matchday.getId()));

        // then
        assertThat(plan.blockers()).singleElement().satisfies(item -> {
            assertThat(item.message()).isEqualTo("No music in the library");
            assertThat(item.fixUrl()).isEqualTo("/admin/music");
        });
    }

    @Test
    void givenSixMatchesIdenticalLegs_whenEstimatedLength_then1m41s() {
        // given
        for (int i = 0; i < 6; i++) {
            var match = match("H" + i, "A" + i);
            released(race(match, spa, gtr, "S01"));
            released(race(match, spa, gtr, "S01"));
        }

        // when
        var plan = service.plan(new TrailerScope.MatchdayScope(matchday.getId()));

        // then
        assertThat(service.estimatedLength(plan)).isEqualTo(Duration.ofSeconds(101));
    }

    private Match match(String home, String away) {
        var homeTeam = new Team("Test_" + home, home);
        homeTeam.setId(UUID.randomUUID());
        var awayTeam = new Team("Test_" + away, away);
        awayTeam.setId(UUID.randomUUID());
        var match = new Match(matchday, homeTeam, awayTeam);
        match.setId(UUID.randomUUID());
        matchday.getMatches().add(match);
        rows.add(new MatchdayGraphicData.MatchGraphicRow("Test_" + home, home, "logo-" + home, "#000", "#111", "#222", 1, "1-0-0",
                "Test_" + away, away, "logo-" + away, "#000", "#111", "#222", 2, "0-1-0", null, null, null, match.getId()));
        return match;
    }

    private Race race(Match match, Track track, Car car, String weather) {
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);
        race.setTrack(track);
        race.setCar(car);
        var settings = new RaceSettings(race);
        race.setSettings(settings);
        new RaceSettingsValues(12, 2, 3, 4, "Default", 1, TimeProgressionMode.MULTIPLIER, 1, weather, "Afternoon", "RS", "None")
                .applyTo(settings);
        match.getRaces().add(race);
        lenient().when(publications.releasedContent(race)).thenReturn(Optional.empty());
        return race;
    }

    private void released(Race race) {
        when(publications.releasedContent(race)).thenReturn(Optional.of(PickPublicationContent.from(race)));
    }

    private Track track(String name, String shortName) {
        var track = new Track(name);
        track.setId(UUID.randomUUID());
        track.setDisplayShortName(shortName);
        track.setImageUrl("/uploads/tracks/" + track.getId() + "/logo.png");
        lenient().when(tracks.findById(track.getId())).thenReturn(Optional.of(track));
        return track;
    }

    private Car car(String manufacturer, String name) {
        var car = new Car(manufacturer, name);
        car.setId(UUID.randomUUID());
        lenient().when(cars.findById(car.getId())).thenReturn(Optional.of(car));
        return car;
    }
}
