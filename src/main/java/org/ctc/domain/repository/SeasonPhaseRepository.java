package org.ctc.domain.repository;

import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonPhase;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonPhaseRepository extends JpaRepository<SeasonPhase, UUID> {

    Optional<SeasonPhase> findBySeasonIdAndPhaseType(UUID seasonId, PhaseType phaseType);

    List<SeasonPhase> findBySeasonIdOrderBySortIndex(UUID seasonId);

    /**
     * Phase 73-02: full-table finder used by {@code BackupExportService}.
     *
     * <p>Eager-fetches the three {@code @ManyToOne} associations the Phase 73-01 MixIn
     * renders as ID references: {@code season}, {@code raceScoring}, {@code matchScoring}.
     */
    @EntityGraph(attributePaths = {"season", "raceScoring", "matchScoring"})
    @Query("SELECT e FROM SeasonPhase e")
    List<SeasonPhase> findAllForBackup();
}
