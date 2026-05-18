package org.ctc.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.SeasonTeam;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface SeasonTeamRepository extends JpaRepository<SeasonTeam, UUID> {

	@EntityGraph(attributePaths = {"team"})
	List<SeasonTeam> findBySeasonId(UUID seasonId);

	Optional<SeasonTeam> findBySeasonIdAndTeamId(UUID seasonId, UUID teamId);

	@Transactional
	void deleteBySeasonIdAndTeamId(UUID seasonId, UUID teamId);

	/**
	 * Full-table finder used by {@code BackupExportService}.
	 *
	 * <p>Eager-fetches the three {@code @ManyToOne} associations: {@code season},
	 * {@code team}, {@code successor} (the self-FK to another SeasonTeam).
	 */
	@EntityGraph(attributePaths = {"season", "team", "successor"})
	@Query("SELECT e FROM SeasonTeam e")
	List<SeasonTeam> findAllForBackup();
}
