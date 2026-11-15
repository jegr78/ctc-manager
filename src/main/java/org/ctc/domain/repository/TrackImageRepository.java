package org.ctc.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.TrackImage;
import org.ctc.domain.model.TrackImageKind;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TrackImageRepository extends JpaRepository<TrackImage, UUID> {
    List<TrackImage> findByTrackIdOrderBySortIndexAscIdAsc(UUID trackId);

    Optional<TrackImage> findFirstByTrackIdAndKindOrderBySortIndexAscIdAsc(UUID trackId, TrackImageKind kind);

    @EntityGraph(attributePaths = "track")
    @Query("SELECT e FROM TrackImage e")
    List<TrackImage> findAllForBackup();
}
