package org.ctc.gt7sync;

import java.util.List;
public record Gt7SyncPreview(List<CarEntry> cars, List<TrackEntry> tracks) {

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

	public enum SyncStatus {NEW, EXISTS}

	public record CarEntry(String gt7Id, String manufacturer, String name, String imageUrl, SyncStatus status) {
	}

	public record TrackEntry(String id, String name, String country, SyncStatus status) {
	}
}
