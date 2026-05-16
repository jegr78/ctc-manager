package org.ctc.backup.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TrackRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 73-03 — runtime guard for EXPORT-05's no-LazyInitializationException promise.
 *
 * <p>This is the test that catches the Wave 1 73-02 deviation that triggered this Plan 03
 * continuation: {@code SeasonRepository.findAllForBackup()} eager-fetches only
 * {@code cars}, NOT {@code tracks} (Hibernate's {@code MultipleBagFetchException}
 * forbids two {@code @JoinFetch}-ed bags). The {@code tracks} association MUST therefore
 * lazy-load inside the {@code @Transactional(readOnly = true)} boundary on
 * {@link BackupArchiveService}.
 *
 * <p>Setup: seeds a test-only season + 3 tracks + wires them via the {@code @ManyToMany}
 * join table, all under the dev profile (H2). Then registers Logback
 * {@link ListAppender}s on the {@code org.hibernate} and {@code org.ctc.backup} loggers
 * before driving a full export.
 *
 * <p>Assertions:
 * <ol>
 *   <li>No log event whose message or throwable proxy mentions
 *       {@code LazyInitializationException} / {@code could not initialize proxy}.</li>
 *   <li>Zero ERROR-level events from {@code org.ctc.backup}.</li>
 *   <li>The serialized {@code data/seasons.json} contains a non-empty {@code tracks}
 *       array for the seeded season — proves the Wave 1 73-02 deviation does NOT
 *       silently drop the {@code tracks} data on export (the alternative failure mode).</li>
 * </ol>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Import(BackupExportNoLazyInitIT.LazyInitSeedConfig.class)
@Tag("integration")
class BackupExportNoLazyInitIT {

	@Autowired
	private BackupArchiveService archiveService;

	@Autowired
	private SeasonRepository seasonRepository;

	@Autowired
	private TrackRepository trackRepository;

	@Autowired
	@Qualifier("backupObjectMapper")
	private ObjectMapper backupObjectMapper;

	@Autowired
	private LazyInitSeedHelper seedHelper;

	private ListAppender<ILoggingEvent> hibernateAppender;
	private ListAppender<ILoggingEvent> backupAppender;
	private Logger hibernateLogger;
	private Logger backupLogger;

	private static final String SEED_SEASON_NAME = "T-LazyInit-Season-Tracks";
	private static final String SEED_TRACK_PREFIX = "T-LazyInit-Track-";

	@BeforeEach
	void setUp() {
		// Seed a Season with 3 Tracks via the @ManyToMany join table — committed so the
		// production @Transactional service can read them in a fresh session.
		seedHelper.seedSeasonWithTracks(SEED_SEASON_NAME, SEED_TRACK_PREFIX);

		// Register ListAppenders on the two log namespaces that surface LIE messages.
		hibernateLogger = (Logger) LoggerFactory.getLogger("org.hibernate");
		backupLogger = (Logger) LoggerFactory.getLogger("org.ctc.backup");
		hibernateAppender = new ListAppender<>();
		backupAppender = new ListAppender<>();
		hibernateAppender.setContext(hibernateLogger.getLoggerContext());
		backupAppender.setContext(backupLogger.getLoggerContext());
		hibernateAppender.start();
		backupAppender.start();
		hibernateLogger.addAppender(hibernateAppender);
		backupLogger.addAppender(backupAppender);
	}

	@AfterEach
	void tearDown() {
		hibernateLogger.detachAppender(hibernateAppender);
		backupLogger.detachAppender(backupAppender);
		hibernateAppender.stop();
		backupAppender.stop();
		seedHelper.deleteSeasonByName(SEED_SEASON_NAME);
		seedHelper.deleteTracksByPrefix(SEED_TRACK_PREFIX);
	}

	@Test
	void givenSeasonWithLazyTracks_whenWriteZip_thenZeroLazyInitMessagesLogged() throws Exception {
		// given — seeded by @BeforeEach
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// when
		archiveService.writeZip(out, Instant.now());

		// then — no LIE-flavoured log messages from either namespace
		List<ILoggingEvent> all = collectAllLogEvents();
		assertThat(all).noneMatch(BackupExportNoLazyInitIT::mentionsLazyInit);
		assertThat(backupAppender.list)
				.as("zero ERROR-level events from org.ctc.backup during writeZip")
				.noneMatch(evt -> evt.getLevel().equals(Level.ERROR));
	}

	@Test
	void givenSeasonWithThreeTracks_whenWriteZip_thenSeasonsJsonContainsNonEmptyTracksArray() throws Exception {
		// given — seeded by @BeforeEach
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		// when
		archiveService.writeZip(out, Instant.now());
		byte[] seasonsJson = readEntry(out.toByteArray(), "data/seasons.json");
		JsonNode seasonsArray = backupObjectMapper.readTree(seasonsJson);

		// then — array shape and the seed-season's tracks ID-ref array is non-empty.
		assertThat(seasonsArray.isArray()).isTrue();
		JsonNode seededSeason = findByField(seasonsArray, "name", SEED_SEASON_NAME);
		assertThat(seededSeason)
				.as("seeded season must appear in data/seasons.json — proves @Transactional propagation")
				.isNotNull();
		JsonNode tracks = seededSeason.get("tracks");
		assertThat(tracks).as("'tracks' field must be present on serialized Season").isNotNull();
		assertThat(tracks.isArray()).isTrue();
		assertThat(tracks.size())
				.as("Season.tracks must lazy-load + serialize 3 entries (Wave 1 73-02 deviation guard)")
				.isEqualTo(3);
	}

	private List<ILoggingEvent> collectAllLogEvents() {
		List<ILoggingEvent> combined = new java.util.ArrayList<>(hibernateAppender.list);
		combined.addAll(backupAppender.list);
		return combined;
	}

	private static boolean mentionsLazyInit(ILoggingEvent evt) {
		String message = evt.getFormattedMessage();
		if (message != null
				&& (message.contains("LazyInitializationException")
						|| message.contains("could not initialize proxy"))) {
			return true;
		}
		IThrowableProxy throwable = evt.getThrowableProxy();
		while (throwable != null) {
			if ("org.hibernate.LazyInitializationException".equals(throwable.getClassName())) {
				return true;
			}
			throwable = throwable.getCause();
		}
		return false;
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

	private JsonNode findByField(JsonNode array, String field, String value) {
		for (JsonNode node : array) {
			JsonNode candidate = node.get(field);
			if (candidate != null && value.equals(candidate.asText())) {
				return node;
			}
		}
		return null;
	}

	/**
	 * Test-only Spring config that publishes the {@link LazyInitSeedHelper} bean —
	 * {@code @Component} on a static nested class is not picked up by the default
	 * component scan, so we register it explicitly via {@code @Import}.
	 */
	@TestConfiguration
	static class LazyInitSeedConfig {
		@Bean
		LazyInitSeedHelper lazyInitSeedHelper(SeasonRepository seasonRepository,
				TrackRepository trackRepository) {
			return new LazyInitSeedHelper(seasonRepository, trackRepository);
		}
	}

	/**
	 * Helper for committed seeding outside the test's transactional scope.
	 *
	 * <p>Each method runs in its own {@link Transactional @Transactional} boundary so the
	 * data is visible to the production service's separate read-only transaction.
	 */
	static class LazyInitSeedHelper {

		private final SeasonRepository seasonRepository;
		private final TrackRepository trackRepository;

		LazyInitSeedHelper(SeasonRepository seasonRepository, TrackRepository trackRepository) {
			this.seasonRepository = seasonRepository;
			this.trackRepository = trackRepository;
		}

		@Transactional
		public void seedSeasonWithTracks(String seasonName, String trackPrefix) {
			// Avoid duplicates if a prior @AfterEach failed to clean up.
			seasonRepository.findAll().stream()
					.filter(s -> seasonName.equals(s.getName()))
					.forEach(seasonRepository::delete);
			trackRepository.findAll().stream()
					.filter(t -> t.getName().startsWith(trackPrefix))
					.forEach(trackRepository::delete);

			Track t1 = trackRepository.save(new Track(trackPrefix + "Spa", "BE"));
			Track t2 = trackRepository.save(new Track(trackPrefix + "Monza", "IT"));
			Track t3 = trackRepository.save(new Track(trackPrefix + "Nürburgring", "DE"));

			Season season = new Season(seasonName, 2099, 99);
			season.getTracks().add(t1);
			season.getTracks().add(t2);
			season.getTracks().add(t3);
			seasonRepository.save(season);
		}

		@Transactional
		public void deleteSeasonByName(String seasonName) {
			seasonRepository.findAll().stream()
					.filter(s -> seasonName.equals(s.getName()))
					.forEach(seasonRepository::delete);
		}

		@Transactional
		public void deleteTracksByPrefix(String trackPrefix) {
			trackRepository.findAll().stream()
					.filter(t -> t.getName().startsWith(trackPrefix))
					.forEach(trackRepository::delete);
		}
	}
}
