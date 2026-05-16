package org.ctc.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ctc.admin.TestDataService;
import org.ctc.backup.audit.DataImportAudit;
import org.ctc.backup.audit.DataImportAuditRepository;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.dto.BackupImportResult;
import org.ctc.backup.schema.BackupManifest;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 73-03 — round-trip IT through the Phase 72 manifest contract.
 *
 * <p>Drives a full export against the dev fixture, then re-opens the ZIP and parses
 * {@code manifest.json} back via the qualified {@code backupObjectMapper}. Asserts:
 * <ol>
 *   <li>The manifest deserialises into a {@link BackupManifest} record with no
 *       {@code FAIL_ON_UNKNOWN_PROPERTIES} fallout — every field on the wire matches
 *       the record shape (Phase 72 contract).</li>
 *   <li>{@link BackupManifest#schemaVersion()} equals {@link BackupSchema#SCHEMA_VERSION}.</li>
 *   <li>{@link BackupManifest#appVersion()} is non-null and non-empty.</li>
 *   <li>{@link BackupManifest#exportDate()} matches the supplied {@link Instant}.</li>
 *   <li>The {@code tableCounts} keyset equals the set of {@code ref.tableName()} values
 *       in {@link BackupSchema#getExportOrder()}.</li>
 *   <li>The sum of {@code tableCounts} entries that have a non-zero count corresponds
 *       to a subset of the {@code data/*.json} ZIP entries (count entries are upper-bound
 *       by ZIP entries; data entries always equal the export-order size).</li>
 * </ol>
 *
 * <p>Phase 77 extends this class with two {@code @Nested} profile classes:
 * <ul>
 *   <li>{@code H2RoundTripTests} — drives the full export → wipe → import round-trip on H2
 *       ({@code @ActiveProfiles("dev")}), asserts 24-entity row-count parity and SHA-256
 *       byte-equality on {@code Race}, {@code SeasonDriver}, and {@code Team} sample
 *       entities serialized through {@code @Qualifier("backupObjectMapper")}.</li>
 *   <li>{@code MariaDbRoundTripTests} — identical scenario on a live MariaDB:11 instance via
 *       Testcontainers ({@code @ActiveProfiles("local")}), gated by
 *       {@code @EnabledIfSystemProperty(named="docker.available", matches="true")}.</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class BackupRoundTripIT {

	/** Defensive table-name allow-list matching the production SAFE_TABLE_NAME guard. */
	private static final Pattern SAFE_TABLE_NAME = Pattern.compile("^[a-z_]+$");

	/** Per-test-class temp root for {@code app.backup.import-backups-dir} — prevents
	 *  same-second collisions on {@code data/.import-backups/&lt;ts&gt;/auto-backup-before-import.zip}
	 *  when this IT runs back-to-back with other import-execute ITs. Inherited by
	 *  {@link H2RoundTripTests} and {@link MariaDbRoundTripTests} via the shared
	 *  {@link DynamicPropertySource}. */
	private static final Path IMPORT_BACKUPS_ROOT;
	static {
		try {
			IMPORT_BACKUPS_ROOT = Files.createTempDirectory("ctc-import-backups-roundtrip-it-");
			IMPORT_BACKUPS_ROOT.toFile().deleteOnExit();
		} catch (IOException e) {
			throw new IllegalStateException("Failed to allocate import-backups tempdir", e);
		}
	}

	@DynamicPropertySource
	static void overrideImportBackupsDir(DynamicPropertyRegistry registry) {
		registry.add("app.backup.import-backups-dir", IMPORT_BACKUPS_ROOT::toString);
	}

	/**
	 * Best-effort recursive content wipe — keeps the directory itself, deletes everything
	 * inside it. Used by {@link H2RoundTripTests#cleanImportBackupsRoot()} and
	 * {@link MariaDbRoundTripTests#cleanImportBackupsRoot()} to prevent two consecutive
	 * @Test methods from colliding on
	 * {@code IMPORT_BACKUPS_ROOT/<ts>/auto-backup-before-import.zip} when their
	 * {@code Instant.now().truncatedTo(SECONDS)} timestamps land in the same second.
	 */
	static void cleanDirContents(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.sorted(Comparator.reverseOrder())
					.filter(p -> !p.equals(dir))
					.forEach(p -> {
						try {
							Files.deleteIfExists(p);
						} catch (IOException ignored) {
							// best-effort cleanup
						}
					});
		}
	}

	@Autowired
	private BackupArchiveService archiveService;

	@Autowired
	private BackupSchema backupSchema;

	@Autowired
	@Qualifier("backupObjectMapper")
	private ObjectMapper backupObjectMapper;

	@Test
	void givenDevFixture_whenWriteZipAndReadManifest_thenManifestRoundTripsThroughBackupObjectMapper()
			throws Exception {
		// given
		Instant exportDate = Instant.parse("2026-05-12T07:00:00Z");
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// when
		archiveService.writeZip(out, exportDate);
		byte[] manifestBytes = readEntry(out.toByteArray(), "manifest.json");
		BackupManifest manifest = backupObjectMapper.readValue(manifestBytes, BackupManifest.class);

		// then
		assertThat(manifest).as("manifest must deserialise via the qualified backupObjectMapper")
				.isNotNull();
		assertThat(manifest.schemaVersion()).isEqualTo(BackupSchema.SCHEMA_VERSION);
		assertThat(manifest.appVersion()).isNotBlank();
		assertThat(manifest.exportDate()).isEqualTo(exportDate);
	}

	@Test
	void givenDevFixture_whenWriteZipWithCurrentInstant_thenExportDateWithinAcceptableWindow()
			throws Exception {
		// given — capture the test-start instant and pass a freshly computed Instant.now()
		Instant beforeWrite = Instant.now();
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// when
		Instant exportDate = Instant.now();
		archiveService.writeZip(out, exportDate);
		Instant afterWrite = Instant.now();
		byte[] manifestBytes = readEntry(out.toByteArray(), "manifest.json");
		BackupManifest manifest = backupObjectMapper.readValue(manifestBytes, BackupManifest.class);

		// then — exportDate must equal the passed instant and lie within the test window
		// (defense-in-depth: zero clock drift, but allow a 60s tolerance for slow CI runs).
		assertThat(manifest.exportDate()).isEqualTo(exportDate);
		assertThat(Duration.between(beforeWrite, manifest.exportDate()))
				.isLessThanOrEqualTo(Duration.between(beforeWrite, afterWrite).plusSeconds(60));
	}

	@Test
	void givenDevFixture_whenReadManifest_thenTableCountsKeysetEqualsExportOrderTableNames()
			throws Exception {
		// given
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// when
		archiveService.writeZip(out, Instant.now());
		byte[] manifestBytes = readEntry(out.toByteArray(), "manifest.json");
		BackupManifest manifest = backupObjectMapper.readValue(manifestBytes, BackupManifest.class);

		// then
		Set<String> expectedKeys = new HashSet<>();
		backupSchema.getExportOrder().forEach(ref -> expectedKeys.add(ref.tableName()));
		assertThat(manifest.tableCounts().keySet())
				.as("tableCounts keys must match the export-order tableName set")
				.containsExactlyInAnyOrderElementsOf(expectedKeys);
	}

	@Test
	void givenDevFixture_whenWriteZip_thenEntityCountsKeysMatchDataJsonEntryCount() throws Exception {
		// given
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// when
		archiveService.writeZip(out, Instant.now());
		byte[] zipBytes = out.toByteArray();
		byte[] manifestBytes = readEntry(zipBytes, "manifest.json");
		BackupManifest manifest = backupObjectMapper.readValue(manifestBytes, BackupManifest.class);

		// then — number of tableCounts entries equals the number of data/*.json entries.
		long dataEntries = countEntriesMatching(zipBytes, "data/", ".json");
		assertThat((long) manifest.tableCounts().size())
				.as("entityCounts entries must equal the data/*.json entry count")
				.isEqualTo(dataEntries);
	}

	private byte[] readEntry(byte[] zipBytes, String name) throws Exception {
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.getName().equals(name)) {
					ByteArrayOutputStream out = new ByteArrayOutputStream();
					byte[] buf = new byte[8192];
					int n;
					while ((n = zip.read(buf)) > 0) {
						out.write(buf, 0, n);
					}
					return out.toByteArray();
				}
				zip.closeEntry();
			}
		}
		throw new AssertionError("ZIP entry not found: " + name);
	}

	private long countEntriesMatching(byte[] zipBytes, String prefix, String suffix) throws Exception {
		long count = 0;
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.getName().startsWith(prefix) && entry.getName().endsWith(suffix)) {
					count++;
				}
				zip.closeEntry();
			}
		}
		return count;
	}

	// =========================================================================
	// Phase 77 — Full round-trip + SHA-256 on H2
	// =========================================================================

	/**
	 * Full export → wipe → import round-trip on H2 (dev profile).
	 *
	 * <p>Asserts 24-entity row-count parity and SHA-256 byte-equality on {@code Race},
	 * {@code SeasonDriver}, and {@code Team} sample entities serialized through
	 * {@code @Qualifier("backupObjectMapper")} (QUAL-02, D-02, D-03, D-07).
	 */
	@Nested
	@SpringBootTest
	@ActiveProfiles("dev")
	class H2RoundTripTests {

		@Autowired
		BackupImportService backupImportService;

		@Autowired
		BackupArchiveService backupArchiveService;

		@Autowired
		TestDataService testDataService;

		@Autowired
		BackupSchema backupSchema;

		@Autowired
		JdbcTemplate jdbcTemplate;

		@Autowired
		@Qualifier("backupObjectMapper")
		ObjectMapper backupObjectMapper;

		@Autowired
		RaceRepository raceRepository;

		@Autowired
		SeasonDriverRepository seasonDriverRepository;

		@Autowired
		TeamRepository teamRepository;

		@Autowired
		DataImportAuditRepository dataImportAuditRepository;

		@Value("${app.backup.staging-dir}")
		String stagingDirRaw;

		Path stagingDir;

		@DynamicPropertySource
		static void overrideImportBackupsDirH2(DynamicPropertyRegistry registry) {
			registry.add("app.backup.import-backups-dir", IMPORT_BACKUPS_ROOT::toString);
		}

		@BeforeEach
		void seedFixture() throws IOException {
			testDataService.seed();
			stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
			Files.createDirectories(stagingDir);
		}

		@AfterEach
		void cleanImportBackupsRoot() throws IOException {
			cleanDirContents(IMPORT_BACKUPS_ROOT);
		}

		@Test
		void givenH2DevFixture_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch()
				throws Exception {
			// given — capture pre-export counts and sample entity hashes
			Map<String, Long> preExportCounts = captureRowCounts();

			// D-04: deterministic first-row selection by natural ordering
			Race preRace = raceRepository.findAll(Sort.by(Sort.Order.asc("id"))).get(0);
			SeasonDriver preSeasonDriver = seasonDriverRepository.findAll(Sort.by(Sort.Order.asc("id"))).get(0);
			Team preTeam = teamRepository.findAll(Sort.by(Sort.Order.asc("id"))).stream()
					.filter(t -> t.getParentTeam() == null)
					.findFirst()
					.orElseThrow(() -> new AssertionError("No root team found in dev fixture"));

			// D-03: SHA-256 over in-DB row via backupObjectMapper
			byte[] preRaceHash = hashEntity(preRace);
			byte[] preSeasonDriverHash = hashEntity(preSeasonDriver);
			byte[] preTeamHash = hashEntity(preTeam);

			UUID preRaceId = preRace.getId();
			UUID preSeasonDriverId = preSeasonDriver.getId();
			UUID preTeamId = preTeam.getId();

			// when — full round-trip: export → stage → execute (wipes + restores)
			byte[] zipBytes = exportToBytes();
			MockMultipartFile file = new MockMultipartFile(
					"file", "h2-round-trip-export.zip", "application/zip", zipBytes);
			BackupImportPreview preview = backupImportService.stage(file);
			BackupImportResult result = backupImportService.execute(preview.stagingId());

			// then — result sanity
			assertThat(result).as("execute() must return a non-null result").isNotNull();

			// then — 24-entity row-count parity
			assertThat(captureRowCounts())
					.as("24-entity row-count parity after H2 round-trip")
					.isEqualTo(preExportCounts);

			// then — SHA-256 byte-equality for Race
			Race postRace = raceRepository.findById(preRaceId)
					.orElseThrow(() -> new AssertionError("Race " + preRaceId + " missing after import"));
			byte[] postRaceHash = hashEntity(postRace);
			assertThat(postRaceHash)
					.as("SHA-256 of Race %s must be byte-equal after round-trip\npre=%s\npost=%s",
							preRaceId,
							HexFormat.of().formatHex(preRaceHash),
							HexFormat.of().formatHex(postRaceHash))
					.containsExactly(preRaceHash);

			// then — SHA-256 byte-equality for SeasonDriver
			SeasonDriver postSeasonDriver = seasonDriverRepository.findById(preSeasonDriverId)
					.orElseThrow(() -> new AssertionError("SeasonDriver " + preSeasonDriverId + " missing after import"));
			byte[] postSeasonDriverHash = hashEntity(postSeasonDriver);
			assertThat(postSeasonDriverHash)
					.as("SHA-256 of SeasonDriver %s must be byte-equal after round-trip\npre=%s\npost=%s",
							preSeasonDriverId,
							HexFormat.of().formatHex(preSeasonDriverHash),
							HexFormat.of().formatHex(postSeasonDriverHash))
					.containsExactly(preSeasonDriverHash);

			// then — SHA-256 byte-equality for Team
			Team postTeam = teamRepository.findById(preTeamId)
					.orElseThrow(() -> new AssertionError("Team " + preTeamId + " missing after import"));
			byte[] postTeamHash = hashEntity(postTeam);
			assertThat(postTeamHash)
					.as("SHA-256 of Team %s must be byte-equal after round-trip\npre=%s\npost=%s",
							preTeamId,
							HexFormat.of().formatHex(preTeamHash),
							HexFormat.of().formatHex(postTeamHash))
					.containsExactly(preTeamHash);
		}

		// -------------------------------------------------------------------------
		// Helpers — duplicated per @Nested class (each class has its own ApplicationContext)
		// -------------------------------------------------------------------------

		/**
		 * Builds a Phase-73 export ZIP via the real {@link BackupArchiveService} writer.
		 */
		private byte[] exportToBytes() throws IOException {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			backupArchiveService.writeZip(baos, Instant.now());
			return baos.toByteArray();
		}

		/**
		 * Captures per-entity row counts as a {@link LinkedHashMap} keyed by snake_case table
		 * name. Mirrors the {@code SAFE_TABLE_NAME} regex guard (T-77-01-01) before native
		 * {@code COUNT(*)} concatenation.
		 */
		private Map<String, Long> captureRowCounts() {
			Map<String, Long> counts = new LinkedHashMap<>();
			for (EntityRef ref : backupSchema.getExportOrder()) {
				String table = ref.tableName();
				if (!SAFE_TABLE_NAME.matcher(table).matches()) {
					throw new IllegalStateException("Unsafe table name in BackupSchema: " + table);
				}
				Long count = jdbcTemplate.queryForObject(
						"SELECT COUNT(*) FROM " + table, Long.class);
				counts.put(table, count == null ? 0L : count);
			}
			return counts;
		}

		/**
		 * Computes SHA-256 over the in-DB entity row serialized via
		 * {@code @Qualifier("backupObjectMapper")} (D-03). Proves wire-shape invariance and
		 * the Phase 75 {@code AuditingEntityListener} bypass contract ({@code created_at} /
		 * {@code updated_at} survive verbatim through the round-trip).
		 */
		private byte[] hashEntity(Object entity) throws Exception {
			byte[] bytes = backupObjectMapper.writeValueAsBytes(entity);
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		}

		/**
		 * Polls {@link DataImportAuditRepository} for up to {@code timeout} for the audit
		 * row. Included for parity with {@code BackupImportMariaDbSmokeIT} (optional use).
		 */
		@SuppressWarnings("unused")
		private DataImportAudit awaitAuditRow(UUID auditUuid, Duration timeout) throws InterruptedException {
			Instant deadline = Instant.now().plus(timeout);
			Optional<DataImportAudit> maybe;
			while (Instant.now().isBefore(deadline)) {
				maybe = dataImportAuditRepository.findById(auditUuid);
				if (maybe.isPresent()) {
					return maybe.get();
				}
				Thread.sleep(100L);
			}
			maybe = dataImportAuditRepository.findById(auditUuid);
			return maybe.orElseThrow(() -> new AssertionError(
					"data_import_audit row " + auditUuid + " did not materialize within " + timeout));
		}
	}

	// =========================================================================
	// Phase 77 — Full round-trip + SHA-256 on live MariaDB (Testcontainers)
	// =========================================================================

	/**
	 * Full export → wipe → import round-trip on a live MariaDB:11 instance via Testcontainers
	 * (local profile). Gated by {@code @EnabledIfSystemProperty(named="docker.available",
	 * matches="true")} — skipped in CI by default, run locally with
	 * {@code -Ddocker.available=true} (QUAL-02, D-05, D-06, D-07).
	 *
	 * <p>Scenario body is identical to {@link H2RoundTripTests} — the engine difference is
	 * purely the {@code @ActiveProfiles} and Testcontainers wiring (D-07 "no MariaDB cheaper
	 * / H2 fuller split"). Catches dialect divergence in the wire format ({@code BINARY(16)}
	 * UUID packing, {@code LONGTEXT} JSON columns, timestamp precision).
	 */
	@Nested
	@SpringBootTest
	@ActiveProfiles("local")
	@Testcontainers
	@EnabledIfSystemProperty(named = "docker.available", matches = "true",
			disabledReason = "Set -Ddocker.available=true (with Docker daemon) to run the MariaDB Testcontainers round-trip IT")
	class MariaDbRoundTripTests {

		@Container
		static MariaDBContainer<?> mariadb = new MariaDBContainer<>("mariadb:11")
				.withDatabaseName("ctc_test")
				.withUsername("ctc")
				.withPassword("test");

		@DynamicPropertySource
		static void overrideJdbcUrl(DynamicPropertyRegistry registry) {
			// Append rewriteBatchedStatements=true so the production JdbcTemplate.batchUpdate
			// 500-row batches compile to a single multi-row INSERT on the wire (Phase 75 RESEARCH §10).
			registry.add("spring.datasource.url",
					() -> mariadb.getJdbcUrl() + "?rewriteBatchedStatements=true");
			registry.add("spring.datasource.username", mariadb::getUsername);
			registry.add("spring.datasource.password", mariadb::getPassword);
			registry.add("spring.datasource.driver-class-name", () -> "org.mariadb.jdbc.Driver");
			// Isolate auto-backup-before-import ZIP path from the real data/.import-backups/
			// to prevent same-second collisions with other import-execute ITs.
			registry.add("app.backup.import-backups-dir", IMPORT_BACKUPS_ROOT::toString);
		}

		@Autowired
		BackupImportService backupImportService;

		@Autowired
		BackupArchiveService backupArchiveService;

		@Autowired
		TestDataService testDataService;

		@Autowired
		BackupSchema backupSchema;

		@Autowired
		JdbcTemplate jdbcTemplate;

		@Autowired
		@Qualifier("backupObjectMapper")
		ObjectMapper backupObjectMapper;

		@Autowired
		RaceRepository raceRepository;

		@Autowired
		SeasonDriverRepository seasonDriverRepository;

		@Autowired
		TeamRepository teamRepository;

		@Autowired
		DataImportAuditRepository dataImportAuditRepository;

		@Value("${app.backup.staging-dir}")
		String stagingDirRaw;

		Path stagingDir;

		@BeforeEach
		void seedFixture() throws IOException {
			testDataService.seed();
			stagingDir = Paths.get(stagingDirRaw).toAbsolutePath().normalize();
			Files.createDirectories(stagingDir);
		}

		@AfterEach
		void cleanImportBackupsRoot() throws IOException {
			cleanDirContents(IMPORT_BACKUPS_ROOT);
		}

		@Test
		void givenLiveMariaDb_whenExportWipeImport_thenRowCountsEqualAndSampleHashesMatch()
				throws Exception {
			// given — capture pre-export counts and sample entity hashes
			Map<String, Long> preExportCounts = captureRowCounts();

			// D-04: deterministic first-row selection by natural ordering
			Race preRace = raceRepository.findAll(Sort.by(Sort.Order.asc("id"))).get(0);
			SeasonDriver preSeasonDriver = seasonDriverRepository.findAll(Sort.by(Sort.Order.asc("id"))).get(0);
			Team preTeam = teamRepository.findAll(Sort.by(Sort.Order.asc("id"))).stream()
					.filter(t -> t.getParentTeam() == null)
					.findFirst()
					.orElseThrow(() -> new AssertionError("No root team found in dev fixture"));

			// D-03: SHA-256 over in-DB row via backupObjectMapper
			byte[] preRaceHash = hashEntity(preRace);
			byte[] preSeasonDriverHash = hashEntity(preSeasonDriver);
			byte[] preTeamHash = hashEntity(preTeam);

			UUID preRaceId = preRace.getId();
			UUID preSeasonDriverId = preSeasonDriver.getId();
			UUID preTeamId = preTeam.getId();

			// when — full round-trip: export → stage → execute (wipes + restores)
			byte[] zipBytes = exportToBytes();
			MockMultipartFile file = new MockMultipartFile(
					"file", "mariadb-round-trip-export.zip", "application/zip", zipBytes);
			BackupImportPreview preview = backupImportService.stage(file);
			BackupImportResult result = backupImportService.execute(preview.stagingId());

			// then — result sanity
			assertThat(result).as("execute() must return a non-null result").isNotNull();

			// then — 24-entity row-count parity
			assertThat(captureRowCounts())
					.as("24-entity row-count parity after MariaDB round-trip")
					.isEqualTo(preExportCounts);

			// then — SHA-256 byte-equality for Race
			Race postRace = raceRepository.findById(preRaceId)
					.orElseThrow(() -> new AssertionError("Race " + preRaceId + " missing after import"));
			byte[] postRaceHash = hashEntity(postRace);
			assertThat(postRaceHash)
					.as("SHA-256 of Race %s must be byte-equal after round-trip\npre=%s\npost=%s",
							preRaceId,
							HexFormat.of().formatHex(preRaceHash),
							HexFormat.of().formatHex(postRaceHash))
					.containsExactly(preRaceHash);

			// then — SHA-256 byte-equality for SeasonDriver
			SeasonDriver postSeasonDriver = seasonDriverRepository.findById(preSeasonDriverId)
					.orElseThrow(() -> new AssertionError("SeasonDriver " + preSeasonDriverId + " missing after import"));
			byte[] postSeasonDriverHash = hashEntity(postSeasonDriver);
			assertThat(postSeasonDriverHash)
					.as("SHA-256 of SeasonDriver %s must be byte-equal after round-trip\npre=%s\npost=%s",
							preSeasonDriverId,
							HexFormat.of().formatHex(preSeasonDriverHash),
							HexFormat.of().formatHex(postSeasonDriverHash))
					.containsExactly(preSeasonDriverHash);

			// then — SHA-256 byte-equality for Team
			Team postTeam = teamRepository.findById(preTeamId)
					.orElseThrow(() -> new AssertionError("Team " + preTeamId + " missing after import"));
			byte[] postTeamHash = hashEntity(postTeam);
			assertThat(postTeamHash)
					.as("SHA-256 of Team %s must be byte-equal after round-trip\npre=%s\npost=%s",
							preTeamId,
							HexFormat.of().formatHex(preTeamHash),
							HexFormat.of().formatHex(postTeamHash))
					.containsExactly(preTeamHash);
		}

		// -------------------------------------------------------------------------
		// Helpers — duplicated per @Nested class (each class has its own ApplicationContext)
		// -------------------------------------------------------------------------

		/**
		 * Builds a Phase-73 export ZIP via the real {@link BackupArchiveService} writer.
		 */
		private byte[] exportToBytes() throws IOException {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			backupArchiveService.writeZip(baos, Instant.now());
			return baos.toByteArray();
		}

		/**
		 * Captures per-entity row counts as a {@link LinkedHashMap} keyed by snake_case table
		 * name. Mirrors the {@code SAFE_TABLE_NAME} regex guard (T-77-01-01) before native
		 * {@code COUNT(*)} concatenation.
		 */
		private Map<String, Long> captureRowCounts() {
			Map<String, Long> counts = new LinkedHashMap<>();
			for (EntityRef ref : backupSchema.getExportOrder()) {
				String table = ref.tableName();
				if (!SAFE_TABLE_NAME.matcher(table).matches()) {
					throw new IllegalStateException("Unsafe table name in BackupSchema: " + table);
				}
				Long count = jdbcTemplate.queryForObject(
						"SELECT COUNT(*) FROM " + table, Long.class);
				counts.put(table, count == null ? 0L : count);
			}
			return counts;
		}

		/**
		 * Computes SHA-256 over the in-DB entity row serialized via
		 * {@code @Qualifier("backupObjectMapper")} (D-03). Proves wire-shape invariance and
		 * the Phase 75 {@code AuditingEntityListener} bypass contract ({@code created_at} /
		 * {@code updated_at} survive verbatim through the round-trip).
		 */
		private byte[] hashEntity(Object entity) throws Exception {
			byte[] bytes = backupObjectMapper.writeValueAsBytes(entity);
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		}

		/**
		 * Polls {@link DataImportAuditRepository} for up to {@code timeout} for the audit
		 * row. Included for parity with {@code BackupImportMariaDbSmokeIT} (optional use).
		 */
		@SuppressWarnings("unused")
		private DataImportAudit awaitAuditRow(UUID auditUuid, Duration timeout) throws InterruptedException {
			Instant deadline = Instant.now().plus(timeout);
			Optional<DataImportAudit> maybe;
			while (Instant.now().isBefore(deadline)) {
				maybe = dataImportAuditRepository.findById(auditUuid);
				if (maybe.isPresent()) {
					return maybe.get();
				}
				Thread.sleep(100L);
			}
			maybe = dataImportAuditRepository.findById(auditUuid);
			return maybe.orElseThrow(() -> new AssertionError(
					"data_import_audit row " + auditUuid + " did not materialize within " + timeout));
		}
	}
}
