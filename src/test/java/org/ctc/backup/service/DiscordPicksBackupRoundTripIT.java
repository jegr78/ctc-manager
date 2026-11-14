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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@CtcDevSpringBootContext
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:ctc_private_picks_backup;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Tag("integration")
class DiscordPicksBackupRoundTripIT {
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
    @Autowired jakarta.persistence.EntityManager entityManager;
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
    @Autowired org.ctc.HistoricalPickCorrections corrections;
    @Autowired PickReservationService reservations;
    @Autowired PickSessionEpoch epoch;
    @Autowired PickPublicationService publications;
    @Autowired PickDisclosureService disclosure;
    @Autowired PickPublicationAttemptRepository publicationAttempts;
    @Autowired org.ctc.admin.service.PickIntegrityAuditService integrity;
    @Autowired BackupArchiveService archives;
    @MockitoSpyBean BackupImportService imports;
    @Autowired PickWindowService windows;
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
            fixture.matchday().setPicksOpen(true);
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
        bindingId = tx.execute(status -> bindings.saveAndFlush(new TeamPickChannelBinding(
                seasons.findById(fixture.season().getId()).orElseThrow(), teams.findById(fixture.homeTeam().getId()).orElseThrow(),
                "100000000000000001", "200000000000000002")).getId());
        original = submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor(fixture.race().getId()), payload(0, "S01, R03, S02"));
        reviews.returnForCorrection(fixture.race().getId(), 1, manager, "Check weather slot order");
        submissions.submit(fixture.race().getId(), 2, UUID.randomUUID(), actor(fixture.race().getId()), payload(0, "S01, R03, S02"));
        reviews.approve(fixture.race().getId(), 3, 2, manager, "Checked revision two");
        corrected = payload(1, "S02, R04, S03");
        corrections.record(fixture.race().getId(), 4, manager, "Management track correction", corrected);
        pendingRace = tx.execute(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            var day = helper.createMatchday(season, "Test pending target", 2);
            day.setPickDeadline(LocalDateTime.of(2035, 1, 2, 12, 0));
            day.setPicksOpen(true);
            var match = helper.createMatch(day, teams.findById(fixture.homeTeam().getId()).orElseThrow(), teams.findById(fixture.awayTeam().getId()).orElseThrow());
            var race = helper.createRace(day, match);
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
        UUID missingRace = tx.execute(status -> {
            var day = helper.createMatchday(seasons.findById(fixture.season().getId()).orElseThrow(), "Test reopened missing target", 3);
            day.setPickDeadline(LocalDateTime.of(2020, 1, 1, 12, 0));
            var match = helper.createMatch(day, teams.findById(fixture.homeTeam().getId()).orElseThrow(), teams.findById(fixture.awayTeam().getId()).orElseThrow());
            var race = helper.createRace(day, match);
            race.setDateTime(LocalDateTime.of(2035, 1, 3, 20, 0));
            return race.getId();
        });
        reviews.openMissing(missingRace, 0, manager, "Test missing submission explicitly opened without another deadline");
        byte[] archive = export();
        long generation = epoch.getAsLong();
        corrections.record(fixture.race().getId(), 5, manager, "After export", payload(1, "Changed after export"));
        reviews.returnForCorrection(pendingRace, 3, manager, "After export opening");
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
            var opened = picks.findByRaceId(missingRace).orElseThrow();
            assertThat(opened.getState()).isEqualTo(PickState.MISSING);
            assertThat(opened.getVersion()).isEqualTo(1);
            assertThat(opened.getLatestRevisionNumber()).isNull();
            assertThat(opened.isManualOpen()).isTrue();
            assertThat(opened.isManualClosed()).isFalse();
            assertThat(opened.getRace().getMatchday().getPickDeadline()).isEqualTo(LocalDateTime.of(2020, 1, 1, 12, 0));
            assertThat(revisions.findByRequestId(original.requestId()).orElseThrow().receipt()).isEqualTo(original);
            assertThat(reservations.pending(fixture.season().getId(), true)).containsExactly(
                    new PickReservationService.Reservation(pendingRace, fixture.homeTeam().getId(), carIds.getFirst(), null, trackIds.getFirst()));
            var history = decisions.findAll().stream().filter(decision -> decision.getPick().getId().equals(approved.getId())).toList();
            assertThat(history).hasSize(3);
            assertThat(history.stream().filter(decision -> decision.getKind() == PickDecisionKind.CORRECTION).findFirst().orElseThrow().getCorrectionPayload()).isEqualTo(corrected);
        });
        assertThat(submissions.submit(fixture.race().getId(), 0, original.requestId(), actor(fixture.race().getId()), payload(0, "S01"))).isEqualTo(original);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-revisions", "missing-decisions", "cross-team", "orphan-track", "duplicate-request", "forged-version", "dropped-correction", "disguised-v4", "window-flag", "window-version", "window-deadline", "window-legacy"})
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
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void givenSupportedOldBackup_whenRestoring_thenLeaveHistoryEmpty(int version) throws Exception {
        // given
        var files = unzip(legacy);
        var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json"));
        manifest.put("schema_version", version);
        removeWindowFields(files);
        var absent = new ArrayList<>(List.of("pick_publication_attempts", "pick_publication_snapshots"));
        if (version < 6) absent.add("team_pick_channel_bindings");
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
            assertThat(picks.findBySeasonId(fixture.season().getId())).isEmpty();
            assertThat(bindings.findById(bindingId)).isEmpty();
        });
    }

    @Test
    void givenSchemaFiveHistoryWithActivationFlagsAndWithoutBindings_whenRestoring_thenPreserveReceiptsAndGrantNoChannel() throws Exception {
        // given
        var files = unzip(export());
        var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json"));
        manifest.put("schema_version", 5);
        removeWindowFields(files);
        for (String table : List.of("pick_publication_attempts", "pick_publication_snapshots")) {
            ((ObjectNode) manifest.get("table_counts")).remove(table);
            files.remove("data/" + table.replace('_', '-') + ".json");
        }
        ((ObjectNode) manifest.get("table_counts")).remove("team_pick_channel_bindings");
        files.remove("data/team-pick-channel-bindings.json");
        for (String table : List.of("seasons", "races")) {
            var rows = (ArrayNode) JSON.readTree(files.get("data/" + table + ".json"));
            for (var row : rows) ((ObjectNode) row).put(table.equals("seasons") ? "picksCollectionEnabled" : "picksManaged", true);
            files.put("data/" + table + ".json", JSON.writeValueAsBytes(rows));
        }
        files.put("manifest.json", JSON.writeValueAsBytes(manifest));
        // when
        var preview = imports.stage(upload(zip(files)));
        imports.execute(preview.stagingId());
        // then
        tx.executeWithoutResult(status -> {
            assertThat(bindings.findById(bindingId)).as("the removed activation flags are accepted and ignored").isEmpty();
            assertThat(revisions.findByRequestId(original.requestId()).orElseThrow().receipt()).isEqualTo(original);
        });
    }

    @Test
    void givenConfirmedLobbyAndUncertainCorrection_whenRestoring_thenPreservePublicMasksAndPendingReconciliation() throws Exception {
        // given
        publications.publish(List.of(fixture.race().getId()), PickPublicationOutput.LOBBY_SETTINGS, manager, rows ->
                new org.ctc.domain.picks.PreparedPickPublication<>("123456789012345678", "234567890123456789", null,
                        "a".repeat(64), "{}", Map.of(fixture.race().getId(), PickDisclosureField.lobby()),
                        () -> "test-lobby-message", message -> message));
        corrections.record(fixture.race().getId(), 5, manager, "Later internal unpublished correction", payload(1, "Test private after lobby", TimeProgressionMode.MULTIPLIER, 0));
        tx.executeWithoutResult(status -> races.findById(fixture.race().getId()).orElseThrow().getMatchday().setPickDeadline(LocalDateTime.of(2026, 10, 25, 2, 30)));
        assertThatThrownBy(() -> publications.publish(List.of(fixture.race().getId()), PickPublicationOutput.SETTINGS, manager, rows ->
                new org.ctc.domain.picks.PreparedPickPublication<>("123456789012345678", "234567890123456789", null,
                        "b".repeat(64), "{}", Map.of(fixture.race().getId(), PickDisclosureField.all()),
                        () -> { throw new IOException("Test lost response"); }, message -> message))).isInstanceOf(IOException.class);
        byte[] archive = export();
        var before = disclosure.forPublic(fixture.race().getId());
        assertThat(before.carName()).isNull(); assertThat(before.settings().weather()).isEqualTo(corrected.weather());
        assertThat(before.settings().timeProgressionMode()).isEqualTo(TimeProgressionMode.EQUAL_CONDITIONS);
        assertThat(before.settings().timeProgressionMultiplier()).isNull();
        // when
        var preview = imports.stage(upload(archive)); imports.execute(preview.stagingId());
        // then
        assertThat(disclosure.forPublic(fixture.race().getId())).isEqualTo(before);
        tx.executeWithoutResult(status -> {
            var attempt = publicationAttempts.findByActiveRaceId(fixture.race().getId()).orElseThrow();
            assertThat(attempt.getState()).isEqualTo(PickPublicationState.UNCERTAIN);
            assertThat(attempt.getContent().weather()).isEqualTo("Test private after lobby");
            assertThat(attempt.getContentVersion()).isEqualTo(2);
            assertThat(attempt.getContentHash()).isEqualTo(attempt.getContent().fingerprint());
            assertThat(attempt.getContent().timeProgressionMode()).isEqualTo(TimeProgressionMode.MULTIPLIER);
            assertThat(attempt.getContent().timeProgressionMultiplier()).isZero();
            var internal = races.findById(fixture.race().getId()).orElseThrow().getSettings();
            assertThat(internal.getTimeProgressionMode()).isEqualTo(TimeProgressionMode.MULTIPLIER);
            assertThat(internal.getTimeProgressionMultiplier()).isZero();
        });
        tx.executeWithoutResult(status -> {
            var season = seasons.findById(fixture.season().getId()).orElseThrow();
            assertThat(season.getCars()).extracting(Car::getId).containsExactlyInAnyOrderElementsOf(carIds);
            assertThat(season.getTracks()).extracting(Track::getId).containsExactlyInAnyOrderElementsOf(trackIds);
        });
        assertThat(integrity.inspect()).isEmpty();
    }

    private PickPayload payload(int option, String weather) {
        return payload(option, weather, TimeProgressionMode.EQUAL_CONDITIONS, null);
    }

    @Test
    void givenSchemaThirteenWithoutLobbyFields_whenRestoring_thenPreserveOriginalLegacyHashesMasksAndPublicationBytes() throws Exception {
        // given
        publications.publish(List.of(fixture.race().getId()), PickPublicationOutput.SETTINGS, manager, rows ->
                new org.ctc.domain.picks.PreparedPickPublication<>("123456789012345678", "234567890123456789", null,
                        "a".repeat(64), "{\"legacy-template\":\"original bytes\"}", Map.of(fixture.race().getId(), Set.of(PickDisclosureField.TRACK)),
                        () -> "test-legacy-lobby-message", message -> message));
        var captured = tx.execute(status -> publicationAttempts.findAll().stream()
                .filter(attempt -> attempt.getSeasonId().equals(fixture.season().getId())).findFirst().orElseThrow());
        var before = disclosure.forPublic(fixture.race().getId());
        var files = unzip(export());
        var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json"));
        manifest.put("schema_version", 13);
        removeWindowFields(files);
        files.put("manifest.json", JSON.writeValueAsBytes(manifest));
        for (String table : List.of("seasons", "race-settings", "matchday-pick-revisions", "matchday-pick-decisions", "pick-publication-attempts")) {
            String file = "data/" + table + ".json";
            var rows = (ArrayNode) JSON.readTree(files.get(file));
            for (var row : rows) {
                var value = switch (table) {
                    case "matchday-pick-revisions" -> row.get("payload");
                    case "matchday-pick-decisions" -> row.get("correctionPayload");
                    case "pick-publication-attempts" -> row.get("content");
                    default -> row;
                };
                if (value != null && value.isObject()) ((ObjectNode) value).remove(List.of("lobbyRulesProfile", "startType", "slipstream"));
            }
            files.put(file, JSON.writeValueAsBytes(rows));
        }
        // when
        var preview = imports.stage(upload(zip(files)));
        imports.execute(preview.stagingId());
        // then
        assertThat(disclosure.forPublic(fixture.race().getId())).isEqualTo(before);
        tx.executeWithoutResult(status -> {
            assertThat(seasons.findById(fixture.season().getId()).orElseThrow().getLobbyRulesProfile()).isEqualTo(LobbyRulesProfile.LEGACY);
            assertThat(races.findById(fixture.race().getId()).orElseThrow().getSettings().getStartType()).isEqualTo(RaceStartType.GRID_FALSE_START);
            assertThat(races.findById(fixture.race().getId()).orElseThrow().getSettings().getSlipstream()).isEqualTo(RaceSlipstream.REAL);
            assertThat(revisions.findByRequestId(original.requestId()).orElseThrow().receipt()).isEqualTo(original);
            var restored = publicationAttempts.findById(captured.getId()).orElseThrow();
            assertThat(restored.getContent()).isEqualTo(captured.getContent());
            assertThat(restored.getContentHash()).isEqualTo(captured.getContentHash());
            assertThat(restored.getFieldMask()).isEqualTo(captured.getFieldMask());
            assertThat(restored.getRequestManifest()).isEqualTo(captured.getRequestManifest());
            assertThat(restored.getRequestHash()).isEqualTo(captured.getRequestHash());
            assertThat(restored.getTemplateHash()).isEqualTo(captured.getTemplateHash());
        });
        assertThat(integrity.inspect()).isEmpty();
    }
    private PickPayload payload(int option, String weather, TimeProgressionMode mode, Integer multiplier) {
        return new PickPayload(trackIds.get(option), carIds.get(option), null, 12, 2, 3, 4, PickInitialFuel.DEFAULT, 1,
                mode, multiplier, weather, "Sunset", Set.of(PickTyre.RS, PickTyre.RM), Set.of(PickTyre.RM));
    }
    private AuthorizedPickActor actor(UUID race) {
        return new AuthorizedPickActor(race, fixture.season().getId(), fixture.homeTeam().getId(), "12345");
    }
    @Test
    void givenCentralWindowAndPairingExceptionWithLegacyHistory_whenRestoring_thenPreserveNewStateAndEveryOriginalReceipt() throws Exception {
        var pairing = tx.execute(status -> races.findById(pendingRace).orElseThrow().getMatch().getId());
        var pair = windows.readPairing(pairing, false);
        var actor = new ManagementPickActor("test-window-manager", epoch.managementGeneration());
        var cutoff = LocalDateTime.of(2036, 1, 2, 18, 0);
        windows.savePairing(pairing, false, cutoff, pair.version(), pair.scope(), actor);
        var day = windows.readMatchday(pair.matchdayId());
        windows.saveMatchday(day.id(), true, day.deadline(), day.weekend(), day.version(), day.scope(), actor);
        var expected = windows.readMatchday(day.id());
        var archive = export();
        var preview = imports.stage(upload(archive)); imports.execute(preview.stagingId());
        var restored = windows.readMatchday(day.id());
        assertThat(restored.open()).isTrue(); assertThat(restored.version()).isEqualTo(expected.version());
        assertThat(windows.readPairing(pairing, false).override()).isEqualTo(cutoff);
        tx.executeWithoutResult(status -> {
            var pick = picks.findByRaceId(pendingRace).orElseThrow();
            assertThat(pick.getState()).isEqualTo(PickState.RETURNED); assertThat(pick.isManualClosed()).isFalse();
            assertThat(decisions.findAll().stream().filter(row -> row.getPick().getId().equals(pick.getId())))
                    .extracting(MatchdayPickDecision::getKind).contains(PickDecisionKind.CLOSE, PickDecisionKind.WINDOW_OPEN);
            assertThat(revisions.findByRequestId(original.requestId()).orElseThrow().receipt()).isEqualTo(original);
        });
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void givenANewWindowInheritingAForeignPlayoff_whenStagingOrTamperingExecute_thenRejectBeforeWipe(boolean bridge) throws Exception {
        var foreign = tx.execute(status -> {
            var season = helper.createSeason("Test foreign pick window " + UUID.randomUUID());
            var phase = new SeasonPhase(season, PhaseType.PLAYOFF, PhaseLayout.BRACKET, 10); phase.setLabel("Test foreign phase"); entityManager.persist(phase);
            var playoff = new Playoff(phase, "Test foreign playoff"); entityManager.persist(playoff);
            var round = new PlayoffRound(playoff, "Test final", 0); entityManager.persist(round);
            var pair = new PlayoffMatchup(round, 0); pair.setTeam1(teams.findById(fixture.homeTeam().getId()).orElseThrow());
            pair.setTeam2(teams.findById(fixture.awayTeam().getId()).orElseThrow()); pair.setPickDeadlineOverride(LocalDateTime.of(2036, 1, 1, 20, 0));
            entityManager.persist(pair); entityManager.flush(); return new UUID[]{season.getId(), pair.getId()};
        });
        try {
            byte[] valid = export(); var files = unzip(valid); var rows = (ArrayNode) JSON.readTree(files.get("data/races.json"));
            for (var row : rows) if (row.path("id").asText().equals(fixture.race().getId().toString())) {
                ((ObjectNode) row).put("playoffMatchup", foreign[1].toString());
                if (!bridge) ((ObjectNode) row).putNull("match");
            }
            files.put("data/races.json", JSON.writeValueAsBytes(rows)); byte[] broken = zip(files);
            assertThatThrownBy(() -> imports.stage(upload(broken))).isInstanceOfSatisfying(BackupArchiveException.class,
                    failure -> assertThat(failure.getMessage()).contains("ownership"));
            verify(imports, never()).wipeAllTables(anyMap());
            var preview = imports.stage(upload(valid));
            try {
                Files.write(ROOT.resolve("staging/upload-" + preview.stagingId() + ".zip"), broken); clearInvocations(imports);
                assertThatThrownBy(() -> imports.execute(preview.stagingId())).isInstanceOf(BackupImportException.class)
                        .cause().isInstanceOfSatisfying(BackupArchiveException.class, failure -> assertThat(failure.getMessage()).contains("ownership"));
                verify(imports, never()).wipeAllTables(anyMap());
                tx.executeWithoutResult(status -> assertThat(revisions.findByRequestId(original.requestId()).orElseThrow().receipt()).isEqualTo(original));
            } finally { imports.deleteStagingFile(preview.stagingId()); }
        } finally { tx.executeWithoutResult(status -> helper.deleteSeasonCascade(seasons.findById(foreign[0]).orElseThrow())); }
    }

    private void removeWindowFields(Map<String, byte[]> files) throws IOException {
        for (String table : List.of("matchdays", "matches", "playoff-matchups")) {
            String name = "data/" + table + ".json";
            var rows = (ArrayNode) JSON.readTree(files.get(name));
            for (var row : rows) ((ObjectNode) row).remove(List.of("picksOpen", "pickWindowVersion", "pickDeadlineOverride"));
            files.put(name, JSON.writeValueAsBytes(rows));
        }
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
        if (kind.equals("window-legacy")) {
            manifest.put("schema_version", 16);
        } else if (kind.startsWith("window-")) {
            String file = kind.equals("window-deadline") ? "data/matches.json" : "data/matchdays.json";
            var rows = (ArrayNode) JSON.readTree(files.get(file));
            var row = (ObjectNode) rows.get(0);
            switch (kind) {
                case "window-flag" -> row.put("picksOpen", "true");
                case "window-version" -> row.put("pickWindowVersion", -1);
                case "window-deadline" -> row.put("pickDeadlineOverride", "2027-03-28T02:30");
                default -> throw new AssertionError(kind);
            }
            files.put(file, JSON.writeValueAsBytes(rows));
        } else if (kind.startsWith("missing-")) {
            files.remove("data/matchday-pick-" + (kind.equals("missing-revisions") ? "revisions" : "decisions") + ".json");
        } else if (kind.equals("disguised-v4")) {
            manifest.put("schema_version", 4);
        } else {
            String table = switch (kind) {
                case "cross-team", "forged-version" -> "matchday-picks";
                case "orphan-track", "duplicate-request" -> "matchday-pick-revisions";
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
                        case "cross-team" -> ((ObjectNode) row).put("team", fixture.awayTeam().getId().toString());
                        case "forged-version" -> ((ObjectNode) row).put("version", 999);
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
