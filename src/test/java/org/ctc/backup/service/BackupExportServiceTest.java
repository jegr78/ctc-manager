package org.ctc.backup.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Phase 73-03 — Mockito unit test for {@link BackupExportService}.
 *
 * <p>Stubs all 24 repositories, drives the three public methods, and asserts:
 * <ul>
 *   <li>{@code countRowsPerTable()} returns a {@link LinkedHashMap} keyed by
 *       {@link EntityRef#tableName()} in the order of {@link BackupSchema#getExportOrder()}.</li>
 *   <li>{@code fetchAllForBackup(Team.class)} dispatches to {@code TeamRepository.findAllForBackup()}.</li>
 *   <li>{@code fetchAllForBackup(Unknown.class)} throws {@link IllegalArgumentException}.</li>
 *   <li>{@code enumerateReferencedUploads()} deduplicates references that resolve
 *       to the same on-disk path and skips orphan paths.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BackupExportServiceTest {

	@Mock private BackupSchema backupSchema;
	@Mock private CarRepository carRepository;
	@Mock private TrackRepository trackRepository;
	@Mock private RaceScoringRepository raceScoringRepository;
	@Mock private MatchScoringRepository matchScoringRepository;
	@Mock private DriverRepository driverRepository;
	@Mock private PsnAliasRepository psnAliasRepository;
	@Mock private TeamRepository teamRepository;
	@Mock private SeasonRepository seasonRepository;
	@Mock private SeasonPhaseRepository seasonPhaseRepository;
	@Mock private SeasonPhaseGroupRepository seasonPhaseGroupRepository;
	@Mock private PhaseTeamRepository phaseTeamRepository;
	@Mock private SeasonTeamRepository seasonTeamRepository;
	@Mock private SeasonDriverRepository seasonDriverRepository;
	@Mock private PlayoffRepository playoffRepository;
	@Mock private PlayoffRoundRepository playoffRoundRepository;
	@Mock private PlayoffMatchupRepository playoffMatchupRepository;
	@Mock private PlayoffSeedRepository playoffSeedRepository;
	@Mock private MatchdayRepository matchdayRepository;
	@Mock private MatchRepository matchRepository;
	@Mock private RaceRepository raceRepository;
	@Mock private RaceLineupRepository raceLineupRepository;
	@Mock private RaceResultRepository raceResultRepository;
	@Mock private RaceSettingsRepository raceSettingsRepository;
	@Mock private RaceAttachmentRepository raceAttachmentRepository;

	private BackupExportService service;
	private Path uploadRoot;

	@BeforeEach
	void setUp(@org.junit.jupiter.api.io.TempDir Path tempDir) {
		uploadRoot = tempDir;
		service = new BackupExportService(
				backupSchema,
				carRepository, trackRepository, raceScoringRepository, matchScoringRepository,
				driverRepository, psnAliasRepository, teamRepository, seasonRepository,
				seasonPhaseRepository, seasonPhaseGroupRepository, phaseTeamRepository,
				seasonTeamRepository, seasonDriverRepository, playoffRepository,
				playoffRoundRepository, playoffMatchupRepository, playoffSeedRepository,
				matchdayRepository, matchRepository, raceRepository, raceLineupRepository,
				raceResultRepository, raceSettingsRepository, raceAttachmentRepository,
				tempDir.toString()
		);
		ReflectionTestUtils.invokeMethod(service, "initialize");
	}

	@Test
	void givenStubbedRepositories_whenCountRowsPerTable_thenReturnsLinkedHashMapKeyedByTableNameInExportOrder() {
		// given
		List<EntityRef> order = List.of(
				new EntityRef(Car.class, "cars", "data/cars.json"),
				new EntityRef(Team.class, "teams", "data/teams.json"),
				new EntityRef(Season.class, "seasons", "data/seasons.json")
		);
		when(backupSchema.getExportOrder()).thenReturn(order);
		when(carRepository.count()).thenReturn(7L);
		when(teamRepository.count()).thenReturn(3L);
		when(seasonRepository.count()).thenReturn(2L);

		// when
		Map<String, Long> counts = service.countRowsPerTable();

		// then
		assertThat(counts).isInstanceOf(LinkedHashMap.class);
		assertThat(counts).containsExactly(
				Map.entry("cars", 7L),
				Map.entry("teams", 3L),
				Map.entry("seasons", 2L)
		);
	}

	@Test
	void givenStubbedTeamRepository_whenFetchAllForBackupTeamClass_thenDispatchesToTeamRepository() {
		// given
		Team team = new Team("Alpha", "ALF");
		when(teamRepository.findAllForBackup()).thenReturn(List.of(team));

		// when
		List<?> result = service.fetchAllForBackup(Team.class);

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isSameAs(team);
	}

	@Test
	void givenUnknownEntityClass_whenFetchAllForBackup_thenThrowsIllegalArgumentException() {
		// when / then
		assertThatThrownBy(() -> service.fetchAllForBackup(String.class))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("String");
	}

	@Test
	void givenTeamAndSeasonTeamShareLogoUrl_whenEnumerateReferencedUploads_thenSingleEntryReturned() throws Exception {
		// given — two entities pointing at the same upload path; one orphan reference
		Path logoFile = uploadRoot.resolve("teams/alf/logo.png");
		Files.createDirectories(logoFile.getParent());
		Files.writeString(logoFile, "image-bytes");

		Team team = new Team("Alpha", "ALF");
		team.setLogoUrl("/uploads/teams/alf/logo.png");

		SeasonTeam seasonTeam = new SeasonTeam();
		seasonTeam.setLogoUrl("/uploads/teams/alf/logo.png");

		Team orphanTeam = new Team("Orphan", "ORP");
		orphanTeam.setLogoUrl("/uploads/teams/orp/missing.png");

		lenient().when(teamRepository.findAll()).thenReturn(List.of(team, orphanTeam));
		lenient().when(seasonTeamRepository.findAll()).thenReturn(List.of(seasonTeam));
		lenient().when(carRepository.findAll()).thenReturn(List.of());
		lenient().when(trackRepository.findAll()).thenReturn(List.of());
		lenient().when(raceAttachmentRepository.findAll()).thenReturn(List.of());

		// when
		List<UploadEntry> entries = service.enumerateReferencedUploads();

		// then
		assertThat(entries).hasSize(1);
		assertThat(entries.get(0).relativePath()).isEqualTo("teams/alf/logo.png");
		assertThat(entries.get(0).absolutePath()).isEqualTo(logoFile.toAbsolutePath().normalize());
	}

	@Test
	void givenRaceAttachmentTypeLink_whenEnumerateReferencedUploads_thenAttachmentLinkIsSkipped() throws Exception {
		// given — only LINK-type attachments (no on-disk file) — must be skipped without filesystem probe
		Race race = new Race();
		RaceAttachment link = new RaceAttachment(race, AttachmentType.LINK, "External", "https://example.com/foo");

		lenient().when(teamRepository.findAll()).thenReturn(List.of());
		lenient().when(seasonTeamRepository.findAll()).thenReturn(List.of());
		lenient().when(carRepository.findAll()).thenReturn(List.of());
		lenient().when(trackRepository.findAll()).thenReturn(List.of());
		lenient().when(raceAttachmentRepository.findAll()).thenReturn(List.of(link));

		// when
		List<UploadEntry> entries = service.enumerateReferencedUploads();

		// then
		assertThat(entries).isEmpty();
	}

	@Test
	void givenPathTraversalLogoUrl_whenEnumerateReferencedUploads_thenRejectedAtEnumeratorBoundary() throws Exception {
		// given — a malicious DB row whose logoUrl escapes uploadRoot via ".."
		Team malicious = new Team("Evil", "EVL");
		malicious.setLogoUrl("/uploads/../../../../etc/passwd");

		lenient().when(teamRepository.findAll()).thenReturn(List.of(malicious));
		lenient().when(seasonTeamRepository.findAll()).thenReturn(List.of());
		lenient().when(carRepository.findAll()).thenReturn(List.of());
		lenient().when(trackRepository.findAll()).thenReturn(List.of());
		lenient().when(raceAttachmentRepository.findAll()).thenReturn(List.of());

		// when
		List<UploadEntry> entries = service.enumerateReferencedUploads();

		// then — the enumerator MUST reject path-traversal references before any
		// Files.exists() probe so filesystem state outside uploadRoot is never leaked.
		assertThat(entries).isEmpty();
	}

	@Test
	void givenCarAndTrackWithImages_whenEnumerateReferencedUploads_thenBothPresentInDeclaredOrder() throws Exception {
		// given
		Path carFile = uploadRoot.resolve("cars/car-1.png");
		Path trackFile = uploadRoot.resolve("tracks/track-1.png");
		Files.createDirectories(carFile.getParent());
		Files.createDirectories(trackFile.getParent());
		Files.writeString(carFile, "car-bytes");
		Files.writeString(trackFile, "track-bytes");

		Car car = new Car();
		car.setImageUrl("/uploads/cars/car-1.png");
		Track track = new Track();
		track.setImageUrl("/uploads/tracks/track-1.png");

		lenient().when(teamRepository.findAll()).thenReturn(List.of());
		lenient().when(seasonTeamRepository.findAll()).thenReturn(List.of());
		lenient().when(carRepository.findAll()).thenReturn(List.of(car));
		lenient().when(trackRepository.findAll()).thenReturn(List.of(track));
		lenient().when(raceAttachmentRepository.findAll()).thenReturn(List.of());

		// when
		List<UploadEntry> entries = service.enumerateReferencedUploads();

		// then
		assertThat(entries).extracting(UploadEntry::relativePath)
				.containsExactly("cars/car-1.png", "tracks/track-1.png");
	}

	/* ------------------------------------------------------------------
	 * Unused mock fields silence MockitoExtension's "unused stubbing"
	 * detection when the test doesn't exercise the entity. We keep the
	 * full 24-repository field roster on the service so the production
	 * constructor signature matches Spring's DI shape one-to-one.
	 * ------------------------------------------------------------------ */
	@SuppressWarnings("unused")
	private static class TouchAllMocks {
		Driver d; PsnAlias p; Season s; SeasonPhase sp; SeasonPhaseGroup spg;
		PhaseTeam pt; SeasonTeam st; SeasonDriver sd; Playoff po; PlayoffRound pr;
		PlayoffMatchup pm; PlayoffSeed ps; Matchday md; Match mt; Race rc;
		RaceLineup rl; RaceResult rr; RaceSettings rs; RaceScoring rsc;
		MatchScoring msc;
	}
}
