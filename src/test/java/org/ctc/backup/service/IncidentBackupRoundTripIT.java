package org.ctc.backup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupImportException;
import org.ctc.discord.DiscordRestClient;
import org.ctc.domain.model.IncidentPayload;
import org.ctc.domain.model.IncidentReportState;
import org.ctc.domain.service.IncidentIntegrationFixture;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
class IncidentBackupRoundTripIT extends IncidentIntegrationFixture {
    static final ObjectMapper JSON = new ObjectMapper();
    static final Path ROOT = SitegenTestDir.create("incident-backup");
    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry properties) {
        properties.add("app.upload-dir", () -> ROOT.resolve("uploads").toString());
        properties.add("app.backup.staging-dir", () -> ROOT.resolve("staging").toString());
        properties.add("app.backup.import-backups-dir", () -> ROOT.resolve("imports").toString());
        properties.add("ctc.site.output-dir", () -> ROOT.resolve("site").toString());
    }
    @Autowired BackupArchiveService archives;
    @MockitoSpyBean BackupImportService imports;
    @MockitoBean DiscordRestClient discord;
    UUID activeReport;
    UUID withdrawnReport;
    UUID firstRequest;
    org.ctc.domain.incidents.IncidentReportReceipt firstReceipt;

    @BeforeEach
    void seedCases() throws Exception {
        Files.createDirectories(ROOT.resolve("uploads"));
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        firstRequest = UUID.randomUUID(); firstReceipt = reports.submit(windowId, fixture.race().getId(), 1, firstRequest, actor, payload);
        activeReport = firstReceipt.reportId();
        reports.edit(activeReport, 1, 1, UUID.randomUUID(), actor, new IncidentPayload("Test corrected guest", "Lap 2 at 00:00", "Test private correction", ""));
        withdrawnReport = reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload).reportId();
        reports.withdraw(withdrawnReport, 1, 1, UUID.randomUUID(), actor);
        clearInvocations(imports, discord);
    }

    @Test
    void givenActiveAndWithdrawnCasesWithMultipleRevisions_whenRestoring_thenPreserveExactIdsPartiesHistoryAndReceipts() throws Exception {
        byte[] archive = export();
        var revisionIds = tx.execute(status -> revisionRows.findByReportIdOrderByRevisionNumberAsc(activeReport).stream().map(row -> row.getId()).toList());
        reports.edit(activeReport, 1, 2, UUID.randomUUID(), actor, new IncidentPayload("Test newer value", "02:00", "Test newer state after export", ""));
        var preview = imports.stage(upload(archive)); imports.execute(preview.stagingId());
        verify(imports).wipeAllTables(anyMap());
        tx.executeWithoutResult(status -> {
            var context = contextRows.findById(contextId).orElseThrow();
            assertThat(context.getMatch().getId()).isEqualTo(fixture.match().getId()); assertThat(context.getChannelId()).isEqualTo(CHANNEL);
            var saved = reportRows.findById(activeReport).orElseThrow();
            assertThat(saved.getVersion()).isEqualTo(2); assertThat(saved.getLatestRevisionNumber()).isEqualTo(2);
            assertThat(saved.getCaseReference()).isEqualTo(firstReceipt.caseReference()); assertThat(saved.getParty()).isEqualTo(actor.party());
            assertThat(saved.getAuthorId()).isEqualTo(actor.userId()); assertThat(saved.getRace().getId()).isEqualTo(fixture.race().getId());
            var revisions = revisionRows.findByReportIdOrderByRevisionNumberAsc(activeReport);
            assertThat(revisions).extracting(row -> row.getId()).containsExactlyElementsOf(revisionIds);
            assertThat(revisions.getFirst().getPayload()).isEqualTo(payload);
            assertThat(revisions.getLast().getPayload().getDescription()).isEqualTo("Test private correction");
            assertThat(reportRows.findById(withdrawnReport).orElseThrow().getState()).isEqualTo(IncidentReportState.WITHDRAWN);
            assertThat(windowRows.findById(windowId).orElseThrow().getDeadline()).isEqualTo(NOW.plusSeconds(3600));
        });
        assertThat(reports.submit(windowId, fixture.race().getId(), 0, firstRequest, actor, null)).isEqualTo(firstReceipt);
        verifyNoInteractions(discord);
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "request", "owner", "fields", "missing-revisions", "count", "disguised-v7"})
    void givenMalformedHistory_whenStagingAndExecuting_thenRejectBeforeWipe(String corruption) throws Exception {
        byte[] valid = export(), broken = malformed(valid, corruption);
        assertThatThrownBy(() -> imports.stage(upload(broken))).isInstanceOfSatisfying(BackupArchiveException.class,
                error -> assertThat(error.reason()).isEqualTo(BackupArchiveException.Reason.DATA_MISMATCH));
        verify(imports, never()).wipeAllTables(anyMap());
        var preview = imports.stage(upload(valid));
        Files.write(ROOT.resolve("staging/upload-" + preview.stagingId() + ".zip"), broken); clearInvocations(imports);
        assertThatThrownBy(() -> imports.execute(preview.stagingId())).isInstanceOf(BackupImportException.class).cause()
                .isInstanceOfSatisfying(BackupArchiveException.class, error -> assertThat(error.reason()).isEqualTo(BackupArchiveException.Reason.DATA_MISMATCH));
        verify(imports, never()).wipeAllTables(anyMap());
        tx.executeWithoutResult(status -> assertThat(reportRows.findById(activeReport).orElseThrow().getVersion()).isEqualTo(2));
        verifyNoInteractions(discord); imports.deleteStagingFile(preview.stagingId());
    }

    @Test
    void givenSupportedSchemaSevenWithoutIncidentFiles_whenRestoring_thenKeepAllIncidentStateAbsentWithoutOpeningWindows() throws Exception {
        var files = unzip(export()); var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json"));
        manifest.put("schema_version", 7); var counts = (ObjectNode) manifest.get("table_counts");
        for (String table : java.util.List.of("incident_match_contexts", "incident_report_windows", "incident_reports", "incident_report_revisions", "incident_report_events")) {
            files.remove("data/" + table.replace('_', '-') + ".json"); counts.remove(table);
        }
        files.put("manifest.json", JSON.writeValueAsBytes(manifest));
        var preview = imports.stage(upload(zip(files))); imports.execute(preview.stagingId());
        assertThat(contextRows.findBySeasonId(fixture.season().getId())).isEmpty(); assertThat(windowRows.findByContextId(contextId)).isEmpty();
        assertThat(reportRows.findById(activeReport)).isEmpty(); verifyNoInteractions(discord);
    }

    private byte[] export() throws Exception {
        var out = new ByteArrayOutputStream(); archives.writeZip(out, clock.instant()); return out.toByteArray();
    }
    private MockMultipartFile upload(byte[] bytes) { return new MockMultipartFile("file", "test-incidents.zip", "application/zip", bytes); }
    private byte[] malformed(byte[] archive, String corruption) throws Exception {
        var files = unzip(archive); var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json"));
        if (corruption.equals("missing-revisions")) files.remove("data/incident-report-revisions.json");
        else if (corruption.equals("count")) {
            var counts = (ObjectNode) manifest.get("table_counts"); counts.put("incident_reports", counts.path("incident_reports").asLong() + 1);
        } else if (corruption.equals("disguised-v7")) manifest.put("schema_version", 7);
        else if (corruption.equals("request")) {
            var rows = (ArrayNode) JSON.readTree(files.get("data/incident-report-revisions.json"));
            ((ObjectNode) rows.get(1)).put("requestId", rows.get(0).path("requestId").asText());
            files.put("data/incident-report-revisions.json", JSON.writeValueAsBytes(rows));
        } else {
            var rows = (ArrayNode) JSON.readTree(files.get("data/incident-reports.json"));
            for (var row : rows) if (row.path("id").asText().equals(activeReport.toString())) {
                var report = (ObjectNode) row;
                if (corruption.equals("version")) report.put("version", 99);
                else if (corruption.equals("owner")) report.put("window", UUID.randomUUID().toString());
                else report.put("unexpectedField", "Test private facts must not enter diagnostics");
            }
            files.put("data/incident-reports.json", JSON.writeValueAsBytes(rows));
        }
        files.put("manifest.json", JSON.writeValueAsBytes(manifest)); return zip(files);
    }
    private Map<String, byte[]> unzip(byte[] archive) throws Exception {
        var files = new LinkedHashMap<String, byte[]>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry; while ((entry = zip.getNextEntry()) != null) files.put(entry.getName(), zip.readAllBytes());
        }
        return files;
    }
    private byte[] zip(Map<String, byte[]> files) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            for (var entry : files.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); }
        }
        return out.toByteArray();
    }
}
