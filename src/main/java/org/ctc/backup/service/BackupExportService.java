package org.ctc.backup.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.ctc.domain.model.AttachmentType;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.Playoff;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.PlayoffRound;
import org.ctc.domain.model.PlayoffSeed;
import org.ctc.domain.model.PsnAlias;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceAttachment;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.model.RaceSettings;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchScoringRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.PlayoffRepository;
import org.ctc.domain.repository.PlayoffRoundRepository;
import org.ctc.domain.repository.PlayoffSeedRepository;
import org.ctc.domain.repository.PsnAliasRepository;
import org.ctc.domain.repository.RaceAttachmentRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.ctc.domain.repository.RaceScoringRepository;
import org.ctc.domain.repository.RaceSettingsRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonPhaseGroupRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.repository.TrackRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase 73-03 — DB-read orchestrator for the backup export pipeline.
 *
 * <p>Owns three responsibilities:
 * <ol>
 *   <li>{@link #countRowsPerTable()} — fast row-count probe to populate
 *       {@code manifest.table_counts} BEFORE serializing payload (RESEARCH §L-5
 *       "count first, serialize second").</li>
 *   <li>{@link #fetchAllForBackup(Class)} — per-entity dispatcher that delegates to
 *       the matching {@code findAllForBackup()} repository method
 *       (provided by Plan 73-02 on each of the 24 repositories).</li>
 *   <li>{@link #enumerateReferencedUploads()} — walks Team / SeasonTeam / Car / Track /
 *       RaceAttachment(type=FILE) rows, collects the referenced upload-path URLs,
 *       deduplicates them via {@link LinkedHashSet}, and filters out orphan paths
 *       that do not exist on disk (RESEARCH §Streaming ZIP Architecture Pattern 4).</li>
 * </ol>
 *
 * <p>Class-level {@code @Transactional(readOnly = true)} is non-negotiable: Plan 73-02
 * could not include {@code Season.tracks} in {@code SeasonRepository.findAllForBackup()}
 * because Hibernate rejects multi-bag fetches ({@code MultipleBagFetchException}).
 * The {@code tracks} collection must therefore materialize lazily inside the still-open
 * Hibernate session that this annotation provides.
 *
 * <p>Architectural note: this service exposes no HTTP-level state; the public methods
 * are pure read-aggregators consumed by {@code BackupArchiveService}.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class BackupExportService {

	private final BackupSchema backupSchema;

	private final CarRepository carRepository;
	private final TrackRepository trackRepository;
	private final RaceScoringRepository raceScoringRepository;
	private final MatchScoringRepository matchScoringRepository;
	private final DriverRepository driverRepository;
	private final PsnAliasRepository psnAliasRepository;
	private final TeamRepository teamRepository;
	private final SeasonRepository seasonRepository;
	private final SeasonPhaseRepository seasonPhaseRepository;
	private final SeasonPhaseGroupRepository seasonPhaseGroupRepository;
	private final PhaseTeamRepository phaseTeamRepository;
	private final SeasonTeamRepository seasonTeamRepository;
	private final SeasonDriverRepository seasonDriverRepository;
	private final PlayoffRepository playoffRepository;
	private final PlayoffRoundRepository playoffRoundRepository;
	private final PlayoffMatchupRepository playoffMatchupRepository;
	private final PlayoffSeedRepository playoffSeedRepository;
	private final MatchdayRepository matchdayRepository;
	private final MatchRepository matchRepository;
	private final RaceRepository raceRepository;
	private final RaceLineupRepository raceLineupRepository;
	private final RaceResultRepository raceResultRepository;
	private final RaceSettingsRepository raceSettingsRepository;
	private final RaceAttachmentRepository raceAttachmentRepository;

	private final String uploadDirRaw;

	private Map<Class<?>, JpaRepository<?, ?>> repositoriesByEntityClass;
	private Path uploadRoot;

	public BackupExportService(
			BackupSchema backupSchema,
			CarRepository carRepository,
			TrackRepository trackRepository,
			RaceScoringRepository raceScoringRepository,
			MatchScoringRepository matchScoringRepository,
			DriverRepository driverRepository,
			PsnAliasRepository psnAliasRepository,
			TeamRepository teamRepository,
			SeasonRepository seasonRepository,
			SeasonPhaseRepository seasonPhaseRepository,
			SeasonPhaseGroupRepository seasonPhaseGroupRepository,
			PhaseTeamRepository phaseTeamRepository,
			SeasonTeamRepository seasonTeamRepository,
			SeasonDriverRepository seasonDriverRepository,
			PlayoffRepository playoffRepository,
			PlayoffRoundRepository playoffRoundRepository,
			PlayoffMatchupRepository playoffMatchupRepository,
			PlayoffSeedRepository playoffSeedRepository,
			MatchdayRepository matchdayRepository,
			MatchRepository matchRepository,
			RaceRepository raceRepository,
			RaceLineupRepository raceLineupRepository,
			RaceResultRepository raceResultRepository,
			RaceSettingsRepository raceSettingsRepository,
			RaceAttachmentRepository raceAttachmentRepository,
			@Value("${app.upload-dir:data/dev/uploads}") String uploadDirRaw
	) {
		this.backupSchema = backupSchema;
		this.carRepository = carRepository;
		this.trackRepository = trackRepository;
		this.raceScoringRepository = raceScoringRepository;
		this.matchScoringRepository = matchScoringRepository;
		this.driverRepository = driverRepository;
		this.psnAliasRepository = psnAliasRepository;
		this.teamRepository = teamRepository;
		this.seasonRepository = seasonRepository;
		this.seasonPhaseRepository = seasonPhaseRepository;
		this.seasonPhaseGroupRepository = seasonPhaseGroupRepository;
		this.phaseTeamRepository = phaseTeamRepository;
		this.seasonTeamRepository = seasonTeamRepository;
		this.seasonDriverRepository = seasonDriverRepository;
		this.playoffRepository = playoffRepository;
		this.playoffRoundRepository = playoffRoundRepository;
		this.playoffMatchupRepository = playoffMatchupRepository;
		this.playoffSeedRepository = playoffSeedRepository;
		this.matchdayRepository = matchdayRepository;
		this.matchRepository = matchRepository;
		this.raceRepository = raceRepository;
		this.raceLineupRepository = raceLineupRepository;
		this.raceResultRepository = raceResultRepository;
		this.raceSettingsRepository = raceSettingsRepository;
		this.raceAttachmentRepository = raceAttachmentRepository;
		this.uploadDirRaw = uploadDirRaw;
	}

	@PostConstruct
	void initialize() {
		this.uploadRoot = Paths.get(uploadDirRaw).toAbsolutePath().normalize();
		this.repositoriesByEntityClass = new HashMap<>();
		this.repositoriesByEntityClass.put(Car.class, carRepository);
		this.repositoriesByEntityClass.put(Track.class, trackRepository);
		this.repositoriesByEntityClass.put(RaceScoring.class, raceScoringRepository);
		this.repositoriesByEntityClass.put(MatchScoring.class, matchScoringRepository);
		this.repositoriesByEntityClass.put(Driver.class, driverRepository);
		this.repositoriesByEntityClass.put(PsnAlias.class, psnAliasRepository);
		this.repositoriesByEntityClass.put(Team.class, teamRepository);
		this.repositoriesByEntityClass.put(Season.class, seasonRepository);
		this.repositoriesByEntityClass.put(SeasonPhase.class, seasonPhaseRepository);
		this.repositoriesByEntityClass.put(SeasonPhaseGroup.class, seasonPhaseGroupRepository);
		this.repositoriesByEntityClass.put(PhaseTeam.class, phaseTeamRepository);
		this.repositoriesByEntityClass.put(SeasonTeam.class, seasonTeamRepository);
		this.repositoriesByEntityClass.put(SeasonDriver.class, seasonDriverRepository);
		this.repositoriesByEntityClass.put(Playoff.class, playoffRepository);
		this.repositoriesByEntityClass.put(PlayoffRound.class, playoffRoundRepository);
		this.repositoriesByEntityClass.put(PlayoffMatchup.class, playoffMatchupRepository);
		this.repositoriesByEntityClass.put(PlayoffSeed.class, playoffSeedRepository);
		this.repositoriesByEntityClass.put(Matchday.class, matchdayRepository);
		this.repositoriesByEntityClass.put(Match.class, matchRepository);
		this.repositoriesByEntityClass.put(Race.class, raceRepository);
		this.repositoriesByEntityClass.put(RaceLineup.class, raceLineupRepository);
		this.repositoriesByEntityClass.put(RaceResult.class, raceResultRepository);
		this.repositoriesByEntityClass.put(RaceSettings.class, raceSettingsRepository);
		this.repositoriesByEntityClass.put(RaceAttachment.class, raceAttachmentRepository);
		log.info("BackupExportService initialized: uploadRoot={}, repositoryCount={}",
				uploadRoot, repositoriesByEntityClass.size());
	}

	/**
	 * Returns the row count for each entity in {@link BackupSchema#getExportOrder()} order,
	 * keyed by {@link EntityRef#tableName()}. The returned {@link LinkedHashMap} preserves
	 * the export ordering so that downstream consumers (the manifest's {@code tableCounts}
	 * field) emit deterministic JSON regardless of map-iteration semantics.
	 */
	public Map<String, Long> countRowsPerTable() {
		Map<String, Long> counts = new LinkedHashMap<>();
		for (EntityRef ref : backupSchema.getExportOrder()) {
			JpaRepository<?, ?> repo = lookupRepository(ref.entityClass());
			counts.put(ref.tableName(), repo.count());
		}
		log.debug("countRowsPerTable computed {} entries", counts.size());
		return counts;
	}

	/**
	 * Dispatches to the right repository's {@code findAllForBackup()} method for the
	 * given entity class. Throws {@link IllegalArgumentException} if no repository is
	 * registered — should never happen in production because the Phase 72 export order
	 * is the contract.
	 */
	public List<?> fetchAllForBackup(Class<?> entityClass) {
		JpaRepository<?, ?> repo = lookupRepository(entityClass);
		try {
			Method method = repo.getClass().getMethod("findAllForBackup");
			Object result = method.invoke(repo);
			return (List<?>) result;
		} catch (NoSuchMethodException ex) {
			throw new IllegalStateException(
					"Repository for " + entityClass.getSimpleName()
							+ " does not declare findAllForBackup() — "
							+ "Plan 73-02 contract violation", ex);
		} catch (ReflectiveOperationException ex) {
			Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
			if (cause instanceof RuntimeException re) {
				throw re;
			}
			throw new RuntimeException(
					"Reflective invocation of findAllForBackup() on " + entityClass.getSimpleName()
							+ " failed", cause);
		}
	}

	/**
	 * Enumerates every upload-file reference held by the database, deduplicates them,
	 * and filters out orphan references whose target file does not exist on disk.
	 *
	 * <p>Order of traversal: {@code Team.logoUrl} → {@code SeasonTeam.logoUrl} →
	 * {@code Car.imageUrl} → {@code Track.imageUrl} → {@code RaceAttachment.url}
	 * (only entries with {@code type=FILE} — {@code LINK} attachments hold absolute
	 * URLs that have nothing to do with the local upload directory).
	 *
	 * <p>Returns an immutable-in-spirit, insertion-ordered list of {@link UploadEntry}
	 * records. Each record carries both the absolute filesystem path (for the writer)
	 * and the relative path (for the ZIP entry name).
	 */
	public List<UploadEntry> enumerateReferencedUploads() {
		Set<String> relativePaths = new LinkedHashSet<>();

		teamRepository.findAll().forEach(team ->
				addIfPresent(relativePaths, team.getLogoUrl()));
		seasonTeamRepository.findAll().forEach(seasonTeam ->
				addIfPresent(relativePaths, seasonTeam.getLogoUrl()));
		carRepository.findAll().forEach(car ->
				addIfPresent(relativePaths, car.getImageUrl()));
		trackRepository.findAll().forEach(track ->
				addIfPresent(relativePaths, track.getImageUrl()));
		raceAttachmentRepository.findAll().forEach(attachment -> {
			if (attachment.getType() == AttachmentType.FILE) {
				addIfPresent(relativePaths, attachment.getUrl());
			}
		});

		List<UploadEntry> entries = new ArrayList<>();
		for (String relative : relativePaths) {
			Path absolute = uploadRoot.resolve(relative).toAbsolutePath().normalize();
			if (!Files.exists(absolute)) {
				log.warn("Skipping orphan upload reference: {} (resolved to {})",
						relative, absolute);
				continue;
			}
			entries.add(new UploadEntry(absolute, relative));
		}
		log.debug("enumerateReferencedUploads found {} unique on-disk uploads (skipped {} orphans)",
				entries.size(), relativePaths.size() - entries.size());
		return entries;
	}

	/**
	 * Strips the {@code /uploads/} prefix (if present) and adds the relative remainder
	 * to {@code set}. Blank or null URLs and URLs that do not point at the local upload
	 * mount are ignored. Defense-in-depth against URL strings whose {@code "/uploads/"}
	 * convention has drifted (e.g. an entity carrying an external CDN reference).
	 */
	private static void addIfPresent(Set<String> set, String url) {
		if (url == null || url.isBlank()) {
			return;
		}
		String prefix = "/uploads/";
		if (!url.startsWith(prefix)) {
			return;
		}
		String relative = url.substring(prefix.length());
		if (relative.isBlank()) {
			return;
		}
		set.add(relative);
	}

	private JpaRepository<?, ?> lookupRepository(Class<?> entityClass) {
		JpaRepository<?, ?> repo = repositoriesByEntityClass.get(entityClass);
		if (repo == null) {
			throw new IllegalArgumentException(
					"No repository registered for entity class " + entityClass.getName()
							+ " — must be one of the 24 BackupSchema.getExportOrder() entities");
		}
		return repo;
	}
}
