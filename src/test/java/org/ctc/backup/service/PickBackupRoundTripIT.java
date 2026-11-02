package org.ctc.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import org.ctc.backup.exception.BackupImportException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.picks.SubmissionReceipt;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.*;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@CtcDevSpringBootContext
@Tag("integration")
class PickBackupRoundTripIT {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Path ROOT = SitegenTestDir.create("private-picks-backup");
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
    @Autowired CarRepository cars;
    @Autowired TrackRepository tracks;
    @Autowired RaceRepository races;
    @Autowired MatchdayPickRepository picks;
    @Autowired MatchdayPickRevisionRepository revisions;
    @Autowired MatchdayPickDecisionRepository decisions;
    @Autowired TeamPickChannelBindingRepository bindings;
    @Autowired PickSubmissionService submissions;
    @Autowired PickReviewService reviews;
    @Autowired PickReservationService reservations;
    @Autowired PickSessionEpoch epoch;
    @Autowired BackupArchiveService archives;
    @MockitoSpyBean BackupImportService imports;
    @MockitoBean PickCollectionStatus collection;
    final ManagementPickActor manager = new ManagementPickActor("test-admin");
    TestHelper.SeasonFixture fixture;
    List<UUID> carIds;
    List<UUID> trackIds;
    UUID pendingRace;
    UUID bindingId;
    PickPayload corrected;
    SubmissionReceipt original;
    byte[] legacy;

    @BeforeEach
    void setUp() throws Exception {
        // given
        Files.createDirectories(ROOT.resolve("uploads"));
        tx.executeWithoutResult(status -> {
            fixture = helper.createFullSeasonFixture("TPB" + UUID.randomUUID().toString().substring(0, 7));
            fixture.race().setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0));
            fixture.matchday().setPickDeadline(LocalDateTime.of(2035, 1, 1, 12, 0));
            var car1 = cars.save(new Car("Test pick backup", UUID.randomUUID().toString()));
            var car2 = cars.save(new Car("Test pick backup", UUID.randomUUID().toString()));
            var track1 = tracks.save(new Track("Test pick backup " + UUID.randomUUID()));
            var track2 = tracks.save(new Track("Test pick backup " + UUID.randomUUID()));
            fixture.season().getCars().addAll(List.of(car1, car2));
            fixture.season().getTracks().addAll(List.of(track1, track2));
            carIds = List.of(car1.getId(), car2.getId());
            trackIds = List.of(track1.getId(), track2.getId());
        });
        legacy = export();
        when(collection.isAvailable()).thenReturn(true);
        when(collection.isConfidentialityReady()).thenReturn(true);
        tx.executeWithoutResult(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            season.setPicksCollectionEnabled(true);
            races.findById(fixture.race().getId()).orElseThrow().setPicksManaged(true);
        });
        bindingId = tx.execute(status -> bindings.saveAndFlush(new TeamPickChannelBinding(
                seasons.findById(fixture.season().getId()).orElseThrow(), teams.findById(fixture.homeTeam().getId()).orElseThrow(),
                "100000000000000001", "200000000000000002")).getId());
        original = submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor(fixture.race().getId()), payload(0, "S01, R03, S02"));
        reviews.returnForCorrection(fixture.race().getId(), 1, manager, "Check weather slot order");
        submissions.submit(fixture.race().getId(), 2, UUID.randomUUID(), actor(fixture.race().getId()), payload(0, "S01, R03, S02"));
        reviews.approve(fixture.race().getId(), 3, 2, manager, "Checked revision two");
        corrected = payload(1, "S02, R04, S03");
        reviews.correct(fixture.race().getId(), 4, manager, "Management track correction", corrected);
        pendingRace = tx.execute(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            var day = helper.createMatchday(season, "Test pending target", 2);
            day.setPickDeadline(LocalDateTime.of(2035, 1, 2, 12, 0));
            var match = helper.createMatch(day, teams.findById(fixture.homeTeam().getId()).orElseThrow(), teams.findById(fixture.awayTeam().getId()).orElseThrow());
            var race = helper.createRace(day, match);
            race.setPicksManaged(true);
            race.setDateTime(LocalDateTime.of(2035, 1, 2, 20, 0));
            return race.getId();
        });
        submissions.submit(pendingRace, 0, UUID.randomUUID(), actor(pendingRace), payload(0, "S01"));
        reviews.returnForCorrection(pendingRace, 1, manager, "Please confirm");
        reviews.close(pendingRace, 2, manager, "Review in progress");
        clearInvocations(imports);
    }

    @AfterEach
    void cleanUp() {
        tx.executeWithoutResult(status -> {
            helper.deleteSeasonCascade(seasons.findById(fixture.season().getId()).orElseThrow());
            seasons.flush();
            cars.deleteAllById(carIds);
            tracks.deleteAllById(trackIds);
            teams.deleteAllById(List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId()));
        });
    }

    @Test
    void givenApprovedCorrectionAndClosedPendingHistory_whenRestoring_thenPreserveExactReceiptStateAndReservations() throws Exception {
        // given
        byte[] archive = export();
        long generation = epoch.getAsLong();
        reviews.correct(fixture.race().getId(), 5, manager, "After export", payload(1, "Changed after export"));
        reviews.returnForCorrection(pendingRace, 3, manager, "After export opening");
        when(collection.isAvailable()).thenReturn(false);
        // when
        var preview = imports.stage(upload(archive));
        imports.execute(preview.stagingId());
        // then
        assertThat(epoch.getAsLong()).isEqualTo(generation + 1);
        verify(imports).wipeAllTables(anyMap());
        tx.executeWithoutResult(status -> {
            var restoredBinding = bindings.findById(bindingId).orElseThrow();
            assertThat(restoredBinding.getGuildId()).isEqualTo("100000000000000001");
            assertThat(restoredBinding.getChannelId()).isEqualTo("200000000000000002");
            assertThat(restoredBinding.getSeason().getId()).isEqualTo(fixture.season().getId());
            assertThat(restoredBinding.getTeam().getId()).isEqualTo(fixture.homeTeam().getId());
            var approved = picks.findByRaceId(fixture.race().getId()).orElseThrow();
            assertThat(approved.getVersion()).isEqualTo(5);
            assertThat(approved.getState()).isEqualTo(PickState.APPROVED);
            assertThat(approved.getApprovedRevisionNumber()).isEqualTo(2);
            assertThat(approved.isManualOpen()).isFalse();
            assertThat(approved.getRace().getSettings().getWeather()).isEqualTo(corrected.weather());
            assertThat(approved.getRace().getCar().getId()).isEqualTo(corrected.carId());
            assertThat(approved.getRace().getTrack().getId()).isEqualTo(corrected.trackId());
            var pending = picks.findByRaceId(pendingRace).orElseThrow();
            assertThat(pending.getVersion()).isEqualTo(3);
            assertThat(pending.getState()).isEqualTo(PickState.RETURNED);
            assertThat(pending.isManualClosed()).isTrue();
            assertThat(pending.isManualOpen()).isFalse();
            assertThat(approved.getSeason().isPicksCollectionEnabled()).isTrue();
            assertThat(pending.getRace().isPicksManaged()).isTrue();
            assertThat(revisions.findByRequestId(original.requestId()).orElseThrow().receipt()).isEqualTo(original);
            assertThat(reservations.pending(fixture.season().getId(), true)).containsExactly(
                    new PickReservationService.Reservation(pendingRace, fixture.homeTeam().getId(), carIds.getFirst(), null, trackIds.getFirst()));
            var history = decisions.findAll().stream().filter(decision -> decision.getPick().getId().equals(approved.getId())).toList();
            assertThat(history).hasSize(3);
            assertThat(history.stream().filter(decision -> decision.getKind() == PickDecisionKind.CORRECTION).findFirst().orElseThrow().getCorrectionPayload()).isEqualTo(corrected);
        });
        assertThat(submissions.submit(fixture.race().getId(), 0, original.requestId(), actor(fixture.race().getId()), payload(0, "S01"))).isEqualTo(original);
    }

    @Test
    void givenPrivateArchiveBeforeConfidentialityRelease_whenStagingOrExecuting_thenRejectBeforeAnyWipe() throws Exception {
        // given
        byte[] archive = export();
        when(collection.isConfidentialityReady()).thenReturn(false);
        // when / then
        assertThatThrownBy(() -> imports.stage(upload(archive))).isInstanceOfSatisfying(BackupArchiveException.class, failure -> {
            assertThat(failure.reason()).isEqualTo(BackupArchiveException.Reason.SCHEMA_MISMATCH);
            assertThat(failure.getMessage()).contains("confidentiality");
        });
        verify(imports, never()).wipeAllTables(anyMap());
        when(collection.isConfidentialityReady()).thenReturn(true);
        var preview = imports.stage(upload(archive));
        when(collection.isConfidentialityReady()).thenReturn(false);
        clearInvocations(imports);
        assertThatThrownBy(() -> imports.execute(preview.stagingId())).isInstanceOf(BackupImportException.class).cause()
                .isInstanceOfSatisfying(BackupArchiveException.class, failure -> {
                    assertThat(failure.reason()).isEqualTo(BackupArchiveException.Reason.SCHEMA_MISMATCH);
                    assertThat(failure.getMessage()).contains("confidentiality");
                });
        verify(imports, never()).wipeAllTables(anyMap());
        tx.executeWithoutResult(status -> assertThat(picks.findByRaceId(fixture.race().getId()).orElseThrow().getVersion()).isEqualTo(5));
        imports.deleteStagingFile(preview.stagingId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-revisions", "missing-decisions", "unmanaged", "cross-team", "orphan-track", "duplicate-request", "forged-version", "changed-settings", "dropped-correction", "disguised-v4"})
    void givenMalformedPrivateHistory_whenStagingOrExecuting_thenRejectBeforeWipeAndKeepLiveHistory(String kind) throws Exception {
        // given
        byte[] valid = export();
        long generation = epoch.getAsLong();
        byte[] broken = malformed(valid, kind);
        // when / then
        assertThatThrownBy(() -> imports.stage(upload(broken))).isInstanceOfSatisfying(BackupArchiveException.class, failure -> {
            assertThat(failure.reason()).isEqualTo(BackupArchiveException.Reason.DATA_MISMATCH);
            assertThat(failure.getMessage().toLowerCase(Locale.ROOT)).contains("pick");
        });
        verify(imports, never()).wipeAllTables(anyMap());
        var preview = imports.stage(upload(valid));
        Files.write(ROOT.resolve("staging/upload-" + preview.stagingId() + ".zip"), broken);
        clearInvocations(imports);
        assertThatThrownBy(() -> imports.execute(preview.stagingId())).isInstanceOf(BackupImportException.class).cause()
                .isInstanceOfSatisfying(BackupArchiveException.class, failure -> assertThat(failure.reason()).isEqualTo(BackupArchiveException.Reason.DATA_MISMATCH));
        verify(imports, never()).wipeAllTables(anyMap());
        tx.executeWithoutResult(status -> {
            assertThat(picks.findByRaceId(fixture.race().getId()).orElseThrow().getVersion()).isEqualTo(5);
            assertThat(picks.findByRaceId(pendingRace).orElseThrow().isManualClosed()).isTrue();
            assertThat(revisions.findByRequestId(original.requestId()).orElseThrow().receipt()).isEqualTo(original);
        });
        assertThat(epoch.getAsLong()).isEqualTo(generation);
        imports.deleteStagingFile(preview.stagingId());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void givenSupportedOldBackup_whenRestoring_thenDefaultNewFlagsOffAndLeaveHistoryEmpty(int version) throws Exception {
        // given
        var files = unzip(legacy);
        var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json"));
        manifest.put("schema_version", version);
        var absent = new ArrayList<>(List.of("team_pick_channel_bindings"));
        if (version < 5) absent.addAll(List.of("matchday_picks", "matchday_pick_revisions", "matchday_pick_decisions"));
        if (version <= 3) absent.addAll(List.of("season_car_pool_entries", "season_car_pool_clauses", "season_car_pool_members"));
        if (version <= 2) absent.add("site_slugs");
        if (version == 1) absent.addAll(List.of("discord_global_config", "discord_post"));
        for (String table : absent) {
            files.remove("data/" + table.replace('_', '-') + ".json");
            ((ObjectNode) manifest.get("table_counts")).remove(table);
        }
        for (String table : version < 5 ? List.of("seasons", "races") : List.<String>of()) {
            var rows = (ArrayNode) JSON.readTree(files.get("data/" + table + ".json"));
            for (var row : rows) ((ObjectNode) row).remove(table.equals("seasons") ? "picksCollectionEnabled" : "picksManaged");
            files.put("data/" + table + ".json", JSON.writeValueAsBytes(rows));
        }
        files.put("manifest.json", JSON.writeValueAsBytes(manifest));
        // when
        var preview = imports.stage(upload(zip(files)));
        imports.execute(preview.stagingId());
        // then
        tx.executeWithoutResult(status -> {
            assertThat(seasons.findById(fixture.season().getId()).orElseThrow().isPicksCollectionEnabled()).isFalse();
            assertThat(races.findById(fixture.race().getId()).orElseThrow().isPicksManaged()).isFalse();
            assertThat(picks.findBySeasonId(fixture.season().getId())).isEmpty();
            assertThat(bindings.findById(bindingId)).isEmpty();
        });
    }

    @Test
    void givenSchemaFiveHistoryWithoutBindings_whenRestoring_thenPreserveReceiptsAndGrantNoChannel() throws Exception {
        // given
        var files = unzip(export());
        var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json"));
        manifest.put("schema_version", 5);
        ((ObjectNode) manifest.get("table_counts")).remove("team_pick_channel_bindings");
        files.remove("data/team-pick-channel-bindings.json");
        files.put("manifest.json", JSON.writeValueAsBytes(manifest));
        // when
        var preview = imports.stage(upload(zip(files)));
        imports.execute(preview.stagingId());
        // then
        tx.executeWithoutResult(status -> {
            assertThat(bindings.findById(bindingId)).isEmpty();
            assertThat(revisions.findByRequestId(original.requestId()).orElseThrow().receipt()).isEqualTo(original);
        });
    }

    private PickPayload payload(int option, String weather) {
        return new PickPayload(trackIds.get(option), carIds.get(option), null, 12, 2, 3, 4, PickInitialFuel.DEFAULT, 1,
                TimeProgressionMode.EQUAL_CONDITIONS, null, weather, "Sunset", Set.of(PickTyre.RS, PickTyre.RM), Set.of(PickTyre.RM));
    }
    private AuthorizedPickActor actor(UUID race) {
        return new AuthorizedPickActor(race, fixture.season().getId(), fixture.homeTeam().getId(), "12345");
    }
    private byte[] export() throws IOException {
        var output = new ByteArrayOutputStream();
        archives.writeZip(output, Instant.now());
        return output.toByteArray();
    }
    private MockMultipartFile upload(byte[] bytes) { return new MockMultipartFile("file", "test-private-picks.zip", "application/zip", bytes); }
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
    private byte[] malformed(byte[] valid, String kind) throws IOException {
        var files = unzip(valid);
        var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json"));
        if (kind.startsWith("missing-")) {
            files.remove("data/matchday-pick-" + (kind.equals("missing-revisions") ? "revisions" : "decisions") + ".json");
        } else if (kind.equals("disguised-v4")) {
            manifest.put("schema_version", 4);
        } else {
            String table = switch (kind) {
                case "unmanaged" -> "races";
                case "cross-team", "forged-version" -> "matchday-picks";
                case "orphan-track", "duplicate-request" -> "matchday-pick-revisions";
                case "changed-settings" -> "race-settings";
                default -> "matchday-pick-decisions";
            };
            String file = "data/" + table + ".json";
            var rows = (ArrayNode) JSON.readTree(files.get(file));
            if (kind.equals("duplicate-request")) {
                ((ObjectNode) rows.get(1)).put("requestId", rows.get(0).path("requestId").asText());
            } else if (kind.equals("orphan-track")) {
                ((ObjectNode) rows.get(0).get("payload")).put("trackId", UUID.randomUUID().toString());
            } else if (kind.equals("dropped-correction")) {
                for (int index = 0; index < rows.size(); index++) if (rows.get(index).path("kind").asText().equals("CORRECTION")) { rows.remove(index); break; }
                var counts = (ObjectNode) manifest.get("table_counts");
                counts.put("matchday_pick_decisions", counts.path("matchday_pick_decisions").asLong() - 1);
            } else {
                for (var row : rows) {
                    String identity = row.path(kind.equals("changed-settings") ? "race" : table.equals("races") ? "id" : "race").asText();
                    if (!identity.equals(fixture.race().getId().toString())) continue;
                    switch (kind) {
                        case "unmanaged" -> ((ObjectNode) row).put("picksManaged", false);
                        case "cross-team" -> ((ObjectNode) row).put("team", fixture.awayTeam().getId().toString());
                        case "forged-version" -> ((ObjectNode) row).put("version", 999);
                        case "changed-settings" -> ((ObjectNode) row).put("weather", "Unrecorded correction");
                        default -> throw new AssertionError(kind);
                    }
                    break;
                }
            }
            files.put(file, JSON.writeValueAsBytes(rows));
        }
        files.put("manifest.json", JSON.writeValueAsBytes(manifest));
        return zip(files);
    }
}
