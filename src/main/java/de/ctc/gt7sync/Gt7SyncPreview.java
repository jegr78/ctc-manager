package de.ctc.gt7sync;

import lombok.*;

import java.util.List;

@Getter
@AllArgsConstructor
public class Gt7SyncPreview {

    public record CarEntry(String gt7Id, String manufacturer, String name, String imageUrl, SyncStatus status) {}
    public record TrackEntry(String id, String name, String country, SyncStatus status) {}
    public enum SyncStatus { NEW, EXISTS }

    private final List<CarEntry> cars;
    private final List<TrackEntry> tracks;

    public long getNewCarCount() {
        return cars.stream().filter(c -> c.status() == SyncStatus.NEW).count();
    }

    public long getExistingCarCount() {
        return cars.stream().filter(c -> c.status() == SyncStatus.EXISTS).count();
    }

    public long getNewTrackCount() {
        return tracks.stream().filter(t -> t.status() == SyncStatus.NEW).count();
    }

    public long getExistingTrackCount() {
        return tracks.stream().filter(t -> t.status() == SyncStatus.EXISTS).count();
    }
}
