package org.ctc.trailer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.ctc.media.MediaTool;
import org.ctc.media.job.MediaJobContext;
import org.springframework.stereotype.Component;

/** Encodes one clip per scene and joins them with cross-fades and the music into the final MP4. */
@Component
@RequiredArgsConstructor
public class TrailerVideoComposer {

    private static final Duration SCENE_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration FINAL_TIMEOUT = Duration.ofMinutes(10);

    private final MediaTool media;

    public Path compose(List<TrailerLayer> layers, Path music, Duration musicLength, Path out, Path workDir, MediaJobContext ctx)
            throws IOException {
        Files.createDirectories(workDir);
        Map<Integer, List<TrailerLayer>> scenes = new TreeMap<>();
        for (var layer : layers) scenes.computeIfAbsent(layer.sceneIndex(), index -> new ArrayList<>()).add(layer);
        var clips = new ArrayList<Path>();
        var lengths = new ArrayList<Duration>();
        int done = 0;
        for (var scene : scenes.values()) {
            ctx.checkNotAbandoned();
            ctx.progress(++done, scenes.size() + 1, "Encoding scene " + done + " of " + scenes.size());
            Path clip = workDir.resolve("clip-%02d.mp4".formatted(scene.getFirst().sceneIndex()));
            media.run(FfmpegGraphBuilder.sceneArgs(scene, clip), SCENE_TIMEOUT);
            clips.add(clip);
            lengths.add(scene.getFirst().sceneLength());
        }
        ctx.checkNotAbandoned();
        ctx.progress(scenes.size() + 1, scenes.size() + 1, "Joining scenes and music");
        media.run(FfmpegGraphBuilder.finalArgs(clips, lengths, music, musicLength, out), FINAL_TIMEOUT);
        return out;
    }
}
