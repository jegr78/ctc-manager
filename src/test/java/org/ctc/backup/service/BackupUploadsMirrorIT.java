package org.ctc.backup.service;

import org.ctc.domain.model.Car;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 73-03 — IT for the byte-identical uploads/ mirror.
 *
 * <p>Overrides {@code app.upload-dir} to a {@link TempDir}-managed directory via
 * {@link DynamicPropertySource}. {@code @BeforeEach} writes on-disk fixture files +
 * persists Team/Car rows referencing them, then drives {@code writeZip()} and asserts:
 * <ol>
 *   <li>Every referenced on-disk file appears as {@code uploads/<rel>} in the ZIP
 *       with byte-identical content.</li>
 *   <li>Orphan references (DB row points at a file that does not exist) are
 *       silently skipped — no entry in the ZIP, no test failure.</li>
 *   <li>Two entities pointing at the same upload URL produce exactly one
 *       {@code uploads/<rel>} entry — the deduplication contract of
 *       {@link BackupExportService#enumerateReferencedUploads()}.</li>
 * </ol>
 *
 * <p>The shared static {@link #UPLOAD_ROOT} field is used to register the dynamic
 * property because {@link DynamicPropertySource} methods must be static and run before
 * the Spring context boots.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Import(BackupUploadsMirrorIT.UploadsMirrorSeedConfig.class)
class BackupUploadsMirrorIT {

	private static final Path UPLOAD_ROOT;
	static {
		try {
			UPLOAD_ROOT = Files.createTempDirectory("ctc-uploads-mirror-it-");
			UPLOAD_ROOT.toFile().deleteOnExit();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to allocate upload root tempdir", e);
		}
	}

	@DynamicPropertySource
	static void overrideUploadDir(DynamicPropertyRegistry registry) {
		registry.add("app.upload-dir", UPLOAD_ROOT::toString);
	}

	@Autowired
	private BackupArchiveService archiveService;

	@Autowired
	private UploadsMirrorSeedHelper seedHelper;

	private static final String SEED_TEAM_PREFIX = "T-UploadsMirror-";
	private static final String SEED_CAR_PREFIX = "T-UploadsMirror-Car-";

	private final Map<String, byte[]> seededBytes = new LinkedHashMap<>();

	@BeforeEach
	void seedFixtures() throws Exception {
		seededBytes.clear();
		// Three referenced files (Team logo, Car image, additional Team-with-shared-logo).
		Path teamLogo = relative("teams/" + UUID.randomUUID() + "/logo.png");
		Path carImage = relative("cars/" + UUID.randomUUID() + "/image.png");
		writeBytes(teamLogo, "team-logo-bytes".getBytes());
		writeBytes(carImage, "car-image-bytes".getBytes());
		seededBytes.put(toRel(teamLogo), Files.readAllBytes(teamLogo));
		seededBytes.put(toRel(carImage), Files.readAllBytes(carImage));

		// Orphan reference — DB row points at a file that does NOT exist on disk.
		String orphanRel = "teams/" + UUID.randomUUID() + "/missing.png";

		seedHelper.seedFixture(SEED_TEAM_PREFIX, SEED_CAR_PREFIX,
				"/uploads/" + toRel(teamLogo),
				"/uploads/" + toRel(carImage),
				"/uploads/" + orphanRel);
	}

	@AfterEach
	void cleanup() throws Exception {
		seedHelper.cleanup(SEED_TEAM_PREFIX, SEED_CAR_PREFIX);
		// Delete the on-disk fixtures (best-effort; tempdir cleanup also fires on JVM exit).
		for (String rel : seededBytes.keySet()) {
			Files.deleteIfExists(UPLOAD_ROOT.resolve(rel));
		}
		seededBytes.clear();
	}

	@Test
	void givenSeededUploadsAndOrphanReference_whenWriteZip_thenSeededFilesIncludedByteIdenticalAndOrphanSkipped()
			throws Exception {
		// given — seeded by @BeforeEach
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// when
		archiveService.writeZip(out, Instant.now());

		// then — collect all uploads/ entries with their byte payloads.
		Map<String, byte[]> uploadEntries = readUploadEntries(out.toByteArray());

		// Every seeded file must be present with byte-identical content.
		for (Map.Entry<String, byte[]> seeded : seededBytes.entrySet()) {
			String zipName = "uploads/" + seeded.getKey();
			assertThat(uploadEntries)
					.as("uploads/%s must be present in the ZIP", seeded.getKey())
					.containsKey(zipName);
			assertThat(uploadEntries.get(zipName))
					.as("uploads/%s bytes must be identical to the on-disk source", seeded.getKey())
					.isEqualTo(seeded.getValue());
		}

		// Orphan must NOT be present — its on-disk file never existed.
		assertThat(uploadEntries.keySet())
				.as("orphan reference must be skipped, not emitted")
				.noneMatch(name -> name.contains("missing.png"));
	}

	@Test
	void givenDuplicateLogoUrlAcrossTwoTeams_whenWriteZip_thenSingleUploadsEntryEmitted() throws Exception {
		// given — seed an additional Team pointing at the SAME logo URL as the first Team
		String sharedLogoRel = seededBytes.keySet().iterator().next();
		seedHelper.seedDuplicateLogoTeam(SEED_TEAM_PREFIX, "/uploads/" + sharedLogoRel);
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// when
		archiveService.writeZip(out, Instant.now());

		// then — exactly ONE uploads/ entry for the shared logo.
		List<String> uploadNames = new ArrayList<>(readUploadEntries(out.toByteArray()).keySet());
		String expected = "uploads/" + sharedLogoRel;
		long matchCount = uploadNames.stream().filter(expected::equals).count();
		assertThat(matchCount)
				.as("LinkedHashSet dedup in enumerateReferencedUploads() must collapse duplicates")
				.isEqualTo(1L);
	}

	private static Path relative(String rel) {
		return UPLOAD_ROOT.resolve(rel);
	}

	private static String toRel(Path absolute) {
		return UPLOAD_ROOT.relativize(absolute).toString().replace('\\', '/');
	}

	private static void writeBytes(Path path, byte[] bytes) throws Exception {
		Files.createDirectories(path.getParent());
		Files.write(path, bytes);
	}

	private Map<String, byte[]> readUploadEntries(byte[] zipBytes) throws Exception {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
			ZipEntry entry;
			byte[] buf = new byte[8192];
			while ((entry = zip.getNextEntry()) != null) {
				if (entry.getName().startsWith("uploads/")) {
					ByteArrayOutputStream content = new ByteArrayOutputStream();
					int n;
					while ((n = zip.read(buf)) > 0) {
						content.write(buf, 0, n);
					}
					entries.put(entry.getName(), content.toByteArray());
				}
				zip.closeEntry();
			}
		}
		return entries;
	}

	@TestConfiguration
	static class UploadsMirrorSeedConfig {
		@Bean
		UploadsMirrorSeedHelper uploadsMirrorSeedHelper(TeamRepository teamRepository,
				CarRepository carRepository) {
			return new UploadsMirrorSeedHelper(teamRepository, carRepository);
		}
	}

	static class UploadsMirrorSeedHelper {

		private final TeamRepository teamRepository;
		private final CarRepository carRepository;

		UploadsMirrorSeedHelper(TeamRepository teamRepository, CarRepository carRepository) {
			this.teamRepository = teamRepository;
			this.carRepository = carRepository;
		}

		@Transactional
		public void seedFixture(String teamPrefix, String carPrefix,
				String teamLogoUrl, String carImageUrl, String orphanLogoUrl) {
			cleanup(teamPrefix, carPrefix);
			Team team = new Team(teamPrefix + "WithLogo", teamPrefix + "WL");
			team.setLogoUrl(teamLogoUrl);
			teamRepository.save(team);

			Team orphanTeam = new Team(teamPrefix + "WithOrphan", teamPrefix + "WO");
			orphanTeam.setLogoUrl(orphanLogoUrl);
			teamRepository.save(orphanTeam);

			Car car = new Car();
			car.setName(carPrefix + "Alpha");
			car.setManufacturer("T-Manuf");
			car.setImageUrl(carImageUrl);
			carRepository.save(car);
		}

		@Transactional
		public void seedDuplicateLogoTeam(String teamPrefix, String sharedLogoUrl) {
			Team duplicate = new Team(teamPrefix + "Duplicate", teamPrefix + "DUP");
			duplicate.setLogoUrl(sharedLogoUrl);
			teamRepository.save(duplicate);
		}

		@Transactional
		public void cleanup(String teamPrefix, String carPrefix) {
			teamRepository.findAll().stream()
					.filter(t -> t.getShortName() != null && t.getShortName().startsWith(teamPrefix))
					.forEach(teamRepository::delete);
			carRepository.findAll().stream()
					.filter(c -> c.getName() != null && c.getName().startsWith(carPrefix))
					.forEach(carRepository::delete);
		}
	}
}
