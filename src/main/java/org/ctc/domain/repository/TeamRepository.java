package org.ctc.domain.repository;

import org.ctc.domain.model.Team;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {

	/**
	 * Single-row finder. Safe only for callers that guarantee shortName uniqueness
	 * (e.g., test fixtures with unique prefixes). Production import flows MUST use
	 * {@link #findAllByShortName(String)} because parent + sub-teams may share a shortName.
	 */
	Optional<Team> findByShortName(String shortName);

	Optional<Team> findByShortNameIgnoreCase(String shortName);

	/**
	 * List finder for the parent + sub-team collision case. Returns all teams matching
	 * the given shortName (parent and any sub-teams). Callers apply parent-precedence
	 * resolution at the service layer (see {@code DriverSheetImportService}).
	 */
	List<Team> findAllByShortName(String shortName);

	/**
	 * Full-table finder used by {@code BackupExportService}.
	 *
	 * <p>Eager-fetches the {@code parentTeam} self-FK so the backup MixIn can render
	 * the parent-ID for sub-teams without triggering {@code LazyInitializationException}.
	 */
	@EntityGraph(attributePaths = {"parentTeam"})
	@Query("SELECT e FROM Team e")
	List<Team> findAllForBackup();
}
