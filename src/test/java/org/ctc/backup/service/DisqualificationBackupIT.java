package org.ctc.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupImportException;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.service.RaceDisqualificationService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.RacePenaltyFixture;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@CtcDevSpringBootContext
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:ctc_disqualification_backup;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@Tag("integration")
class DisqualificationBackupIT extends RacePenaltyFixture {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Path ROOT = SitegenTestDir.create("driver-dsq-backup");
    static final String RESULTS = "data/race-results.json";
    static final String HISTORY = "data/race-disqualification-revisions.json";
    static {
        try { Files.createDirectories(ROOT.resolve("uploads")); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry properties) {
        properties.add("app.upload-dir", () -> ROOT.resolve("uploads").toString());
        properties.add("app.backup.staging-dir", () -> ROOT.resolve("staging").toString());
        properties.add("app.backup.import-backups-dir", () -> ROOT.resolve("recovery").toString());
    }
    @Autowired BackupArchiveService archives;
    @MockitoSpyBean BackupImportService backupImports;
    @Autowired RaceDisqualificationService disqualifications;
    @Autowired RaceRepository raceRows;
    @Autowired JdbcTemplate jdbc;

    UUID resultId() { return tx.execute(status -> raceRows.findById(raceId).orElseThrow().getResults().stream()
            .filter(r -> r.getDriver().getId().equals(homeDriver)).findFirst().orElseThrow().getId()); }
    void correct(boolean dsq) {
        var id = resultId(); var view = disqualifications.view(raceId, id);
        disqualifications.save(raceId, id, new RaceDisqualificationService.Correction(dsq, view.revision(), view.fingerprint(),
                view.generation(), "Distinctive private DSQ evidence"), "trusted-manager");
    }
    byte[] archive() throws Exception { var out = new ByteArrayOutputStream(); archives.writeZip(out, Instant.now()); return out.toByteArray(); }
    MockMultipartFile upload(byte[] bytes) { return new MockMultipartFile("file", "dsq.zip", "application/zip", bytes); }

    @Test
    void givenImmutableDsqHistory_whenRestoring_thenKeepExactFlagsPointsReferencesIdsAndMicrosecondEvidence() throws Exception {
        // given
        correct(true); correct(true); var id = resultId();
        var time = java.sql.Timestamp.valueOf("2200-11-05 12:34:56.123456");
        jdbc.update("update race_disqualification_revisions set created_at=?,updated_at=? where race_id=?", time, time, raceId);
        var bytes = archive(); var files = unzip(bytes);
        var before = disqualifications.view(raceId, id);
        assertThat(JSON.readTree(files.get("manifest.json")).path("schema_version").intValue()).isEqualTo(21);
        assertThat(JSON.readTree(files.get("manifest.json")).path("table_counts").size()).isEqualTo(48);
        assertThat(new String(files.get(RESULTS), java.nio.charset.StandardCharsets.UTF_8))
                .doesNotContain("classificationPosition", "classificationLabel", "disqualificationRevisions");
        correct(false);
        // when
        var staged = backupImports.stage(upload(bytes)); backupImports.execute(staged.stagingId());
        // then
        var restored = disqualifications.view(raceId, id);
        assertThat(restored.disqualified()).isTrue(); assertThat(restored.result().getPosition()).isEqualTo(1);
        assertThat(restored.result().getPointsRace()).isZero(); assertThat(restored.history()).hasSize(2);
        assertThat(restored.history()).extracting(row -> row.getId()).containsExactlyElementsOf(before.history().stream().map(row -> row.getId()).toList());
        assertThat(restored.history()).allSatisfy(row -> {
            assertThat(row.getCreatedAt()).isEqualTo(time.toLocalDateTime()); assertThat(row.getUpdatedAt()).isEqualTo(time.toLocalDateTime());
            assertThat(row.getReason()).isEqualTo("Distinctive private DSQ evidence"); assertThat(row.getActorId()).isEqualTo("trusted-manager");
        });
        assertThat(JSON.readTree(unzip(archive()).get(HISTORY))).isEqualTo(JSON.readTree(files.get(HISTORY)));
        assertScores(0, 80);
    }

    @ParameterizedTest
    @ValueSource(ints = {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15})
    void givenSupportedLegacyArchive_whenRestoringWithoutNewFileOrFlags_thenDefaultFalseAndKeepOriginalPoints(int version) throws Exception {
        // given
        var files = unzip(archive());
        assertThat(JSON.readTree(files.get(HISTORY)).size()).isZero();
        var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json")); manifest.put("schema_version", version);
        ((ObjectNode) manifest.get("table_counts")).put("race_disqualification_revisions", 0); files.remove(HISTORY);
        var rows = (ArrayNode) JSON.readTree(files.get(RESULTS)); for (var row : rows) ((ObjectNode) row).remove("disqualified");
        files.put(RESULTS, JSON.writeValueAsBytes(rows)); files.put("manifest.json", JSON.writeValueAsBytes(manifest));
        // when
        var staged = backupImports.stage(upload(zip(files))); backupImports.execute(staged.stagingId());
        // then
        var restored = disqualifications.view(raceId, resultId());
        assertThat(restored.disqualified()).isFalse(); assertThat(restored.history()).isEmpty();
        assertThat(restored.result().getPosition()).isEqualTo(1); assertThat(restored.result().getPointsRace()).isEqualTo(80);
        assertScores(80, 70);
    }

    @ParameterizedTest
    @ValueSource(strings = {"flag", "state", "gap", "driver", "result", "team", "actor", "fingerprint", "legacy", "nanoseconds", "duplicate-driver", "missing-history", "duplicate-position", "points"})
    void givenMalformedDsqArchive_whenStagingOrTamperingExecute_thenRejectBeforeWipeAndKeepLiveClassification(String kind) throws Exception {
        // given
        correct(true); var valid = archive(); var broken = mutate(valid, kind);
        clearInvocations(backupImports);
        // when / then
        assertThatThrownBy(() -> backupImports.stage(upload(broken))).isInstanceOfSatisfying(BackupArchiveException.class, failure -> {
            assertThat(failure.reason()).isEqualTo(BackupArchiveException.Reason.DATA_MISMATCH); assertThat(failure.getMessage()).contains("DSQ");
        });
        verify(backupImports, never()).wipeAllTables(anyMap());
        var staged = backupImports.stage(upload(valid));
        Files.write(ROOT.resolve("staging/upload-" + staged.stagingId() + ".zip"), broken); clearInvocations(backupImports);
        assertThatThrownBy(() -> backupImports.execute(staged.stagingId())).isInstanceOf(BackupImportException.class)
                .cause().isInstanceOf(BackupArchiveException.class).hasMessageContaining("DSQ");
        verify(backupImports, never()).wipeAllTables(anyMap());
        assertThat(disqualifications.view(raceId, resultId()).history()).hasSize(1);
        assertThat(disqualifications.view(raceId, resultId()).disqualified()).isTrue(); assertScores(0, 80);
    }

    private byte[] mutate(byte[] bytes, String kind) throws Exception {
        var files = unzip(bytes); var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json"));
        var results = (ArrayNode) JSON.readTree(files.get(RESULTS)); var history = (ArrayNode) JSON.readTree(files.get(HISTORY));
        var row = (ObjectNode) history.get(0);
        var result = (ObjectNode) java.util.stream.StreamSupport.stream(results.spliterator(), false)
                .filter(value -> value.path("id").asText().equals(resultId().toString())).findFirst().orElseThrow();
        switch (kind) {
            case "flag" -> result.put("disqualified", "true"); case "state" -> result.put("disqualified", false);
            case "gap" -> row.put("revisionNumber", 2); case "driver" -> row.put("driver", awayDriver.toString());
            case "result" -> row.put("result", UUID.randomUUID().toString()); case "team" -> row.put("homeTeam", awayId.toString());
            case "actor" -> row.put("actorId", " "); case "fingerprint" -> row.put("resultFingerprint", "z".repeat(64));
            case "legacy" -> manifest.put("schema_version", 15);
            case "nanoseconds" -> row.put("createdAt", "2200-11-05T12:34:56.123456789");
            case "duplicate-driver" -> results.add(result.deepCopy().put("id", UUID.randomUUID().toString()).put("position", 3)
                    .put("disqualified", false).put("pointsRace", 70).put("pointsTotal", 70));
            case "missing-history" -> history.removeAll();
            case "duplicate-position" -> {
                for (var value : results) if (value.path("race").asText().equals(raceId.toString())) ((ObjectNode) value).put("position", 1);
            }
            case "points" -> result.put("pointsRace", 1); default -> throw new IllegalArgumentException("Unknown test mutation");
        }
        ((ObjectNode) manifest.get("table_counts")).put("race_results", results.size()).put("race_disqualification_revisions", history.size());
        files.put(RESULTS, JSON.writeValueAsBytes(results)); files.put(HISTORY, JSON.writeValueAsBytes(history));
        files.put("manifest.json", JSON.writeValueAsBytes(manifest)); return zip(files);
    }

    private LinkedHashMap<String, byte[]> unzip(byte[] original) throws Exception {
        var files = new LinkedHashMap<String, byte[]>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(original))) {
            ZipEntry entry; while ((entry = zip.getNextEntry()) != null) files.put(entry.getName(), zip.readAllBytes());
        }
        return files;
    }
    private byte[] zip(LinkedHashMap<String, byte[]> files) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            for (var entry : files.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); }
        }
        return out.toByteArray();
    }
}
