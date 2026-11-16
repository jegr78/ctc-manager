package org.ctc.trailer;

import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.ctc.domain.model.MusicTrack;
import org.ctc.media.job.MediaJobStatus;

/** Everything the trailer section of a matchday or playoff round shows. */
public record TrailerView(String basePath, boolean hasRaces, List<TrailerChecklistItem> blockers, List<MusicTrack> music, Optional<MediaJobStatus> status,
                          Optional<TrailerOutputs> latest, Duration estimatedLength, boolean startEnabled) {

    public TrailerView {
        blockers = List.copyOf(blockers);
        music = List.copyOf(music);
    }

    public boolean running() {
        return status.map(MediaJobStatus::running).orElse(false);
    }

    public String statusLabel() {
        return status.map(s -> switch (s.state()) {
            case FAILED -> "Rendering failed: " + s.message();
            default -> s.message();
        }).orElse(null);
    }

    public String latestLabel() {
        return latest.map(outputs -> DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
                .format(outputs.createdAt().atZone(ZoneId.of("Europe/Berlin"))) + " · " + outputs.musicTitle()).orElse(null);
    }

    public String lengthLabel() {
        return "%d:%02d".formatted(estimatedLength.toMinutes(), estimatedLength.toSecondsPart());
    }
}
