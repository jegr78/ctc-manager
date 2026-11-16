package org.ctc.trailer;

import java.nio.file.Path;
import java.time.Instant;

/** The latest trailer of a scope; {@code downloadName} is the file name stem for downloads. */
public record TrailerOutputs(Path mp4, Path zip, Path youtubeText, Instant createdAt, String musicTitle, String downloadName) {
}
