package org.ctc.domain.repository;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.MusicTrack;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MusicTrackRepository extends JpaRepository<MusicTrack, UUID> {
    List<MusicTrack> findAllByOrderByTitleAscIdAsc();

    @EntityGraph(attributePaths = {})
    @Query("SELECT e FROM MusicTrack e")
    List<MusicTrack> findAllForBackup();
}
