package org.ctc.trailer;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Builds the ffmpeg argument lists for one scene clip and for the final trailer; pure functions without I/O. */
public final class FfmpegGraphBuilder {

    static final int FPS = 30;
    static final double CROSSFADE = 0.5;
    private static final double SLIDE = 0.4;
    private static final double FADE_OUT = 2.0;

    private FfmpegGraphBuilder() {
    }

    /** One scene: a slow zoom on the background, the car sliding in from the right and the foreground from the left. */
    public static List<String> sceneArgs(List<TrailerLayer> layers, Path out) {
        double seconds = layers.getFirst().sceneLength().toMillis() / 1000.0;
        int frames = (int) Math.round(seconds * FPS);
        var args = new ArrayList<String>();
        var graph = new StringBuilder();
        String current = null;
        for (int i = 0; i < layers.size(); i++) {
            var layer = layers.get(i);
            if ("background".equals(layer.name())) {
                args.addAll(List.of("-i", layer.png().toAbsolutePath().toString()));
                graph.append("[").append(i).append(":v]scale=3840:-2,zoompan=z='1+0.06*on/").append(frames)
                        .append("':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=").append(frames)
                        .append(":s=1920x1080:fps=").append(FPS).append(",format=rgba[l").append(i).append("];");
            } else {
                args.addAll(List.of("-loop", "1", "-framerate", String.valueOf(FPS), "-t", seconds(seconds), "-i",
                        layer.png().toAbsolutePath().toString()));
                String x = "car".equals(layer.name()) ? "max(0,80-80*t/" + SLIDE + ")" : "min(0,-80+80*t/" + SLIDE + ")";
                graph.append("[").append(i).append(":v]format=rgba,fade=t=in:st=0:d=").append(SLIDE).append(":alpha=1[o").append(i).append("];")
                        .append("[").append(current).append("][o").append(i).append("]overlay=x='").append(x).append("':y=0[l").append(i).append("];");
            }
            current = "l" + i;
        }
        graph.append("[").append(current).append("]format=yuv420p[v]");
        args.addAll(List.of("-filter_complex", graph.toString(), "-map", "[v]", "-t", seconds(seconds), "-r", String.valueOf(FPS),
                "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-an", "-y", out.toAbsolutePath().toString()));
        return List.copyOf(args);
    }

    /** All scene clips joined by cross-fades, with the music looped, cut to the video and faded out at the end. */
    public static List<String> finalArgs(List<Path> clips, List<Duration> lengths, Path music, Path out) {
        Duration total = totalLength(lengths);
        double totalSeconds = total.toMillis() / 1000.0;
        var args = new ArrayList<String>();
        for (var clip : clips) args.addAll(List.of("-i", clip.toAbsolutePath().toString()));
        args.addAll(List.of("-stream_loop", "-1"));
        args.addAll(List.of("-i", music.toAbsolutePath().toString()));
        var graph = new StringBuilder();
        if (clips.size() == 1) {
            graph.append("[0:v]null[v];");
        } else {
            double elapsed = 0;
            String previous = "0:v";
            for (int i = 1; i < clips.size(); i++) {
                elapsed += lengths.get(i - 1).toMillis() / 1000.0;
                String label = i == clips.size() - 1 ? "v" : "x" + i;
                graph.append("[").append(previous).append("][").append(i).append(":v]xfade=transition=fade:duration=")
                        .append(seconds(CROSSFADE)).append(":offset=").append(seconds(elapsed - i * CROSSFADE)).append("[").append(label).append("];");
                previous = label;
            }
        }
        double fade = Math.min(FADE_OUT, totalSeconds / 2);
        graph.append("[").append(clips.size()).append(":a]atrim=0:").append(seconds(totalSeconds))
                .append(",asetpts=PTS-STARTPTS,afade=t=out:st=").append(seconds(totalSeconds - fade)).append(":d=")
                .append(seconds(fade)).append("[a]");
        args.addAll(List.of("-filter_complex", graph.toString(), "-map", "[v]", "-map", "[a]", "-t", seconds(totalSeconds),
                "-r", String.valueOf(FPS), "-c:v", "libx264", "-preset", "medium", "-crf", "20", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "192k", "-movflags", "+faststart", "-y", out.toAbsolutePath().toString()));
        return List.copyOf(args);
    }

    public static Duration totalLength(List<Duration> lengths) {
        long millis = lengths.stream().mapToLong(Duration::toMillis).sum() - Math.round(CROSSFADE * 1000) * Math.max(0, lengths.size() - 1);
        return Duration.ofMillis(millis);
    }

    private static String seconds(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
