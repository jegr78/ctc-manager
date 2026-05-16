package org.ctc.domain.repository;

import org.ctc.domain.model.SeasonPhaseGroup;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SeasonPhaseGroupRepository extends JpaRepository<SeasonPhaseGroup, UUID> {

    List<SeasonPhaseGroup> findByPhaseIdOrderBySortIndex(UUID phaseId);

    /**
     * Full-table finder used by {@code BackupExportService}.
     *
     * <p>Eager-fetches the {@code phase} {@code @ManyToOne} association.
     */
    @EntityGraph(attributePaths = {"phase"})
    @Query("SELECT e FROM SeasonPhaseGroup e")
    List<SeasonPhaseGroup> findAllForBackup();
}
