package org.ctc.domain.repository;

import org.ctc.domain.model.RaceScoring;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface RaceScoringRepository extends JpaRepository<RaceScoring, UUID> {

	/**
	 * Full-table finder used by {@code BackupExportService}.
	 *
	 * <p>{@code RaceScoring} has no {@code @ManyToOne} associations; the empty
	 * {@code @EntityGraph} keeps the contract uniform across all 24 backup finders.
	 */
	@EntityGraph(attributePaths = {})
	@Query("SELECT e FROM RaceScoring e")
	List<RaceScoring> findAllForBackup();
}
