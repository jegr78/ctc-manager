package org.ctc.domain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.*;
import org.ctc.admin.dto.DataAuditFinding;
import org.ctc.admin.service.PickIntegrityAuditService;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupImportException;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.picks.PreparedPickPublication;
import org.ctc.domain.repository.PickPublicationAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:ctc_tyre_archive;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class TyreArchiveIT extends TyrePersistenceIT {
    static final ObjectMapper JSON = new ObjectMapper();
    @Autowired BackupArchiveService archives;
    @MockitoSpyBean BackupImportService imports;
    @Autowired PickIntegrityAuditService integrity;
    @Autowired PickPublicationService publications;
    @Autowired PickDisclosureService disclosure;
    @Autowired PickPublicationAttemptRepository attempts;
    @Autowired PickSessionEpoch epoch;

    @BeforeEach
    void archiveDirectory() throws IOException { Files.createDirectories(ROOT.resolve("uploads")); clearInvocations(imports); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void givenApprovedTyresAndFrozenPublication_whenRestoring_thenKeepExactPayloadReceiptHashesAndDisclosure(boolean sports) throws Exception {
        // given
        var choice = choice(sports); UUID request = UUID.randomUUID();
        var receipt = submissions.submit(fixture.race().getId(), 0, request, actor, choice);
        reviews.approve(fixture.race().getId(), 1, 1, manager, "Test tyre archive approval");
        publications.publish(List.of(fixture.race().getId()), PickPublicationOutput.SETTINGS, manager, rows -> bundle());
        var frozen = tx.execute(status -> attempts.findAll().stream().filter(a -> a.getSeasonId().equals(fixture.season().getId())).findFirst().orElseThrow());
        var before = disclosure.forPublic(fixture.race().getId());
        assertThat(before.settings().availableTyres()).isEqualTo(RaceSettingsValues.from(choice).availableTyres());
        assertThat(before.settings().mandatoryTyres()).isEqualTo(RaceSettingsValues.from(choice).mandatoryTyres());
        assertThat(before.carName()).as("a settings output releases every field").isNotNull();
        corrections.record(fixture.race().getId(), 2, manager, "Test unpublished Racing correction", payload);
        byte[] archive = export(); long generation = epoch.getAsLong();
        // when
        var preview = imports.stage(upload(archive)); imports.execute(preview.stagingId());
        // then
        assertThat(epoch.getAsLong()).isEqualTo(generation + 1);
        assertThat(disclosure.forPublic(fixture.race().getId())).isEqualTo(before);
        tx.executeWithoutResult(status -> {
            assertThat(revisions.findByRequestId(request).orElseThrow().getPayload()).isEqualTo(choice);
            assertThat(revisions.findByRequestId(request).orElseThrow().receipt()).isEqualTo(receipt);
            var restored = attempts.findById(frozen.getId()).orElseThrow();
            assertThat(restored.getContent()).isEqualTo(frozen.getContent());
            assertThat(restored.getContentHash()).isEqualTo(frozen.getContentHash());
            assertThat(restored.getFieldMask()).isEqualTo(frozen.getFieldMask());
            assertThat(restored.getRequestManifest()).isEqualTo(frozen.getRequestManifest());
            assertThat(restored.getRequestHash()).isEqualTo(frozen.getRequestHash());
            assertThat(restored.getTemplateHash()).isEqualTo(frozen.getTemplateHash());
        });
        assertThat(integrity.inspect()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"'RS,I',I", "'RS,W',W", "'RS,SS',None", "'RS,RS',None", "RS,RM"})
    void givenMalformedNativeTyresInArchive_whenStagingAndExecutingTamperedBytes_thenRejectBeforeWipeAndKeepGeneration(String available, String mandatory) throws Exception {
        // given
        approve(choice(true)); byte[] valid = export(); var files = unzip(valid);
        var rows = (ArrayNode) JSON.readTree(files.get("data/race-settings.json"));
        for (var row : rows) if (fixture.race().getId().toString().equals(row.path("race").asText())) {
            ((ObjectNode) row).put("availableTyres", available).put("mandatoryTyres", mandatory);
        }
        files.put("data/race-settings.json", JSON.writeValueAsBytes(rows)); byte[] broken = zip(files);
        long generation = epoch.getAsLong();
        // when / then
        assertThatThrownBy(() -> imports.stage(upload(broken))).isInstanceOfSatisfying(BackupArchiveException.class, failure -> {
            assertThat(failure.reason()).isEqualTo(BackupArchiveException.Reason.DATA_MISMATCH);
            assertThat(failure.getMessage()).contains("tyre");
        });
        var preview = imports.stage(upload(valid));
        Files.write(ROOT.resolve("staging/upload-" + preview.stagingId() + ".zip"), broken); clearInvocations(imports);
        assertThatThrownBy(() -> imports.execute(preview.stagingId())).isInstanceOf(BackupImportException.class).cause()
                .isInstanceOfSatisfying(BackupArchiveException.class, failure -> assertThat(failure.getMessage()).contains("tyre"));
        assertThat(mockingDetails(imports).getInvocations()).noneMatch(call -> call.getMethod().getName().equals("wipeAllTables")); assertThat(epoch.getAsLong()).isEqualTo(generation);
        tx.executeWithoutResult(status -> assertThat(PickSetupSnapshot.from(entityManager.find(Race.class, fixture.race().getId())).matches(choice(true))).isTrue());
        imports.deleteStagingFile(preview.stagingId());
    }

    @ParameterizedTest
    @ValueSource(ints = {13, 14})
    void givenOldArchiveVersionClaimingNewTypedTyres_whenStaging_thenRejectWithoutWipe(int version) throws Exception {
        // given
        approve(choice(true)); var files = unzip(export());
        var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json")); manifest.put("schema_version", version);
        files.put("manifest.json", JSON.writeValueAsBytes(manifest)); long generation = epoch.getAsLong();
        // when / then
        assertThatThrownBy(() -> imports.stage(upload(zip(files)))).isInstanceOf(BackupArchiveException.class);
        assertThat(mockingDetails(imports).getInvocations()).noneMatch(call -> call.getMethod().getName().equals("wipeAllTables")); assertThat(epoch.getAsLong()).isEqualTo(generation);
    }

    @ParameterizedTest
    @CsvSource({"'RS,I',I", "'RS,W',W", "'RS,SS',None", "'RS,RS',None"})
    void givenHistoricalInvalidNativeTyres_whenAuditingAndPublishing_thenReportWithoutRepairAndNeverRenderOrSend(String available, String mandatory) {
        // given
        approve(choice(true));
        tx.executeWithoutResult(status -> {
            var settings = entityManager.find(Race.class, fixture.race().getId()).getSettings();
            settings.setAvailableTyres(available); settings.setMandatoryTyres(mandatory);
        });
        var renders = new AtomicInteger();
        // when / then
        assertThat(integrity.inspect()).anySatisfy(finding -> {
            assertThat(finding.category()).isEqualTo(DataAuditFinding.Category.LOBBY_RULES);
            assertThat(finding.evidence()).isEqualTo("Invalid tyre choices for race " + fixture.race().getId());
        });
        assertThatThrownBy(() -> publications.publish(List.of(fixture.race().getId()), PickPublicationOutput.SETTINGS, manager,
                rows -> { renders.incrementAndGet(); return bundle(); })).isInstanceOf(BusinessRuleException.class);
        assertThat(renders).hasValue(0);
        tx.executeWithoutResult(status -> assertThat(entityManager.find(Race.class, fixture.race().getId()).getSettings().getAvailableTyres()).isEqualTo(available));
    }

    private void approve(PickPayload selected) {
        submissions.submit(fixture.race().getId(), 0, UUID.randomUUID(), actor, selected);
        reviews.approve(fixture.race().getId(), 1, 1, manager, "Test archive setup");
    }
    private PickPayload choice(boolean sports) {
        return new PickPayload(payload.trackId(), payload.carId(), payload.carPoolEntryId(), payload.laps(), payload.tyreWear(), payload.fuelConsumption(),
                payload.refuelingSpeed(), payload.initialFuel(), payload.requiredPitStops(), payload.timeProgressionMode(), payload.timeProgressionMultiplier(),
                payload.weather(), payload.timeOfDay(), sports ? Set.of(PickTyre.SS, PickTyre.SM, PickTyre.SH) : Set.of(PickTyre.RS, PickTyre.RM, PickTyre.RH, PickTyre.I, PickTyre.W),
                sports ? Set.of(PickTyre.SM) : Set.of(PickTyre.RM), payload.lobbyRulesProfile(), payload.startType(), payload.slipstream());
    }
    private PreparedPickPublication<String> bundle() {
        return new PreparedPickPublication<>("123456789012345678", "234567890123456789", null, "a".repeat(64),
                "{\"template\":\"Test exact Sports publication bytes\\n<raw>\"}", Map.of(fixture.race().getId(), Set.of(PickDisclosureField.AVAILABLE_TYRES)),
                () -> "test-tyre-message", message -> message);
    }
    private byte[] export() throws IOException { var out = new ByteArrayOutputStream(); archives.writeZip(out, Instant.now()); return out.toByteArray(); }
    private MockMultipartFile upload(byte[] bytes) { return new MockMultipartFile("file", "test-tyres.zip", "application/zip", bytes); }
    private Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        var files = new LinkedHashMap<String, byte[]>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) { ZipEntry entry; while ((entry = zip.getNextEntry()) != null) files.put(entry.getName(), zip.readAllBytes()); }
        return files;
    }
    private byte[] zip(Map<String, byte[]> files) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) { for (var item : files.entrySet()) { zip.putNextEntry(new ZipEntry(item.getKey())); zip.write(item.getValue()); zip.closeEntry(); } }
        return out.toByteArray();
    }
}
