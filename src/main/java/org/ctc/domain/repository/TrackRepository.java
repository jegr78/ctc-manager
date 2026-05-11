package org.ctc.domain.repository;

import org.ctc.domain.model.Track;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface TrackRepository extends JpaRepository<Track, UUID> {
	List<Track> findAllByOrderByNameAsc();

	boolean existsByName(String name);

	/**
	 * Phase 73-02: full-table finder used by {@code BackupExportService}.
	 *
	 * <p>{@code Track} has no {@code @ManyToOne} associations the export aggregate touches;
	 * the empty {@code @EntityGraph} keeps the contract uniform across all 24 backup finders.
	 */
	@EntityGraph(attributePaths = {})
	@Query("SELECT e FROM Track e")
	List<Track> findAllForBackup();
}
