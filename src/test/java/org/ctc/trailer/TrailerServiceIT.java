package org.ctc.trailer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.ctc.admin.dto.MatchdayGraphicData;
import org.ctc.domain.model.MusicTrack;
import org.ctc.domain.service.MusicLibraryService;
import org.ctc.domain.service.PickSessionEpoch;
import org.ctc.media.MediaTool;
import org.ctc.media.job.MediaJobContext;
import org.ctc.media.job.MediaJobExecutor;
import org.ctc.media.job.MediaJobState;
import org.ctc.media.job.MediaJobStatus;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@CtcDevSpringBootContext
@Tag("integration")
class TrailerServiceIT {
    @Autowired TrailerService trailers;
    @Autowired MusicLibraryService music;
    @Autowired MediaJobExecutor jobs;
    @Autowired MediaTool media;
    @Autowired PickSessionEpoch epoch;
    @Autowired org.ctc.domain.service.FileStorageService files;
    @MockitoBean TrailerPlanService plans;
    @MockitoBean TrailerSceneRenderer renderer;

    private final TrailerScope scope = new TrailerScope.MatchdayScope(UUID.randomUUID());
    private MusicTrack song;

    @BeforeEach
    void setUp() throws Exception {
        Path mp3 = Files.createTempFile("trailer-music", ".mp3");
        media.run(List.of("-f", "lavfi", "-i", "sine=d=2", "-c:a", "libmp3lame", "-y", mp3.toString()), Duration.ofSeconds(30));
        song = music.upload(new MockMultipartFile("file", "song.mp3", "audio/mpeg", Files.readAllBytes(mp3)), "Test_Trailer Song",
                "Test Artist", null);
        Files.delete(mp3);
        when(plans.plan(scope)).thenReturn(plan(List.of()));
    }

    @AfterEach
    void cleanUp() throws Exception {
        music.delete(song.getId());
        trailers.latest(scope).ifPresent(outputs -> deleteTree(outputs.mp4().getParent()));
    }

    @Test
    void givenReadyMatchday_whenStartAndAwait_thenOutputsExistAndLatestReturnsThem() throws Exception {
        // given
        rendersLayers();

        // when
        var status = await(trailers.start(scope, song.getId()));

        // then
        assertThat(status.state()).isEqualTo(MediaJobState.DONE);
        var outputs = trailers.latest(scope).orElseThrow();
        assertThat(media.probeStreams(outputs.mp4()).hasAudio()).isTrue();
        assertThat(outputs.zip()).isRegularFile();
        assertThat(Files.readString(outputs.youtubeText())).contains("Music: Test_Trailer Song by Test Artist");
        assertThat(outputs.musicTitle()).isEqualTo("Test_Trailer Song");
        assertThat(outputs.mp4().getParent().getParent()).as("no work directory left behind")
                .isDirectoryNotContaining(path -> path.getFileName().toString().startsWith("."));
    }

    @Test
    void givenPreviousTrailer_whenNewJobFails_thenPreviousOutputsKept() throws Exception {
        // given
        rendersLayers();
        await(trailers.start(scope, song.getId()));
        var previous = trailers.latest(scope).orElseThrow();
        doThrow(new java.io.IOException("Renderer broke")).when(renderer).render(any(), any(), any());

        // when
        var status = await(trailers.start(scope, song.getId()));

        // then
        assertThat(status.state()).isEqualTo(MediaJobState.FAILED);
        assertThat(status.message()).contains("Renderer broke");
        assertThat(trailers.latest(scope)).hasValueSatisfying(kept -> assertThat(kept.createdAt()).isEqualTo(previous.createdAt()));
        assertThat(previous.mp4()).isRegularFile();
    }

    @Test
    void givenRestoreEventDuringRender_whenJobEnds_thenAbandonedAndWorkDirDeletedAndPreviousKept() throws Exception {
        // given
        rendersLayers();
        await(trailers.start(scope, song.getId()));
        var previous = trailers.latest(scope).orElseThrow();
        var workDirs = new ArrayList<Path>();
        doAnswer(call -> {
            workDirs.add(call.getArgument(1));
            epoch.afterRestore(null);
            call.<MediaJobContext>getArgument(2).checkNotAbandoned();
            return List.of();
        }).when(renderer).render(any(), any(), any());

        // when
        var status = await(trailers.start(scope, song.getId()));

        // then
        assertThat(status.state()).isEqualTo(MediaJobState.ABANDONED);
        assertThat(workDirs).singleElement().satisfies(dir -> {
            assertThat(dir).doesNotExist();
            assertThat(dir.toAbsolutePath().startsWith(files.resolve("/uploads/trailers")))
                    .as("work happens outside the upload tree a restore swaps").isFalse();
        });
        assertThat(trailers.latest(scope)).hasValueSatisfying(kept -> assertThat(kept.createdAt()).isEqualTo(previous.createdAt()));
    }

    @Test
    void givenBlockers_whenStart_thenTrailerBlockedException() {
        // given
        var blocker = new TrailerChecklistItem("No races to show", null);
        when(plans.plan(scope)).thenReturn(plan(List.of(blocker)));

        // when / then
        assertThatThrownBy(() -> trailers.start(scope, song.getId()))
                .isInstanceOfSatisfying(TrailerBlockedException.class, e -> assertThat(e.items()).containsExactly(blocker));
        assertThat(trailers.status(scope)).isEmpty();
    }

    @Test
    void givenReadyMatchday_whenStartAndAwait_thenPeakMemoryRecorded() throws Exception {
        // given
        rendersLayers();

        // when
        await(trailers.start(scope, song.getId()));

        // then
        String peak = peakMemory();
        Path report = Path.of("target/test-perf/trailer-memory.txt");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "JVM peak during trailer job: " + peak + "\n");
        assertThat(report).content().contains("JVM peak");
    }

    private void rendersLayers() throws Exception {
        when(renderer.render(any(), any(), any())).thenAnswer(call -> {
            Path dir = call.getArgument(1);
            Files.createDirectories(dir);
            var length = Duration.ofSeconds(1);
            return List.of(
                    new TrailerLayer("intro", 0, "background", png(dir.resolve("00-intro-background.png"), false), length),
                    new TrailerLayer("intro", 0, "foreground", png(dir.resolve("00-intro-foreground.png"), true), length),
                    new TrailerLayer("outro", 1, "background", png(dir.resolve("01-outro-background.png"), false), length),
                    new TrailerLayer("outro", 1, "foreground", png(dir.resolve("01-outro-foreground.png"), true), length));
        });
    }

    private static Path png(Path file, boolean transparent) throws Exception {
        var image = new BufferedImage(1920, 1080, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(transparent ? Color.WHITE : Color.DARK_GRAY);
        if (transparent) g.fillRect(400, 400, 400, 200); else g.fillRect(0, 0, 1920, 1080);
        g.dispose();
        ImageIO.write(image, "png", file.toFile());
        return file;
    }

    private TrailerPlan plan(List<TrailerChecklistItem> blockers) {
        var overview = new MatchdayGraphicData("Test_Trailer MD1", "Test-Season 2026", "2026", null, null, List.of());
        return new TrailerPlan(scope, "Test_Trailer MD1", "Test-Season 2026", overview, List.of(), blockers, Set.of());
    }

    private MediaJobStatus await(MediaJobStatus started) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
        while (System.nanoTime() < deadline) {
            var status = jobs.status(started.scopeKey()).orElseThrow();
            if (status.jobId().equals(started.jobId()) && !status.running()) return status;
            Thread.sleep(50);
        }
        throw new AssertionError("trailer job did not finish within two minutes");
    }

    private static String peakMemory() throws Exception {
        Path status = Path.of("/proc/self/status");
        if (Files.isReadable(status)) {
            return Files.readAllLines(status).stream().filter(line -> line.startsWith("VmHWM")).findFirst().orElse("VmHWM unknown");
        }
        var runtime = Runtime.getRuntime();
        return "heap used " + (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024) + " MB (no /proc on this platform)";
    }

    private static void deleteTree(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (java.io.IOException ignored) {
            // best-effort test cleanup
        }
    }
}
