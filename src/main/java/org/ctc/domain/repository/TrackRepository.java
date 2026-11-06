package org.ctc.domain.repository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;
import java.time.LocalDateTime;
import org.ctc.domain.model.Track;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

public interface TrackRepository extends JpaRepository<Track, UUID> {
	List<Track> findAllByOrderByNameAsc();

	boolean existsByName(String name);

    Optional<Track> findByName(String name);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE Track t SET t.imageUrl = :imageUrl, t.updatedAt = :updatedAt "
            + "WHERE t.id = :id AND t.name = :name AND (t.imageUrl IS NULL OR TRIM(t.imageUrl) = '')")
    int fillMissingImage(UUID id, String name, String imageUrl, LocalDateTime updatedAt);

	/**
	 * Full-table finder used by {@code BackupExportService}.
	 *
	 * <p>{@code Track} has no {@code @ManyToOne} associations the export aggregate touches;
	 * the empty {@code @EntityGraph} keeps the contract uniform across all 24 backup finders.
	 */
	@EntityGraph(attributePaths = {})
	@Query("SELECT e FROM Track e")
	List<Track> findAllForBackup();
}
