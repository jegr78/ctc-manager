package org.ctc.backup.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 73-03 — full Spring-context IT for {@link BackupExportService}.
 *
 * <p>Boots the {@code dev} profile so {@code DevDataSeeder} populates H2 with the
 * standard fixture (Saison 2023 + 2024-3 + sub-teams + drivers + races). Drives the
 * three public methods of {@link BackupExportService} and asserts:
 * <ul>
 *   <li>{@code countRowsPerTable()} returns a non-zero {@link LinkedHashMap} keyed by
 *       table names from {@link BackupSchema#getExportOrder()}; at minimum the
 *       {@code teams} and {@code seasons} tables (always seeded by
 *       {@code DevDataSeeder}) report non-zero counts.</li>
 *   <li>{@code fetchAllForBackup(Season.class)} returns the seeded seasons (the
 *       {@link SeasonRepository#findAllForBackup()} dispatcher path).</li>
 *   <li>Map iteration order matches the {@code BackupSchema} export order — the
 *       contract Plan 73-03 promises so the manifest emits deterministic JSON.</li>
 * </ul>
 *
 * <p>Not {@code @Transactional} — the production service carries the read-only
 * transaction; a test-class transaction would mask a regression that removed it.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupExportServiceIT {

	@Autowired
	private BackupExportService exportService;

	@Autowired
	private BackupSchema backupSchema;

	@Autowired
	private SeasonRepository seasonRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Test
	void givenDevFixture_whenCountRowsPerTable_thenSeededTablesHaveNonZeroCounts() {
		// when
		Map<String, Long> counts = exportService.countRowsPerTable();

		// then
		assertThat(counts).as("countRowsPerTable must return one entry per export-order entity")
				.hasSize(backupSchema.getExportOrder().size());
		assertThat(counts).containsKey("teams");
		assertThat(counts).containsKey("seasons");
		assertThat(counts.get("teams"))
				.as("dev fixture seeds at least one team (TestDataService.seed)")
				.isGreaterThan(0L);
		assertThat(counts.get("seasons"))
				.as("dev fixture seeds at least one season (TestDataService.seed)")
				.isGreaterThan(0L);
		// Cross-check: count must equal repository.count() for the seeded entities.
		assertThat(counts.get("teams")).isEqualTo(teamRepository.count());
		assertThat(counts.get("seasons")).isEqualTo(seasonRepository.count());
	}

	@Test
	void givenDevFixture_whenCountRowsPerTable_thenMapPreservesExportOrder() {
		// when
		Map<String, Long> counts = exportService.countRowsPerTable();

		// then — iteration order of the returned LinkedHashMap must match the export order.
		List<String> expectedOrder = backupSchema.getExportOrder().stream()
				.map(EntityRef::tableName)
				.toList();
		assertThat(counts.keySet()).containsExactlyElementsOf(expectedOrder);
	}

	@Test
	void givenDevFixture_whenFetchAllForBackupSeasonClass_thenSeededSeasonsReturned() {
		// when
		List<?> rows = exportService.fetchAllForBackup(Season.class);

		// then
		assertThat(rows).as("fetchAllForBackup(Season.class) must return the seeded seasons")
				.isNotEmpty()
				.hasSize((int) seasonRepository.count())
				.allMatch(Season.class::isInstance);
	}
}
