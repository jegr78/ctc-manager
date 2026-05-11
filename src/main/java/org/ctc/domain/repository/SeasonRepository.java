package org.ctc.domain.repository;

import org.ctc.domain.model.Season;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeasonRepository extends JpaRepository<Season, UUID> {

	/**
	 * Returns the unique active season, if any.
	 *
	 * <p>Note: scoring, format, and dates live on {@link org.ctc.domain.model.SeasonPhase},
	 * not on {@code Season} directly. Use {@code seasonPhaseService.findRegularPhase(...)}
	 * or {@code season.getPhases()} to access them.
	 */
	Optional<Season> findByActiveTrue();

	/**
	 * Returns seasons containing the given team via the {@code seasonTeams} association.
	 */
	List<Season> findBySeasonTeamsTeamId(UUID teamId);

	/**
	 * Canonical season identity lookup. The result list contains 0, 1, or &gt;1 entries —
	 * no DB UNIQUE constraint enforces uniqueness on (year, number); callers must apply
	 * the contract (typically via {@code SeasonManagementService.findUnique}).
	 */
	List<Season> findByYearAndNumber(int year, int number);

	/**
	 * Legacy single-year lookup. Same 0/1/&gt;1 contract as
	 * {@link #findByYearAndNumber(int, int)}; used by the driver-sheet importer when a
	 * tab matches the legacy {@code ^\d{4}$} pattern.
	 */
	List<Season> findByYear(int year);

	/**
	 * Phase 73-02: full-table finder used by {@code BackupExportService}.
	 *
	 * <p><strong>Deviation from Plan 73-02 (Rule 1 — runtime bug fix):</strong> the plan
	 * called for {@code attributePaths = {"cars", "tracks"}}, but eager-joining both
	 * {@code List<>} {@code @ManyToMany} collections in a single JPQL query triggers
	 * Hibernate's {@code MultipleBagFetchException} ("cannot simultaneously fetch
	 * multiple bags"). RESEARCH §EntityGraph Fetch Map line 450 incorrectly assumed
	 * Hibernate would issue two separate join-table SELECTs.
	 *
	 * <p>The {@code @EntityGraph} therefore lists only {@code "cars"}; the
	 * {@code BackupExportService} (Plan 73-03) is {@code @Transactional(readOnly=true)},
	 * so it can dereference {@code season.getTracks()} inside its own session — the
	 * collection materialises lazily without {@code LazyInitializationException}.
	 */
	@EntityGraph(attributePaths = {"cars"})
	@Query("SELECT e FROM Season e")
	List<Season> findAllForBackup();
}
