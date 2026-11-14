package org.ctc.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.ctc.admin.service.MatchdayPickAdminService;
import org.ctc.admin.service.PickIntegrityAuditService;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupImportException;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.picks.PreparedPickPublication;
import org.ctc.domain.repository.PickPublicationAttemptRepository;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:ctc_lobby_workflow;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Tag("integration")
class LobbyRulesPersistenceIT extends PickPersistenceIT {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Path ROOT = SitegenTestDir.create("lobby-rules-workflow");
    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry properties) {
        properties.add("app.upload-dir", () -> ROOT.resolve("uploads").toString());
        properties.add("app.backup.staging-dir", () -> ROOT.resolve("staging").toString());
        properties.add("app.backup.import-backups-dir", () -> ROOT.resolve("imports").toString());
        properties.add("ctc.site.output-dir", () -> ROOT.resolve("site").toString());
    }
    @Autowired SeasonLobbyRulesService rules;
    @Autowired MatchdayPickAdminService management;
    @Autowired PickIntegrityAuditService integrity;
    @Autowired PickPublicationService publications;
    @Autowired PickPublicationAttemptRepository attempts;
    @Autowired PickDisclosureService disclosure;
    @Autowired PickSessionEpoch epoch;
    @Autowired BackupArchiveService archives;
    @MockitoSpyBean BackupImportService imports;
    final ManagementPickActor manager = new ManagementPickActor("test-lobby-manager");

    @Override
    @BeforeEach
    void setUp() {
        super.setUp();
        rules.changeProfile(fixture.season().getId(), LobbyRulesProfile.LEGACY, LobbyRulesProfile.CTC_2027);
        payload = choices(payload, RaceStartType.ROLLING, RaceSlipstream.WEAK);
    }

    @Test
    void givenChangedProfileWithoutSavedSetup_whenSubmittingOldDraft_thenRejectBeforeHistoryOrSettings() {
        // given
        rules.changeProfile(fixture.season().getId(), LobbyRulesProfile.CTC_2027, LobbyRulesProfile.LEGACY);
        // when / then
        assertThatThrownBy(() -> submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("reload");
        tx.executeWithoutResult(status -> {
            assertThat(picks.findByRaceId(fixture.race().getId())).isEmpty();
            assertThat(revisions.findAll()).noneMatch(revision -> revision.getPayload().carId().equals(payload.carId()));
            assertThat(entityManager.find(Race.class, fixture.race().getId()).getSettings()).isNull();
        });
    }

    @Test
    void givenPrivate2027History_whenChangingProfile_thenRejectAndKeepCapturedSubmission() {
        // given
        var request = UUID.randomUUID();
        submissions.submit(fixture.race().getId(), 0, request, actor, payload);
        // when / then
        assertThatThrownBy(() -> rules.changeProfile(fixture.season().getId(), LobbyRulesProfile.CTC_2027, LobbyRulesProfile.LEGACY))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("private pick history");
        tx.executeWithoutResult(status -> assertThat(revisions.findByRequestId(request).orElseThrow().getPayload()).isEqualTo(payload));
    }

    @ParameterizedTest
    @ValueSource(strings = {"submission", "profile"})
    void givenConcurrentProfileChange_whenWaitingWriterContinues_thenReadCurrentProfileBeforeAnyMutation(String operation) throws Exception {
        // given
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var waiting = new CountDownLatch(1);
        // when
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = workers.submit(() -> tx.execute(status -> {
                var season = seasons.findByIdForUpdate(fixture.season().getId()).orElseThrow();
                season.setLobbyRulesProfile(LobbyRulesProfile.LEGACY);
                locked.countDown();
                await(release);
                return true;
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var second = workers.submit(() -> {
                    Thread.currentThread().setName("lobby-waiting-writer");
                    waiting.countDown();
                    try {
                        if (operation.equals("submission")) submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload);
                        else rules.changeProfile(fixture.season().getId(), LobbyRulesProfile.CTC_2027, LobbyRulesProfile.LEGACY);
                        return (RuntimeException) null;
                    } catch (RuntimeException failure) { return failure; }
                });
                assertThat(waiting.await(10, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(java.util.concurrent.TimeoutException.class);
                release.countDown();
                assertThat(first.get(15, TimeUnit.SECONDS)).isTrue();
                assertThat(second.get(15, TimeUnit.SECONDS)).isInstanceOf(BusinessRuleException.class);
            } finally { release.countDown(); }
        }
        // then
        tx.executeWithoutResult(status -> {
            assertThat(seasons.findById(fixture.season().getId()).orElseThrow().getLobbyRulesProfile()).isEqualTo(LobbyRulesProfile.LEGACY);
            assertThat(picks.findByRaceId(fixture.race().getId())).isEmpty();
            assertThat(entityManager.find(Race.class, fixture.race().getId()).getSettings()).isNull();
        });
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("Season writer was not released"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new AssertionError(failure); }
    }

    @Test
    void givenSameSelectionWithChangedStartAndSlipstream_whenReviewing_thenMarkActualDifferencesAndRetainIdentityComparison() {
        // given
        submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload);
        tx.executeWithoutResult(status -> {
            var race = entityManager.find(Race.class, fixture.race().getId());
            race.setCar(cars.findById(payload.carId()).orElseThrow());
            race.setTrack(tracks.findById(payload.trackId()).orElseThrow());
            race.setSettings(new RaceSettings(race));
            RaceSettingsValues.from(choices(payload, RaceStartType.GRID_FALSE_START, RaceSlipstream.REAL)).applyTo(race.getSettings());
        });
        // when
        var detail = management.detail(fixture.race().getId());
        // then
        assertThat(detail.comparison()).filteredOn(row -> Set.of("Start type", "Race slipstream").contains(row.label()))
                .hasSize(2).allMatch(row -> row.changed());
        assertThat(detail.comparison()).filteredOn(row -> Set.of("Season rules", "Track", "Car", "Pool entry").contains(row.label()))
                .hasSize(4).noneMatch(row -> row.changed());
        UUID otherCar = tx.execute(status -> {
            var original = cars.findById(payload.carId()).orElseThrow();
            String originalName = original.getName();
            original.setName("variant — " + originalName);
            var other = cars.saveAndFlush(new Car(original.getManufacturer() + " — variant", originalName));
            entityManager.find(Race.class, fixture.race().getId()).setCar(other);
            return other.getId();
        });
        try {
            assertThat(management.detail(fixture.race().getId()).comparison()).filteredOn(row -> row.label().equals("Car"))
                    .singleElement().satisfies(row -> {
                        assertThat(row.existing()).isEqualTo(row.submitted());
                        assertThat(row.changed()).isTrue();
                    });
        } finally {
            tx.executeWithoutResult(status -> {
                entityManager.find(Race.class, fixture.race().getId()).setCar(cars.findById(payload.carId()).orElseThrow());
                entityManager.flush();
                cars.deleteById(otherCar);
            });
        }
    }

    @ParameterizedTest
    @EnumSource(PickPublicationState.class)
    void givenAnySavedPublicationStateWithoutSettings_whenChangingProfile_thenRejectEvenFailedOrRetiredAttempts(PickPublicationState state) {
        // given
        tx.executeWithoutResult(status -> {
            var race = entityManager.find(Race.class, fixture.race().getId());
            var now = Instant.now();
            var attempt = new PickPublicationAttempt(new PickPublicationAttempt.Capture(UUID.randomUUID(), race, 1, 0,
                    PickPublicationOrigin.LEGACY, PickPublicationOutput.SETTINGS, PickPublicationContent.from(race), Set.of(),
                    "test-manager", "123456789012345678", "234567890123456789", null, "a".repeat(64), "{}", epoch.managementGeneration(), now));
            switch (state) {
                case PREPARED -> { }
                case SUCCEEDED -> attempt.confirm("test-message", now);
                case FAILED -> attempt.fail("TEST_REJECTED", now);
                case UNCERTAIN -> attempt.uncertain("TEST_UNKNOWN", now);
                case ABANDONED -> attempt.abandon("test-manager", "Test reviewed remote state", now);
            }
            attempts.saveAndFlush(attempt);
        });
        // when / then
        assertThatThrownBy(() -> rules.changeProfile(fixture.season().getId(), LobbyRulesProfile.CTC_2027, LobbyRulesProfile.LEGACY))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("publication attempt");
        tx.executeWithoutResult(status -> assertThat(seasons.findById(fixture.season().getId()).orElseThrow().getLobbyRulesProfile())
                .isEqualTo(LobbyRulesProfile.CTC_2027));
    }

    @Test
    void givenApproved2027CorrectionAndPartialPublication_whenRestoring_thenPreserveExactChoicesHistoryMasksAndHashes() throws Exception {
        // given
        var request = UUID.randomUUID();
        var receipt = submissions.submit(fixture.race().getId(), 0, request, actor, payload);
        reviews.approve(fixture.race().getId(), 1, 1, manager, "Test checked 2027 setup");
        var corrected = choices(payload, RaceStartType.GRID_FALSE_START, RaceSlipstream.REAL);
        corrections.record(fixture.race().getId(), 2, manager, "Test corrected start and slipstream", corrected);
        publications.publish(List.of(fixture.race().getId()), PickPublicationOutput.SETTINGS, manager, rows ->
                new PreparedPickPublication<>("123456789012345678", "234567890123456789", null, "a".repeat(64), "{}",
                        Map.of(fixture.race().getId(), Set.of(PickDisclosureField.START_TYPE)), () -> "test-frozen-message", message -> message));
        var publicBefore = disclosure.forPublic(fixture.race().getId());
        assertThat(publicBefore.settings().startType()).isEqualTo(RaceStartType.GRID_FALSE_START);
        assertThat(publicBefore.settings().slipstream()).isNull();
        var captured = tx.execute(status -> attempts.findAll().stream().filter(a -> a.getSeasonId().equals(fixture.season().getId())).findFirst().orElseThrow());
        var archive = export();
        corrections.record(fixture.race().getId(), 3, manager, "Test later unpublished choices", payload);
        clearInvocations(imports);
        // when
        var preview = imports.stage(upload(archive));
        imports.execute(preview.stagingId());
        // then
        assertThat(mockingDetails(imports).getInvocations()).anyMatch(call -> call.getMethod().getName().equals("wipeAllTables"));
        assertThat(disclosure.forPublic(fixture.race().getId())).isEqualTo(publicBefore);
        tx.executeWithoutResult(status -> {
            var race = entityManager.find(Race.class, fixture.race().getId());
            assertThat(race.getMatchday().getSeason().getLobbyRulesProfile()).isEqualTo(LobbyRulesProfile.CTC_2027);
            assertThat(race.getSettings().getStartType()).isEqualTo(corrected.startType());
            assertThat(race.getSettings().getSlipstream()).isEqualTo(corrected.slipstream());
            assertThat(revisions.findByRequestId(request).orElseThrow().getPayload()).isEqualTo(payload);
            assertThat(revisions.findByRequestId(request).orElseThrow().receipt()).isEqualTo(receipt);
            assertThat(decisions.findAll().stream().filter(d -> d.getKind() == PickDecisionKind.CORRECTION).map(MatchdayPickDecision::getCorrectionPayload))
                    .contains(corrected);
            var restored = attempts.findById(captured.getId()).orElseThrow();
            assertThat(restored.getContent()).isEqualTo(captured.getContent());
            assertThat(restored.getContentHash()).isEqualTo(captured.getContentHash());
            assertThat(restored.getFieldMask()).isEqualTo(captured.getFieldMask());
            assertThat(restored.getRequestManifest()).isEqualTo(captured.getRequestManifest());
            assertThat(restored.getRequestHash()).isEqualTo(captured.getRequestHash());
            assertThat(restored.getTemplateHash()).isEqualTo(captured.getTemplateHash());
        });
        assertThat(integrity.inspect()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-profile", "null-profile", "unknown-profile", "missing-start", "unknown-slipstream", "season-mismatch", "native-mismatch"})
    void givenMalformedCurrentRulesArchive_whenStagingOrExecuting_thenRejectBeforeWipeAndKeepLiveState(String kind) throws Exception {
        // given
        boolean nativeSetup = kind.equals("native-mismatch");
        if (nativeSetup) tx.executeWithoutResult(status -> {
            var race = entityManager.find(Race.class, fixture.race().getId());
            var settings = new RaceSettings(race); RaceSettingsValues.from(payload).applyTo(settings); race.setSettings(settings);
        });
        else {
            submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, payload);
            reviews.approve(fixture.race().getId(), 1, 1, manager, "Test approved before malformed archive");
        }
        byte[] valid = export();
        var files = unzip(valid);
        String file = kind.endsWith("profile") || kind.endsWith("mismatch") ? "data/seasons.json" : "data/race-settings.json";
        var rows = (ArrayNode) JSON.readTree(files.get(file));
        for (var row : rows) {
            if (!(file.contains("seasons") ? row.path("id") : row.path("race")).asText().equals(
                    (file.contains("seasons") ? fixture.season().getId() : fixture.race().getId()).toString())) continue;
            var object = (ObjectNode) row;
            switch (kind) {
                case "missing-profile" -> object.remove("lobbyRulesProfile");
                case "null-profile" -> object.putNull("lobbyRulesProfile");
                case "unknown-profile" -> object.put("lobbyRulesProfile", "FORGED");
                case "missing-start" -> object.remove("startType");
                case "unknown-slipstream" -> object.put("slipstream", "FORGED");
                case "season-mismatch", "native-mismatch" -> object.put("lobbyRulesProfile", "LEGACY");
                default -> throw new AssertionError(kind);
            }
        }
        files.put(file, JSON.writeValueAsBytes(rows));
        byte[] broken = zip(files);
        long generation = epoch.getAsLong();
        clearInvocations(imports);
        // when / then
        assertThatThrownBy(() -> imports.stage(upload(broken))).isInstanceOfSatisfying(BackupArchiveException.class,
                failure -> assertThat(failure.reason()).isEqualTo(BackupArchiveException.Reason.DATA_MISMATCH));
        var preview = imports.stage(upload(valid));
        Files.write(ROOT.resolve("staging/upload-" + preview.stagingId() + ".zip"), broken);
        assertThatThrownBy(() -> imports.execute(preview.stagingId())).isInstanceOf(BackupImportException.class)
                .cause().isInstanceOf(BackupArchiveException.class);
        assertThat(mockingDetails(imports).getInvocations()).noneMatch(call -> call.getMethod().getName().equals("wipeAllTables"));
        assertThat(epoch.getAsLong()).isEqualTo(generation);
        tx.executeWithoutResult(status -> {
            if (nativeSetup) assertThat(picks.findByRaceId(fixture.race().getId())).isEmpty();
            else assertThat(picks.findByRaceId(fixture.race().getId()).orElseThrow().getVersion()).isEqualTo(2);
            assertThat(entityManager.find(Race.class, fixture.race().getId()).getSettings().getStartType()).isEqualTo(RaceStartType.ROLLING);
        });
        imports.deleteStagingFile(preview.stagingId());
    }

    static PickPayload choices(PickPayload source, RaceStartType start, RaceSlipstream slipstream) {
        return new PickPayload(source.trackId(), source.carId(), source.carPoolEntryId(), source.laps(), source.tyreWear(), source.fuelConsumption(),
                source.refuelingSpeed(), source.initialFuel(), source.requiredPitStops(), source.timeProgressionMode(), source.timeProgressionMultiplier(),
                source.weather(), source.timeOfDay(), source.availableTyres(), source.mandatoryTyres(), LobbyRulesProfile.CTC_2027, start, slipstream);
    }
    byte[] export() throws IOException {
        var bytes = new ByteArrayOutputStream(); archives.writeZip(bytes, Instant.now()); return bytes.toByteArray();
    }
    static MockMultipartFile upload(byte[] bytes) { return new MockMultipartFile("file", "test-lobby-rules.zip", "application/zip", bytes); }
    static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        var files = new LinkedHashMap<String, byte[]>();
        try (var input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry; while ((entry = input.getNextEntry()) != null) files.put(entry.getName(), input.readAllBytes());
        }
        return files;
    }
    static byte[] zip(Map<String, byte[]> files) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new ZipOutputStream(bytes)) {
            for (var entry : files.entrySet()) { output.putNextEntry(new ZipEntry(entry.getKey())); output.write(entry.getValue()); output.closeEntry(); }
        }
        return bytes.toByteArray();
    }
}
