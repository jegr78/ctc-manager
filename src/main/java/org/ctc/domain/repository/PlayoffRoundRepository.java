package org.ctc.domain.repository;

import org.ctc.domain.model.PlayoffRound;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PlayoffRoundRepository extends JpaRepository<PlayoffRound, UUID> {

	@EntityGraph(attributePaths = {"playoff"})
	List<PlayoffRound> findByPlayoffIdOrderByRoundIndexAsc(UUID playoffId);

	/**
	 * Full-table finder used by {@code BackupExportService}.
	 *
	 * <p>Eager-fetches the {@code playoff} {@code @ManyToOne} association.
	 */
	@EntityGraph(attributePaths = {"playoff"})
	@Query("SELECT e FROM PlayoffRound e")
	List<PlayoffRound> findAllForBackup();
}
