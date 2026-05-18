package org.ctc.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.PhaseTeam;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PhaseTeamRepository extends JpaRepository<PhaseTeam, UUID> {

    List<PhaseTeam> findByPhaseId(UUID phaseId);

    List<PhaseTeam> findByPhaseIdAndGroupId(UUID phaseId, UUID groupId);

    Optional<PhaseTeam> findByPhaseIdAndTeamId(UUID phaseId, UUID teamId);

    boolean existsByPhaseSeasonId(UUID seasonId);

    /**
     * Full-table finder used by {@code BackupExportService}.
     *
     * <p>Eager-fetches the three {@code @ManyToOne} associations: {@code phase},
     * {@code team}, {@code group}.
     */
    @EntityGraph(attributePaths = {"phase", "team", "group"})
    @Query("SELECT e FROM PhaseTeam e")
    List<PhaseTeam> findAllForBackup();
}
