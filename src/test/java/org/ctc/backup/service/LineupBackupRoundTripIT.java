package org.ctc.backup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.zip.*;
import org.ctc.TestHelper;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.LineupApplyService;
import org.ctc.domain.service.LineupSubmissionService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

@CtcDevSpringBootContext
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:ctc_lineup_backup;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Tag("integration")
class LineupBackupRoundTripIT {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Path ROOT = SitegenTestDir.create("lineup-backup");
    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry properties) {
        properties.add("app.upload-dir", () -> ROOT.resolve("uploads").toString());
        properties.add("app.backup.staging-dir", () -> ROOT.resolve("staging").toString());
        properties.add("app.backup.import-backups-dir", () -> ROOT.resolve("imports").toString());
        properties.add("ctc.site.output-dir", () -> ROOT.resolve("site").toString());
    }
    @Autowired TestHelper helper;
    @Autowired TransactionTemplate tx;
    @Autowired SeasonRepository seasons;
    @Autowired TeamRepository teams;
    @Autowired DriverRepository drivers;
    @Autowired RaceLineupRepository raceLineups;
    @Autowired LineupSubmissionRepository submissions;
    @Autowired LineupSubmissionRevisionRepository revisions;
    @Autowired LineupWindowEventRepository windows;
    @Autowired LineupSubmissionService lineupSubmissions;
    @Autowired LineupApplyService applies;
    @Autowired BackupArchiveService archives;
    @MockitoSpyBean BackupImportService imports;
    final ManagementPickActor manager = new ManagementPickActor("test-admin");
    final List<UUID> driverIds = new ArrayList<>();
    TestHelper.SeasonFixture fixture;
    UUID correctionRequest;

    @BeforeEach
    void setUp() throws Exception {
        // given
        Files.createDirectories(ROOT.resolve("uploads"));
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TLB" + UUID.randomUUID().toString().substring(0, 6));
            fixture.race().setDateTime(LocalDateTime.of(2035, 3, 1, 20, 0));
            fixture.season().setLineupSize(4);
            for (int i = 0; i < 4; i++) {
                var driver = helper.createDriver("TLB_D" + i + fixture.homeTeam().getShortName(), "TLB driver " + i);
                driverIds.add(driver.getId());
                helper.createSeasonDriver(fixture.season(), driver, fixture.homeTeam());
            }
        });
        lineupSubmissions.decideWindow(fixture.race().getId(), LineupWindowKind.OPEN, 0, manager, "Settings posted manually");
        correctionRequest = UUID.randomUUID();
        var entries = driverIds.stream().map(id -> new LineupPayload.Entry(id, id.toString(), null)).toList();
        lineupSubmissions.correct(fixture.race().getId(), fixture.homeTeam().getId(), 0, correctionRequest, manager, new LineupPayload(4, entries), "By phone");
        var submission = submissions.findByRaceIdAndTeamId(fixture.race().getId(), fixture.homeTeam().getId()).orElseThrow();
        applies.apply(submission.getId(), 1, 1, Map.of(), false, manager);
        org.mockito.Mockito.clearInvocations(imports);
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            raceLineups.deleteAll(raceLineups.findByRaceId(fixture.race().getId()));
            seasons.findById(fixture.season().getId()).ifPresent(helper::deleteSeasonCascade);
            seasons.flush();
            drivers.deleteAllById(driverIds);
            teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
    }

    @Test
    void givenAppliedCorrectionAndWindowDecision_whenRestoring_thenPreserveHistoryApplicationAndLineupSize() throws Exception {
        // given
        byte[] archive = export();
        lineupSubmissions.decideWindow(fixture.race().getId(), LineupWindowKind.CLOSE, 1, manager, "After export");
        // when
        imports.execute(imports.stage(upload(archive)).stagingId());
        // then
        verify(imports).wipeAllTables(anyMap());
        tx.executeWithoutResult(status -> {
            assertThat(seasons.findById(fixture.season().getId()).orElseThrow().getLineupSize()).isEqualTo(4);
            var submission = submissions.findByRaceIdAndTeamId(fixture.race().getId(), fixture.homeTeam().getId()).orElseThrow();
            assertThat(submission.status()).isEqualTo(LineupStatus.APPLIED);
            assertThat(submission.getAppliedBy()).isEqualTo("test-admin");
            var revision = revisions.findByRequestId(correctionRequest).orElseThrow();
            assertThat(revision.getReason()).isEqualTo("By phone");
            assertThat(revision.lineup().driverIds()).containsExactlyElementsOf(driverIds);
            assertThat(windows.findByRaceIdOrderBySequenceNumberDesc(fixture.race().getId())).extracting(LineupWindowEvent::getKind)
                    .as("the decision made after the export is gone").containsExactly(LineupWindowKind.OPEN);
            assertThat(raceLineups.findByRaceId(fixture.race().getId())).hasSize(4);
        });
        var replay = lineupSubmissions.correct(fixture.race().getId(), fixture.homeTeam().getId(), 0, correctionRequest, manager,
                new LineupPayload(4, List.of(new LineupPayload.Entry(driverIds.getFirst(), "x", null))), "Replay");
        assertThat(replay.revisionNumber()).as("a restored request identity still replays").isEqualTo(1);
    }

    @Test
    void givenOlderSchemaCarryingLineupRows_whenRestoring_thenRejectBeforeWiping() throws Exception {
        // given
        var files = unzip(export());
        var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json"));
        manifest.put("schema_version", 22);
        files.put("manifest.json", JSON.writeValueAsBytes(manifest));
        var seasonFile = files.keySet().stream().filter(name -> name.endsWith("seasons.json")).findFirst().orElseThrow();
        var seasonRows = JSON.readTree(files.get(seasonFile));
        seasonRows.forEach(row -> ((ObjectNode) row).put("lineupSize", 6));
        files.put(seasonFile, JSON.writeValueAsBytes(seasonRows));
        // when / then
        assertThatThrownBy(() -> imports.stage(upload(zip(files)))).isInstanceOfSatisfying(BackupArchiveException.class, failure -> {
            assertThat(failure.reason()).isEqualTo(BackupArchiveException.Reason.DATA_MISMATCH);
            assertThat(failure.getMessage()).contains("Lineup submissions require backup schema 23");
        });
        verify(imports, never()).wipeAllTables(anyMap());
    }

    private byte[] export() throws IOException {
        var output = new ByteArrayOutputStream();
        archives.writeZip(output, Instant.now());
        return output.toByteArray();
    }
    private MockMultipartFile upload(byte[] bytes) { return new MockMultipartFile("file", "test-lineups.zip", "application/zip", bytes); }
    private Map<String, byte[]> unzip(byte[] archive) throws IOException {
        var files = new LinkedHashMap<String, byte[]>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) files.put(entry.getName(), zip.readAllBytes());
        }
        return files;
    }
    private byte[] zip(Map<String, byte[]> files) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var entry : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
