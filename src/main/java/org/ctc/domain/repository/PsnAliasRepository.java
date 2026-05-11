package org.ctc.domain.repository;

import org.ctc.domain.model.PsnAlias;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PsnAliasRepository extends JpaRepository<PsnAlias, UUID> {

	Optional<PsnAlias> findByAliasIgnoreCase(String alias);

	boolean existsByAliasIgnoreCase(String alias);

	List<PsnAlias> findByDriverId(UUID driverId);

	/**
	 * Phase 73-02: full-table finder used by {@code BackupExportService}.
	 *
	 * <p>Eager-fetches the {@code driver} {@code @ManyToOne} association so the Phase 73-01
	 * MixIn can render the driver-ID without triggering {@code LazyInitializationException}.
	 */
	@EntityGraph(attributePaths = {"driver"})
	@Query("SELECT e FROM PsnAlias e")
	List<PsnAlias> findAllForBackup();
}
