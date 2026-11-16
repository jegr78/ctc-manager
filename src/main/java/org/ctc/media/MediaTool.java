package org.ctc.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.ctc.util.LogSanitizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Runs ffmpeg and ffprobe as child processes with argument lists, never through a shell. */
@Component
public class MediaTool {

    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(30);
    private static final int STDERR_LINES = 20;

    private final String ffmpeg;
    private final String ffprobe;
    private final ObjectMapper json = new ObjectMapper();

    public MediaTool(@Value("${app.media.ffmpeg-path:ffmpeg}") String ffmpeg,
                     @Value("${app.media.ffprobe-path:ffprobe}") String ffprobe) {
        this.ffmpeg = ffmpeg;
        this.ffprobe = ffprobe;
    }

    public record ProbeResult(Duration duration, int width, int height, boolean hasAudio) {
    }

    public void run(List<String> ffmpegArgs, Duration timeout) {
        List<String> command = new ArrayList<>(List.of(ffmpeg, "-hide_banner", "-nostdin"));
        command.addAll(ffmpegArgs);
        execute(command, timeout);
    }

    public Duration probeDuration(Path file) {
        return probeStreams(file).duration();
    }

    public ProbeResult probeStreams(Path file) {
        String output = execute(List.of(ffprobe, "-v", "error",
                "-show_entries", "format=duration:stream=codec_type,width,height",
                "-of", "json", file.toAbsolutePath().toString()), PROBE_TIMEOUT);
        try {
            JsonNode root = json.readTree(output);
            double seconds = root.path("format").path("duration").asDouble(0);
            int width = 0;
            int height = 0;
            boolean audio = false;
            for (JsonNode stream : root.path("streams")) {
                String type = stream.path("codec_type").asText();
                if ("video".equals(type) && width == 0) {
                    width = stream.path("width").asInt();
                    height = stream.path("height").asInt();
                } else if ("audio".equals(type)) {
                    audio = true;
                }
            }
            if (seconds <= 0) {
                throw new MediaToolException("ffprobe reported no duration");
            }
            return new ProbeResult(Duration.ofMillis(Math.round(seconds * 1000)), width, height, audio);
        } catch (IOException e) {
            throw new MediaToolException("ffprobe output could not be read", e);
        }
    }

    private String execute(List<String> command, Duration timeout) {
        Process process;
        try {
            process = new ProcessBuilder(command).start();
            process.getOutputStream().close();
        } catch (IOException e) {
            throw new MediaToolException(command.getFirst() + " could not be started", e);
        }
        StreamCollector stdout = new StreamCollector(process.getInputStream(), false);
        StreamCollector stderr = new StreamCollector(process.getErrorStream(), true);
        stdout.start();
        stderr.start();
        try {
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                throw new MediaToolException(command.getFirst() + " timed out after " + timeout.toSeconds() + " s");
            }
            stdout.join();
            stderr.join();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new MediaToolException(command.getFirst() + " was interrupted", e);
        }
        if (process.exitValue() != 0) {
            throw new MediaToolException(command.getFirst() + " failed with exit code " + process.exitValue()
                    + ": " + LogSanitizer.sanitize(stderr.text()));
        }
        return stdout.text();
    }

    private static final class StreamCollector extends Thread {

        private final InputStream in;
        private final boolean tailOnly;
        private final ByteArrayOutputStream all = new ByteArrayOutputStream();
        private final Deque<String> tail = new ArrayDeque<>();

        StreamCollector(InputStream in, boolean tailOnly) {
            this.in = in;
            this.tailOnly = tailOnly;
            setDaemon(true);
        }

        @Override
        public void run() {
            try (in) {
                if (!tailOnly) {
                    in.transferTo(all);
                    return;
                }
                StringBuilder line = new StringBuilder();
                int b;
                while ((b = in.read()) != -1) {
                    if (b == '\n' || b == '\r') {
                        push(line);
                    } else {
                        line.append((char) b);
                    }
                }
                push(line);
            } catch (IOException ignored) {
                // The process ended; whatever was read is reported.
            }
        }

        private void push(StringBuilder line) {
            if (line.isEmpty()) {
                return;
            }
            synchronized (tail) {
                tail.addLast(line.toString());
                if (tail.size() > STDERR_LINES) {
                    tail.removeFirst();
                }
            }
            line.setLength(0);
        }

        String text() {
            if (!tailOnly) {
                return all.toString(StandardCharsets.UTF_8);
            }
            synchronized (tail) {
                return String.join("\n", tail);
            }
        }
    }
}
