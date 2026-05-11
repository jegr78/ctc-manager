package org.ctc.domain.repository;

import org.ctc.domain.model.MatchScoring;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MatchScoringRepository extends JpaRepository<MatchScoring, UUID> {

	/**
	 * Phase 73-02: full-table finder used by {@code BackupExportService}.
	 *
	 * <p>{@code MatchScoring} has no {@code @ManyToOne} associations; the empty
	 * {@code @EntityGraph} keeps the contract uniform across all 24 backup finders.
	 */
	@EntityGraph(attributePaths = {})
	@Query("SELECT e FROM MatchScoring e")
	List<MatchScoring> findAllForBackup();
}
