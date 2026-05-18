package org.ctc.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonPhase;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeasonPhaseRepository extends JpaRepository<SeasonPhase, UUID> {

    Optional<SeasonPhase> findBySeasonIdAndPhaseType(UUID seasonId, PhaseType phaseType);

    List<SeasonPhase> findBySeasonIdOrderBySortIndex(UUID seasonId);

    /**
     * Full-table finder used by {@code BackupExportService}.
     *
     * <p>Eager-fetches the three {@code @ManyToOne} associations the backup MixIn
     * renders as ID references: {@code season}, {@code raceScoring}, {@code matchScoring}.
     */
    @EntityGraph(attributePaths = {"season", "raceScoring", "matchScoring"})
    @Query("SELECT e FROM SeasonPhase e")
    List<SeasonPhase> findAllForBackup();
}
