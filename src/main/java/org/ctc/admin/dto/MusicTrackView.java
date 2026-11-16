package org.ctc.admin.dto;

import java.util.UUID;
import org.ctc.domain.model.MusicTrack;

public record MusicTrackView(UUID id, String title, String artist, String credit, String duration) {

    public static MusicTrackView of(MusicTrack track) {
        int seconds = track.getDurationSeconds();
        return new MusicTrackView(track.getId(), track.getTitle(), track.getArtist(), track.getCredit(),
                "%d:%02d".formatted(seconds / 60, seconds % 60));
    }
}
