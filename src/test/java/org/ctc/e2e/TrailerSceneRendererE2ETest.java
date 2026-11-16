package org.ctc.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import org.ctc.admin.dto.MatchdayGraphicData;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.service.CatalogImageService;
import org.ctc.domain.service.FileStorageService;
import org.ctc.media.job.MediaJobContext;
import org.ctc.trailer.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@Tag("e2e")
class TrailerSceneRendererE2ETest {

    final CatalogImageService gallery = mock(CatalogImageService.class);
    final TrackRepository tracks = mock(TrackRepository.class);
    final FileStorageService files = mock(FileStorageService.class);
    final MediaJobContext ctx = mock(MediaJobContext.class);
    TrailerSceneRenderer renderer;
    @TempDir Path dir;
    final UUID trackId = UUID.randomUUID();
    final UUID carId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        renderer = new TrailerSceneRenderer(engine, new TrailerSceneModel(gallery, tracks, files));
        Path background = image(dir.resolve("bg.jpg"), "jpg", new Color(60, 90, 50), false);
        Path cutout = image(dir.resolve("cut.png"), "png", new Color(200, 20, 30), true);
        Path logo = image(dir.resolve("logo.png"), "png", Color.WHITE, true);
        when(files.resolve(anyString())).thenAnswer(inv -> switch (inv.getArgument(0, String.class)) {
            case "/uploads/bg.jpg" -> background;
            case "/uploads/cut.png" -> cutout;
            default -> logo;
        });
        var track = new Track("Michelin Raceway Road Atlanta");
        track.setId(trackId);
        track.setImageUrl("/uploads/logo.png");
        when(tracks.findById(trackId)).thenReturn(Optional.of(track));
        when(gallery.firstTrackImage(trackId, TrackImageKind.BACKGROUND)).thenReturn(Optional.of(trackImage("/uploads/bg.jpg")));
        when(gallery.firstTrackImage(trackId, TrackImageKind.LAYOUT)).thenReturn(Optional.of(trackImage("/uploads/logo.png")));
        var car = new CarImage();
        car.setImagePath("/uploads/cut.png");
        when(gallery.firstCarImage(any(), any())).thenReturn(Optional.of(car));
    }

    @Test
    void givenPlanWithOneRace_whenRender_thenEveryLayerIsA1920x1080Png() throws Exception {
        // when
        var layers = renderer.render(plan("Test_Home", "Y".repeat(10)), dir.resolve("work"), ctx);

        // then
        assertThat(layers).hasSize(2 + 2 + 3 + 2 + 2 + 2);
        for (var layer : layers) {
            var image = ImageIO.read(layer.png().toFile());
            assertThat(image.getWidth()).as(layer.png().getFileName().toString()).isEqualTo(1920);
            assertThat(image.getHeight()).isEqualTo(1080);
        }
    }

    @Test
    void givenRaceVisual_whenRender_thenCarLayerHasTransparentCornersAndOpaqueCar() throws Exception {
        // when
        var layers = renderer.render(plan("Test_Home", "Car"), dir.resolve("work"), ctx);

        // then
        var car = ImageIO.read(layers.stream().filter(l -> l.name().equals("car")).findFirst().orElseThrow().png().toFile());
        assertThat(car.getRGB(0, 0) >>> 24).as("top-left corner transparent").isZero();
        assertThat(car.getRGB(1919, 0) >>> 24).as("top-right corner transparent").isZero();
        assertThat(car.getRGB(1500, 800) >>> 24).as("car area opaque").isGreaterThan(200);
    }

    @Test
    void givenLongNames_whenRender_thenTextStaysInsideTheFrameMargins() throws Exception {
        // when
        var layers = renderer.render(plan("Test_" + "X".repeat(55), "Y".repeat(45)), dir.resolve("work"), ctx);

        // then
        for (var layer : layers.stream().filter(l -> l.name().equals("foreground") && l.scene().startsWith("race")).toList()) {
            var image = ImageIO.read(layer.png().toFile());
            for (int x = 1880; x < 1920; x++) {
                for (int y = 0; y < 1080; y++) {
                    assertThat(image.getRGB(x, y) >>> 24).as("%s pixel %d,%d outside the right margin", layer.scene(), x, y).isZero();
                }
            }
        }
    }

    @Test
    void givenLayers_whenZip_thenEntriesNamedBySceneIndexAndLayer() throws Exception {
        // given
        var layers = renderer.render(plan("Test_Home", "Car"), dir.resolve("work"), ctx);

        // when
        var zip = TrailerSceneRenderer.zipLayers(layers, dir.resolve("layers.zip"));

        // then
        try (var file = new ZipFile(zip.toFile())) {
            assertThat(file.stream().map(e -> e.getName()).toList()).contains("00-intro-background.png", "02-race-visual-car.png", "05-outro-foreground.png");
        }
    }

    private TrailerPlan plan(String homeTeam, String carName) {
        var row = new MatchdayGraphicData.MatchGraphicRow(homeTeam, "HOM", null, "#000", "#111", "#222", 1, "1-0-0",
                "Test_Away", "AWY", null, "#000", "#111", "#222", 2, "0-1-0", null, null, null, UUID.randomUUID());
        var overview = new MatchdayGraphicData("Test_Trailer MD1", "Test_Trailer Season", "2027", null, null, List.of(row));
        var content = new PickPublicationContent(trackId, carId, null, "Michelin Raceway Road Atlanta", carName, null,
                12, 2, 3, 4, "Default", 1, TimeProgressionMode.MULTIPLIER, 1, "S01, S02, S03, S04, S05, S06, S07, S08", "Afternoon",
                "RS, RM, RH, IM, W", "RM", LobbyRulesProfile.LEGACY, RaceStartType.values()[0], RaceSlipstream.values()[0]);
        var scene = new TrailerRaceScene(UUID.randomUUID(), "HOM vs AWY", homeTeam, "Test_Away", null, null, List.of(1), content,
                trackId, "Michelin Raceway Road Atlanta", null, carId, carName, "SEED 1 · 4-1-0", "SEED 2 · 3-2-0");
        return new TrailerPlan(new TrailerScope.MatchdayScope(UUID.randomUUID()), "Test_Trailer MD1", "Test_Trailer Season",
                overview, List.of(scene), List.of(), Set.of());
    }

    private static Path image(Path target, String format, Color color, boolean alpha) throws Exception {
        var image = new BufferedImage(800, 400, alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(color);
        if (alpha) g.fillRoundRect(100, 100, 600, 200, 60, 60); else g.fillRect(0, 0, 800, 400);
        g.dispose();
        ImageIO.write(image, format, target.toFile());
        Files.size(target);
        return target;
    }

    private static TrackImage trackImage(String path) {
        var image = new TrackImage();
        image.setImagePath(path);
        return image;
    }
}
