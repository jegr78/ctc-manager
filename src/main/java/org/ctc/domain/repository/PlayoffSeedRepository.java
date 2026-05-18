package org.ctc.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.PlayoffSeed;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlayoffSeedRepository extends JpaRepository<PlayoffSeed, UUID> {

	@EntityGraph(attributePaths = {"team"})
	List<PlayoffSeed> findByPlayoffId(UUID playoffId);

	Optional<PlayoffSeed> findByPlayoffIdAndTeamId(UUID playoffId, UUID teamId);

	void deleteByPlayoffId(UUID playoffId);

	/**
	 * Full-table finder used by {@code BackupExportService}.
	 *
	 * <p>Eager-fetches the two {@code @ManyToOne} associations: {@code playoff} and {@code team}.
	 */
	@EntityGraph(attributePaths = {"playoff", "team"})
	@Query("SELECT e FROM PlayoffSeed e")
	List<PlayoffSeed> findAllForBackup();
}
