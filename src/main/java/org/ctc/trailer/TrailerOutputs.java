package org.ctc.trailer;

import java.nio.file.Path;
import java.time.Instant;

public record TrailerOutputs(Path mp4, Path zip, Path youtubeText, Instant createdAt, String musicTitle) {
}
