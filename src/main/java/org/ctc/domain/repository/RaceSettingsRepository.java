package org.ctc.domain.repository;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.RaceSettings;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RaceSettingsRepository extends JpaRepository<RaceSettings, UUID> {

	/**
	 * Full-table finder used by {@code BackupExportService}.
	 *
	 * <p>Eager-fetches the {@code race} {@code @OneToOne} association (owning side).
	 */
	@EntityGraph(attributePaths = {"race"})
	@Query("SELECT e FROM RaceSettings e")
	List<RaceSettings> findAllForBackup();
}
