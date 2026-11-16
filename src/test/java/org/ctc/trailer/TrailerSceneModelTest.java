package org.ctc.trailer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.ctc.admin.dto.MatchdayGraphicData;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.service.CatalogImageService;
import org.ctc.domain.service.FileStorageService;
import org.ctc.testsupport.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrailerSceneModelTest {

    final CatalogImageService gallery = mock(CatalogImageService.class);
    final TrackRepository tracks = mock(TrackRepository.class);
    final FileStorageService files = mock(FileStorageService.class);
    final TrailerSceneModel model = new TrailerSceneModel(gallery, tracks, files);
    @TempDir Path dir;
    final UUID trackId = UUID.randomUUID();
    final UUID carId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        Path jpeg = dir.resolve("bg.jpg");
        Files.write(jpeg, TestImages.jpeg(64, 36));
        Path png = dir.resolve("img.png");
        Files.write(png, TestImages.png(64, 36));
        when(files.resolve(anyString())).thenAnswer(inv -> inv.getArgument(0, String.class).endsWith(".jpg") ? jpeg : png);
        var track = new Track("Circuit de Spa-Francorchamps");
        track.setId(trackId);
        track.setImageUrl("/uploads/tracks/logo.png");
        when(tracks.findById(trackId)).thenReturn(Optional.of(track));
        when(gallery.firstTrackImage(trackId, TrackImageKind.BACKGROUND)).thenReturn(Optional.of(trackImage("/uploads/t/bg.jpg")));
        when(gallery.firstTrackImage(trackId, TrackImageKind.LAYOUT)).thenReturn(Optional.of(trackImage("/uploads/t/map.png")));
        var cutout = new CarImage();
        cutout.setImagePath("/uploads/c/cut.png");
        when(gallery.firstCarImage(any(), any())).thenReturn(Optional.of(cutout));
    }

    @Test
    void givenPlanWithTwoRaces_whenScenes_thenOrderAndLengthsFollowTheTimeline() {
        // when
        var scenes = model.scenes(plan(scene("SPA", List.of(1, 2)), scene(null, List.of(1))));

        // then
        assertThat(scenes).extracting(TrailerScene::name).containsExactly(
                "intro", "overview", "race-visual", "race-settings", "race-visual", "race-settings", "outro");
        assertThat(scenes).extracting(TrailerScene::index).containsExactly(0, 1, 2, 3, 4, 5, 6);
        assertThat(scenes).extracting(TrailerScene::length).containsExactly(TrailerPlanService.INTRO, TrailerPlanService.OVERVIEW,
                TrailerPlanService.RACE_VISUAL, TrailerPlanService.RACE_SETTINGS, TrailerPlanService.RACE_VISUAL,
                TrailerPlanService.RACE_SETTINGS, TrailerPlanService.OUTRO);
        assertThat(scenes.get(2).layers()).containsExactly("background", "car", "foreground");
        assertThat(scenes.get(3).layers()).containsExactly("background", "foreground");
    }

    @Test
    void givenShortName_whenScenes_thenHeadlineIsShortNameWithFullNameBelow() {
        // when
        var visual = model.scenes(plan(scene("SPA", List.of(1, 2)))).get(2).variables();

        // then
        assertThat(visual.get("headline")).isEqualTo("SPA");
        assertThat(visual.get("subline")).isEqualTo("Circuit de Spa-Francorchamps");
        assertThat(visual.get("headlineSize")).isEqualTo("fit-xl");
        assertThat(visual.get("legLabel")).isEqualTo("LEGS 1 & 2");
        assertThat(visual.get("homeShort")).isEqualTo("HOM");
        assertThat(visual.get("awayShort")).isEqualTo("AWY");
        assertThat(visual.get("homeStanding")).isEqualTo("SEED 1 · 4-1-0");
        assertThat(visual.get("awayStanding")).isEqualTo("SEED 2 · 3-2-0");
    }

    @Test
    void givenNoShortName_whenScenes_thenFullNameIsHeadlineInSmallerSize() {
        // when
        var visual = model.scenes(plan(scene(null, List.of(2)))).get(2).variables();

        // then
        assertThat(visual.get("headline")).isEqualTo("Circuit de Spa-Francorchamps");
        assertThat(visual.get("subline")).isNull();
        assertThat(visual.get("headlineSize")).isEqualTo("fit-s");
        assertThat(visual.get("legLabel")).isEqualTo("LEG 2");
    }

    @Test
    void givenImages_whenScenes_thenDataUrisCarryTheRealMimeType() {
        // when
        var visual = model.scenes(plan(scene("SPA", List.of(1)))).get(2).variables();

        // then
        assertThat((String) visual.get("trackBackground")).startsWith("data:image/jpeg;base64,");
        assertThat((String) visual.get("trackLayout")).startsWith("data:image/png;base64,");
        assertThat((String) visual.get("trackLogo")).startsWith("data:image/png;base64,");
        assertThat((String) visual.get("carCutout")).startsWith("data:image/png;base64,");
    }

    @Test
    void givenPublishedSettings_whenScenes_thenTilesShowFormattedValues() {
        // when
        var settings = model.scenes(plan(scene("SPA", List.of(1)))).get(3).variables();

        // then
        @SuppressWarnings("unchecked")
        var tiles = (List<TrailerSceneModel.Tile>) settings.get("tiles");
        assertThat(tiles).extracting(TrailerSceneModel.Tile::label, TrailerSceneModel.Tile::value).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Laps", "12"),
                org.assertj.core.groups.Tuple.tuple("Pit stops", "1"),
                org.assertj.core.groups.Tuple.tuple("Tyre wear", "x2"),
                org.assertj.core.groups.Tuple.tuple("Fuel use", "x3"),
                org.assertj.core.groups.Tuple.tuple("Refuel", "4 l/s"),
                org.assertj.core.groups.Tuple.tuple("Initial fuel", "Default"),
                org.assertj.core.groups.Tuple.tuple("Time of day", "Afternoon"),
                org.assertj.core.groups.Tuple.tuple("Progression", "1x"),
                org.assertj.core.groups.Tuple.tuple("Slipstream", "Real"),
                org.assertj.core.groups.Tuple.tuple("Mandatory", "None"),
                org.assertj.core.groups.Tuple.tuple("Start type", "Grid Start With False Start Check"),
                org.assertj.core.groups.Tuple.tuple("Tyres", "RS"),
                org.assertj.core.groups.Tuple.tuple("Weather", "S01"));
        assertThat(tiles).extracting(TrailerSceneModel.Tile::span).containsExactly(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 3, 5);
        assertThat(settings.get("carName")).isEqualTo("Nissan GT-R NISMO GT3 '18");
    }

    @Test
    void givenLongTeamAndCarNames_whenScenes_thenSmallerFitClasses() {
        // given
        var long60 = "Test_" + "X".repeat(55);
        var scene = new TrailerRaceScene(UUID.randomUUID(), "LNG vs OTH", long60, "Test_Other", "logo", "logo", List.of(1),
                content(), trackId, "Michelin Raceway Road Atlanta", null, carId, "Y".repeat(45), "SEED 1 · 4-1-0", "SEED 2 · 3-2-0");

        // when
        var vars = model.scenes(plan(scene)).get(2).variables();

        // then
        assertThat(vars.get("headlineSize")).isEqualTo("fit-s");
        assertThat(vars.get("carNameSize")).isEqualTo("fit-s");
        assertThat(model.scenes(plan(scene)).get(3).variables().get("teamSize")).isEqualTo("fit-s");
    }

    @Test
    void givenOverview_whenScenes_thenRowsAndTitleAreProvided() {
        // when
        var overview = model.scenes(plan(scene("SPA", List.of(1)))).get(1).variables();

        // then
        assertThat(overview.get("title")).isEqualTo("Test_Trailer MD1");
        assertThat((List<?>) overview.get("pairings")).hasSize(1);
        assertThat(overview.get("pairingsSize")).isEqualTo("rows-few");
    }

    private TrailerPlan plan(TrailerRaceScene... scenes) {
        var row = new MatchdayGraphicData.MatchGraphicRow("Test_Home", "HOM", "logo", "#000", "#111", "#222", 1, "1-0-0",
                "Test_Away", "AWY", "logo", "#000", "#111", "#222", 2, "0-1-0", null, null, null, UUID.randomUUID());
        var overview = new MatchdayGraphicData("Test_Trailer MD1", "Test_Trailer Season", "2027", "data:image/png;base64,AA", "font", List.of(row));
        return new TrailerPlan(new TrailerScope.MatchdayScope(UUID.randomUUID()), "Test_Trailer MD1", "Test_Trailer Season", overview,
                List.of(scenes), List.of(), Set.of());
    }

    private TrailerRaceScene scene(String shortName, List<Integer> legs) {
        return new TrailerRaceScene(UUID.randomUUID(), "HOM vs AWY", "Test_Home", "Test_Away", "logo", "logo", legs, content(),
                trackId, "Circuit de Spa-Francorchamps", shortName, carId, "Nissan GT-R NISMO GT3 '18", "SEED 1 · 4-1-0", "SEED 2 · 3-2-0");
    }

    private PickPublicationContent content() {
        return new PickPublicationContent(trackId, carId, null, "Circuit de Spa-Francorchamps", "Nissan GT-R NISMO GT3 '18", null,
                12, 2, 3, 4, "Default", 1, TimeProgressionMode.MULTIPLIER, 1, "S01", "Afternoon", "RS", "None",
                LobbyRulesProfile.LEGACY, RaceStartType.values()[0], RaceSlipstream.values()[0]);
    }

    private static TrackImage trackImage(String path) {
        var image = new TrackImage();
        image.setImagePath(path);
        return image;
    }
}
