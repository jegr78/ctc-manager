package org.ctc.trailer;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class FfmpegGraphBuilderTest {

    private static final Path DIR = Path.of("/work");

    @Test
    void givenRaceVisualLayers_whenSceneArgs_thenBackgroundZoomsAndOverlaysSlideIn() {
        // given
        var layers = List.of(layer("race-visual", "background"), layer("race-visual", "car"), layer("race-visual", "foreground"));

        // when
        var args = FfmpegGraphBuilder.sceneArgs(layers, DIR.resolve("scene-02.mp4"));

        // then
        String graph = args.get(args.indexOf("-filter_complex") + 1);
        assertThat(graph).contains("zoompan=z='1+0.06*on/180'", "d=180", "s=1920x1080");
        assertThat(graph).as("car enters from the right").contains("overlay=x='max(0,80-80*t/0.4)'");
        assertThat(graph).as("foreground enters from the left").contains("overlay=x='min(0,-80+80*t/0.4)'");
        assertThat(args).containsSubsequence("-t", "6.000").endsWith("/work/scene-02.mp4");
        assertThat(args.stream().filter(arg -> arg.endsWith(".png"))).hasSize(3).allMatch(arg -> arg.startsWith("/"));
    }

    @Test
    void givenTwoScenes_whenFinalArgs_thenXfadeAtBoundaryMinusHalfSecond() {
        // when
        var args = FfmpegGraphBuilder.finalArgs(List.of(DIR.resolve("a.mp4"), DIR.resolve("b.mp4")),
                List.of(Duration.ofSeconds(4), Duration.ofSeconds(8)), DIR.resolve("m.mp3"), Duration.ofSeconds(60), DIR.resolve("out.mp4"));

        // then
        String graph = args.get(args.indexOf("-filter_complex") + 1);
        assertThat(graph).contains("xfade=transition=fade:duration=0.500:offset=3.500");
        assertThat(FfmpegGraphBuilder.totalLength(List.of(Duration.ofSeconds(4), Duration.ofSeconds(8)))).isEqualTo(Duration.ofMillis(11_500));
    }

    @Test
    void givenThreeScenes_whenFinalArgs_thenOffsetsAccumulate() {
        // when
        var args = FfmpegGraphBuilder.finalArgs(List.of(DIR.resolve("a.mp4"), DIR.resolve("b.mp4"), DIR.resolve("c.mp4")),
                List.of(Duration.ofSeconds(4), Duration.ofSeconds(8), Duration.ofSeconds(6)), DIR.resolve("m.mp3"), Duration.ofSeconds(60),
                DIR.resolve("out.mp4"));

        // then
        String graph = args.get(args.indexOf("-filter_complex") + 1);
        assertThat(graph).contains("offset=3.500", "offset=11.000");
    }

    @Test
    void givenMusicShorterThanVideo_whenFinalArgs_thenStreamLoopPresent() {
        // when
        var args = FfmpegGraphBuilder.finalArgs(List.of(DIR.resolve("a.mp4"), DIR.resolve("b.mp4")),
                List.of(Duration.ofSeconds(40), Duration.ofSeconds(40)), DIR.resolve("m.mp3"), Duration.ofSeconds(30), DIR.resolve("out.mp4"));

        // then
        assertThat(args).containsSubsequence("-stream_loop", "-1", "-i", "/work/m.mp3");
    }

    @Test
    void givenMusicLongerThanVideo_whenFinalArgs_thenNoLoopAndFadeOutOverLastTwoSeconds() {
        // when
        var args = FfmpegGraphBuilder.finalArgs(List.of(DIR.resolve("a.mp4"), DIR.resolve("b.mp4")),
                List.of(Duration.ofSeconds(4), Duration.ofSeconds(8)), DIR.resolve("m.mp3"), Duration.ofSeconds(120), DIR.resolve("out.mp4"));

        // then
        assertThat(args).doesNotContain("-stream_loop");
        String graph = args.get(args.indexOf("-filter_complex") + 1);
        assertThat(graph).contains("atrim=0:11.500", "afade=t=out:st=9.500:d=2");
        assertThat(args).containsSubsequence("-c:v", "libx264", "-crf", "20", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "192k",
                "-movflags", "+faststart");
    }

    private static TrailerLayer layer(String scene, String name) {
        return new TrailerLayer(scene, 2, name, DIR.resolve("02-" + scene + "-" + name + ".png"), Duration.ofSeconds(6));
    }
}
