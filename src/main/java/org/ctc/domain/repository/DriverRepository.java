package org.ctc.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.Driver;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

	Optional<Driver> findByPsnId(String psnId);

	Optional<Driver> findByPsnIdIgnoreCase(String psnId);

	List<Driver> findByActiveTrue();

	@Query("SELECT a.driver FROM PsnAlias a WHERE LOWER(a.alias) = LOWER(:alias)")
	Optional<Driver> findByAliasIgnoreCase(@Param("alias") String alias);

	/**
	 * Full-table finder used by {@code BackupExportService}.
	 *
	 * <p>{@code Driver} only owns {@code @OneToMany} collections (psnAliases, ...) which the
	 * backup MixIn ignores; no {@code @ManyToOne} associations need eager-fetching.
	 */
	@EntityGraph(attributePaths = {})
	@Query("SELECT e FROM Driver e")
	List<Driver> findAllForBackup();
}
