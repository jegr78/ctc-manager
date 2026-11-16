package org.ctc.trailer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import javax.imageio.ImageIO;
import org.ctc.media.MediaTool;
import org.ctc.media.job.MediaJobContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrailerVideoComposerTest {

    private final MediaTool media = new MediaTool("ffmpeg", "ffprobe");
    private final TrailerVideoComposer composer = new TrailerVideoComposer(media);
    @TempDir Path dir;

    @Test
    void givenRenderedFixtureLayers_whenCompose_thenMp4WithExpectedDurationResolutionAndAudio() throws Exception {
        // given
        var length = Duration.ofMillis(1500);
        var layers = List.of(
                new TrailerLayer("intro", 0, "background", png("0-bg.png", Color.DARK_GRAY, false), length),
                new TrailerLayer("intro", 0, "foreground", png("0-fg.png", Color.WHITE, true), length),
                new TrailerLayer("race-visual", 1, "background", png("1-bg.png", new Color(30, 80, 40), false), length),
                new TrailerLayer("race-visual", 1, "car", png("1-car.png", Color.RED, true), length),
                new TrailerLayer("race-visual", 1, "foreground", png("1-fg.png", Color.YELLOW, true), length));
        Path music = dir.resolve("music.mp3");
        media.run(List.of("-f", "lavfi", "-i", "sine=d=1", "-y", music.toString()), Duration.ofSeconds(30));

        // when
        Path video = composer.compose(layers, music, dir.resolve("trailer.mp4"), dir.resolve("work"),
                mock(MediaJobContext.class));

        // then
        var probe = media.probeStreams(video);
        assertThat(probe.width()).isEqualTo(1920);
        assertThat(probe.height()).isEqualTo(1080);
        assertThat(probe.hasAudio()).as("music track mixed in").isTrue();
        assertThat(probe.duration().toMillis() / 1000.0).as("two 1.5 s scenes minus one 0.5 s cross-fade")
                .isCloseTo(2.5, within(0.15));
    }

    private Path png(String name, Color color, boolean transparent) throws Exception {
        var image = new BufferedImage(1920, 1080, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(color);
        if (transparent) g.fillRect(600, 300, 600, 300); else g.fillRect(0, 0, 1920, 1080);
        g.dispose();
        Path file = dir.resolve(name);
        ImageIO.write(image, "png", file.toFile());
        return file;
    }
}
