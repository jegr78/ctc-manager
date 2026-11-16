package org.ctc.trailer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.MusicTrack;
import org.ctc.domain.service.CarCutoutService;
import org.ctc.domain.service.FileStorageService;
import org.ctc.domain.service.MusicLibraryService;
import org.ctc.media.cutout.CutoutModel;
import org.ctc.media.job.MediaJobContext;
import org.ctc.media.job.MediaJobExecutor;
import org.ctc.media.job.MediaJobKind;
import org.ctc.media.job.MediaJobStatus;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Renders a trailer as a background media job. Files are written to a work directory next to the scope's output
 * directory, which replaces the previous trailer only after every step succeeded.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrailerService {

    private static final String MP4 = "trailer.mp4";
    private static final String ZIP = "layers.zip";
    private static final String TEXT = "youtube.txt";
    private static final String META = "trailer.properties";

    private final TrailerPlanService plans;
    private final TrailerSceneRenderer renderer;
    private final TrailerVideoComposer composer;
    private final CarCutoutService cutouts;
    private final CutoutModel cutoutModel;
    private final MusicLibraryService music;
    private final MediaJobExecutor jobs;
    private final FileStorageService files;
    private final Clock clock;

    /** @throws TrailerBlockedException when the checklist has open items */
    public MediaJobStatus start(TrailerScope scope, UUID musicIdOrNull) {
        var plan = plans.plan(scope);
        if (!plan.ready()) throw new TrailerBlockedException(plan.blockers());
        var song = music.pick(musicIdOrNull, RandomGenerator.getDefault())
                .orElseThrow(() -> new BusinessRuleException("The chosen music track no longer exists."));
        return jobs.submit(MediaJobKind.TRAILER, scope.key(), ctx -> run(scope, plan, song, ctx));
    }

    public Optional<MediaJobStatus> status(TrailerScope scope) {
        return jobs.status(scope.key());
    }

    public Optional<TrailerOutputs> latest(TrailerScope scope) {
        Path dir = outputDir(scope);
        Path meta = dir.resolve(META);
        if (!Files.isRegularFile(meta) || !Files.isRegularFile(dir.resolve(MP4))) return Optional.empty();
        var properties = new Properties();
        try (var in = Files.newBufferedReader(meta)) {
            properties.load(in);
            String name = properties.getProperty("name");
            if (name == null || !name.matches("[a-z0-9-]{1,120}")) return Optional.empty();
            return Optional.of(new TrailerOutputs(dir.resolve(MP4), dir.resolve(ZIP), dir.resolve(TEXT),
                    Instant.parse(properties.getProperty("createdAt")), properties.getProperty("music", ""), name));
        } catch (IOException | RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /** Removes work directories a restart interrupted. */
    @EventListener(ApplicationReadyEvent.class)
    public void removeInterruptedWork() {
        for (String kind : new String[] {"matchday", "round"}) {
            try {
                Path parent = files.resolve("/uploads/trailers/" + kind);
                if (parent == null || !Files.isDirectory(parent)) continue;
                try (var entries = Files.list(parent)) {
                    entries.filter(path -> path.getFileName().toString().startsWith(".")).forEach(TrailerService::deleteTree);
                }
            } catch (IOException | RuntimeException e) {
                log.warn("Could not clean interrupted trailer work for {}", kind, e);
            }
        }
    }

    private void run(TrailerScope scope, TrailerPlan initial, MusicTrack song, MediaJobContext ctx) throws IOException {
        var plan = initial;
        if (!plan.carsNeedingCutout().isEmpty()) {
            try {
                int done = 0;
                for (UUID car : plan.carsNeedingCutout()) {
                    ctx.checkNotAbandoned();
                    ctx.progress(++done, plan.carsNeedingCutout().size(), "Creating car cutout " + done + " of "
                            + plan.carsNeedingCutout().size());
                    cutouts.createCutout(car);
                }
            } finally {
                cutoutModel.release();
            }
            ctx.checkNotAbandoned();
            plan = plans.plan(scope);
            if (!plan.ready()) throw new TrailerBlockedException(plan.blockers());
        }
        Path target = outputDir(scope);
        Path work = target.resolveSibling(".work-" + UUID.randomUUID());
        try {
            var layers = renderer.render(plan, work.resolve("layers"), ctx);
            ctx.checkNotAbandoned();
            TrailerSceneRenderer.zipLayers(layers, work.resolve(ZIP));
            composer.compose(layers, music.resolveFile(song), Duration.ofSeconds(song.getDurationSeconds()), work.resolve(MP4),
                    work.resolve("clips"), ctx);
            ctx.checkNotAbandoned();
            Files.writeString(work.resolve(TEXT), YoutubeTextBuilder.build(plan, song).asFile());
            writeMeta(work.resolve(META), song, downloadName(plan));
            deleteTree(work.resolve("layers"));
            deleteTree(work.resolve("clips"));
            replace(target, work);
            ctx.progress(1, 1, "Trailer ready.");
            log.info("Rendered trailer for {}", scope.key());
        } finally {
            deleteTree(work);
        }
    }

    static String downloadName(TrailerPlan plan) {
        String slug = ("ctc-trailer-" + plan.seasonLabel() + "-" + plan.title()).toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
        return slug.length() > 120 ? slug.substring(0, 120).replaceAll("-+$", "") : slug;
    }

    private void writeMeta(Path meta, MusicTrack song, String name) throws IOException {
        var properties = new Properties();
        properties.setProperty("name", name);
        properties.setProperty("createdAt", clock.instant().toString());
        properties.setProperty("music", song.getTitle());
        try (var out = Files.newBufferedWriter(meta)) {
            properties.store(out, null);
        }
    }

    private static void replace(Path target, Path work) throws IOException {
        Path old = target.resolveSibling(".old-" + UUID.randomUUID());
        boolean hadPrevious = Files.exists(target);
        if (hadPrevious) Files.move(target, old, StandardCopyOption.ATOMIC_MOVE);
        try {
            Files.move(work, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            if (hadPrevious) Files.move(old, target, StandardCopyOption.ATOMIC_MOVE);
            throw e;
        }
        deleteTree(old);
    }

    private Path outputDir(TrailerScope scope) {
        return switch (scope) {
            case TrailerScope.MatchdayScope matchday -> files.resolve("/uploads/trailers/matchday/" + matchday.matchdayId());
            case TrailerScope.PlayoffRoundScope round -> files.resolve("/uploads/trailers/round/" + round.roundId());
        };
    }

    private static void deleteTree(Path dir) {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            log.warn("Could not delete trailer work directory {}", dir, e);
        }
    }
}
