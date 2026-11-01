package org.ctc.gt7sync;

import java.util.List;
import java.util.UUID;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.ctc.domain.service.CarMetadataService.Proposal;

public record Gt7SyncPreview(List<CarEntry> cars, List<TrackEntry> tracks, UUID previewId, List<String> warnings) {
    public Gt7SyncPreview(List<CarEntry> cars, List<TrackEntry> tracks) { this(cars, tracks, null, List.of()); }
    public long getNewCarCount() { return cars.stream().filter(c -> c.status() == SyncStatus.NEW).count(); }
    public long getExistingCarCount() { return cars.stream().filter(c -> c.status() != SyncStatus.NEW).count(); }
    public long getMetadataUpdateCount() { return cars.stream().filter(CarEntry::metadataSelectable).count(); }
    public long getNewTrackCount() { return tracks.stream().filter(t -> t.status() == SyncStatus.NEW).count(); }
    public long getExistingTrackCount() { return tracks.stream().filter(t -> t.status() == SyncStatus.EXISTS).count(); }
    public enum SyncStatus {
        NEW("New"), EXISTS("Already imported"), UPDATE("Metadata update"), REVIEW("Metadata review required"), AMBIGUOUS("Identity review required");
        private final String label;
        SyncStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
    public record CarEntry(String gt7Id, String manufacturer, String name, String imageUrl, SyncStatus status,
                           Proposal metadata, UUID existingCarId, boolean metadataSelectable, String message) {
        public CarEntry(String gt7Id, String manufacturer, String name, String imageUrl, SyncStatus status) {
            this(gt7Id, manufacturer, name, imageUrl, status,
                    new Proposal(null, null, null, null, null, null, null, null, null, List.of()), null, false, "");
        }
        public String getCategoryLabel() { return metadata.category() == null ? "Unknown" : metadata.category().getLabel(); }
        public String getDrivetrainLabel() { return metadata.drivetrain() == null ? "Unknown" : metadata.drivetrain().getLabel(); }
        public String getAcceptedYearLabel() { return metadata.modelYear() == null ? "Unknown" : metadata.modelYear().toString(); }
        public String getProposedYearLabel() { return metadata.proposedModelYear() == null ? "None" : metadata.proposedModelYear().toString(); }
        public String getProvenanceLabel() {
            return Stream.of(metadata.categorySource(), metadata.drivetrainSource(), metadata.modelYearSource(),
                    metadata.modelYearEvidence(), metadata.proposedModelYearEvidence()).filter(Objects::nonNull).collect(Collectors.joining(" | "));
        }
    }
    public record TrackEntry(String id, String name, String country, SyncStatus status) {}
}
