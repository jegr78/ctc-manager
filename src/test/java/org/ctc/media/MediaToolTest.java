package org.ctc.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaToolTest {

    private final MediaTool tool = new MediaTool("ffmpeg", "ffprobe");

    @TempDir
    Path dir;

    @Test
    void givenGeneratedSineMp3_whenProbeDuration_thenReturnsThreeSeconds() {
        // given
        Path mp3 = dir.resolve("sine.mp3");
        tool.run(List.of("-f", "lavfi", "-i", "sine=d=3", "-y", mp3.toString()), Duration.ofSeconds(30));

        // when
        Duration duration = tool.probeDuration(mp3);

        // then
        assertThat(duration.toMillis() / 1000.0).as("probed MP3 length").isCloseTo(3.0, within(0.1));
    }

    @Test
    void givenUnknownOption_whenRun_thenThrowsWithStderr() {
        // when / then
        assertThatThrownBy(() -> tool.run(List.of("-definitely-not-an-option"), Duration.ofSeconds(10)))
                .isInstanceOf(MediaToolException.class)
                .hasMessageContaining("definitely-not-an-option");
    }

    @Test
    void givenSlowCommand_whenTimeoutExceeded_thenProcessDestroyedAndThrows() {
        // given
        Path out = dir.resolve("long.wav");

        // when / then
        assertThatThrownBy(() -> tool.run(
                        List.of("-re", "-f", "lavfi", "-i", "sine=d=30", "-y", out.toString()), Duration.ofMillis(500)))
                .isInstanceOf(MediaToolException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void givenTwoSecond1080pClip_whenProbeStreams_thenReportsSizeAndAudio() throws Exception {
        // given
        Path mp4 = dir.resolve("clip.mp4");
        tool.run(List.of(
                "-f", "lavfi", "-i", "color=c=black:s=1920x1080:d=2:r=30",
                "-f", "lavfi", "-i", "sine=d=2",
                "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", "-y", mp4.toString()),
                Duration.ofSeconds(60));
        assertThat(Files.size(mp4)).isPositive();

        // when
        MediaTool.ProbeResult result = tool.probeStreams(mp4);

        // then
        assertThat(result.width()).isEqualTo(1920);
        assertThat(result.height()).isEqualTo(1080);
        assertThat(result.hasAudio()).as("audio stream present").isTrue();
        assertThat(result.duration().toMillis() / 1000.0).isCloseTo(2.0, within(0.1));
    }
}
