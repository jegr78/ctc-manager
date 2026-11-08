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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:ctc_incident_backup;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
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
    @Autowired org.ctc.domain.service.IncidentRulingService rulings;
    @Autowired org.ctc.domain.service.PickSessionEpoch epoch;
    @Autowired org.ctc.domain.repository.IncidentRulingRepository rulingRows;
    @Autowired org.ctc.domain.repository.IncidentRulingRevisionRepository rulingRevisions;
    @Autowired org.ctc.domain.repository.IncidentRulingPublicationRepository rulingPublications;
    @Autowired org.ctc.domain.service.IncidentDeliveryService delivery;
    @Autowired org.ctc.domain.repository.IncidentDeliveryRepository deliveryRows;
    @Autowired org.ctc.domain.repository.IncidentThreadPublicationRepository threadRows;
    @Autowired org.ctc.domain.repository.PickPublicationAttemptRepository pickPublicationRows;
    @Autowired org.ctc.domain.service.IncidentThreadPublicationService threadPublication;
    String previousParent,previousStaff,previousTags;
    @BeforeEach void rememberIncidentSettings() {tx.executeWithoutResult(status -> {var config=configs.findById(configId).orElseThrow();previousParent=config.getStewardsChannelId();previousStaff=config.getIncidentStaffRoleIds();previousTags=config.getStewardsForumTagIds();});}
    @org.junit.jupiter.api.AfterEach void restoreIncidentSettings() {tx.executeWithoutResult(status -> configs.findById(configId).ifPresent(config -> {config.setStewardsChannelId(previousParent);config.setIncidentStaffRoleIds(previousStaff);config.setStewardsForumTagIds(previousTags);}));}
    @MockitoSpyBean BackupImportService imports;
    @MockitoBean DiscordRestClient discord;
    UUID activeReport;
    UUID withdrawnReport;
    UUID firstRequest;
    org.ctc.domain.incidents.IncidentReportReceipt firstReceipt;

    @BeforeEach
    void seedCases() throws Exception {
        assertThat(pickPublicationRows.count()).as("incident archive fixtures contain no unrelated pick publications").isZero();
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
    void givenPermanentRulingVersions_whenRestoring_thenPreserveExactIdsTextsAuthorsAndPausedCase() throws Exception {
        rulings.saveDraft(activeReport, 0, "Test first steward ruling", "", manager, epoch.managementGeneration());
        rulings.saveDraft(activeReport, 1, "Test second steward ruling", "Test changed decision", manager, epoch.managementGeneration());
        var before = tx.execute(status -> {
            var root = rulingRows.findByReportId(activeReport).orElseThrow();
            return rulingRevisions.findByRulingIdOrderByRevisionNumberAsc(root.getId()).stream().map(row -> row.getId()).toList();
        });
        String staleGeneration = epoch.managementGeneration();
        byte[] archive = export();
        rulings.saveDraft(activeReport, 2, "Test later unsaved-in-archive ruling", "", manager, staleGeneration);
        var preview = imports.stage(upload(archive)); imports.execute(preview.stagingId());
        tx.executeWithoutResult(status -> {
            var root = rulingRows.findByReportId(activeReport).orElseThrow();
            var revisions = rulingRevisions.findByRulingIdOrderByRevisionNumberAsc(root.getId());
            assertThat(revisions).extracting(row -> row.getId()).containsExactlyElementsOf(before);
            assertThat(revisions).extracting(row -> row.getText()).containsExactly("Test first steward ruling", "Test second steward ruling");
            assertThat(revisions).allSatisfy(row -> assertThat(row.getActorId()).isEqualTo(manager.id()));
            assertThat(root.getReport().getId()).isEqualTo(activeReport); assertThat(root.getVersion()).isEqualTo(2);
            assertThat(root.getReport().getWindow().isDeliveryPaused()).isTrue();
            assertThat(root.getPublishedMessageId()).isNull();
        });
        assertThatThrownBy(() -> rulings.saveDraft(activeReport, 2, "Test stale restore form", "", manager, staleGeneration))
                .isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class).hasMessageContaining("changed");
        verifyNoInteractions(discord);
    }

    @Test
    void givenActiveAndWithdrawnCasesWithMultipleRevisions_whenRestoring_thenPreserveExactIdsPartiesHistoryAndReceipts() throws Exception {
        var operationIds = tx.execute(status -> deliveryRows.findByReportWindowId(windowId).stream().map(org.ctc.domain.model.IncidentDelivery::getId).sorted().toList());
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
            assertThat(windowRows.findById(windowId).orElseThrow().isDeliveryPaused()).isTrue();
            assertThat(deliveryRows.findByReportWindowId(windowId)).extracting(org.ctc.domain.model.IncidentDelivery::getId).containsExactlyInAnyOrderElementsOf(operationIds);
            assertThat(deliveryRows.findByReportWindowId(windowId)).allSatisfy(row -> { assertThat(row.getState()).isEqualTo(org.ctc.domain.model.IncidentDeliveryState.PENDING); assertThat(row.getRevision().getRevisionNumber()).isEqualTo(1); });
        });
        assertThat(reports.submit(windowId, fixture.race().getId(), 0, firstRequest, actor, null)).isEqualTo(firstReceipt);
        verifyNoInteractions(discord);
    }

    @ParameterizedTest
    @ValueSource(strings={"CLAIMED","SENT","FAILED","UNCERTAIN","REVIEWED_PENDING"})
    void givenEveryDurableDeliveryOutcome_whenRestoring_thenRetainExactBusinessStateButDiscardPriorExternalReviewGrants(String outcome) throws Exception {
        UUID id=tx.execute(status -> deliveryRows.findByOperationKey(activeReport+":RECEIPT").orElseThrow().getId());
        tx.executeWithoutResult(status -> {
            var row=deliveryRows.findById(id).orElseThrow();var token=UUID.randomUUID();row.claim(token,"test-runtime:0",NOW,NOW.plusSeconds(600));
            if(outcome.equals("SENT"))row.acknowledge(token,1,"test-runtime:0",NOW,"500");
            else if(!outcome.equals("CLAIMED")) {
                row.fail(token,1,"test-runtime:0",NOW,"TEST_REJECTION",outcome.equals("UNCERTAIN"),NOW.plusSeconds(60));
                if(outcome.equals("REVIEWED_PENDING"))row.reconcileMissing(2,"test-previous-manager","Test previous review must not authorize restored work",NOW);
            }
        });
        clock.set(NOW.plusSeconds(3600));windows.closeDue(windowId,null);
        var before=tx.execute(status -> {var row=deliveryRows.findById(id).orElseThrow();return java.util.List.of(row.getState().name(),row.getVersion(),row.getRevision().getId(),row.getOperationKey());});
        var preview=imports.stage(upload(export()));imports.execute(preview.stagingId());
        tx.executeWithoutResult(status -> {
            var row=deliveryRows.findById(id).orElseThrow();assertThat(java.util.List.of(row.getState().name(),row.getVersion(),row.getRevision().getId(),row.getOperationKey())).isEqualTo(before);
            assertThat(row.isRestoreReviewed()).isFalse();assertThat(row.getReport().getWindow().isDeliveryPaused()).isTrue();assertThat(row.getReport().getFrozenRevisionNumber()).isEqualTo(2);
            var disclosure=deliveryRows.findByOperationKey(activeReport+":DISCLOSURE").orElseThrow();assertThat(disclosure.getRevision().getRevisionNumber()).isEqualTo(2);assertThat(disclosure.getState()).isEqualTo(org.ctc.domain.model.IncidentDeliveryState.PENDING);
            if(outcome.equals("REVIEWED_PENDING"))assertThat(row.getReconciledBy()).isEqualTo("test-previous-manager");
        });
        assertThat(delivery.dispatch(id)).isFalse();verifyNoInteractions(discord);
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "request", "owner", "fields", "missing-revisions", "count", "disguised-v7", "delivery-owner", "delivery-destination", "delivery-revision", "delivery-claim", "delivery-review-grant", "window-pause", "window-review"})
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
        for (String table : java.util.List.of("incident_match_contexts", "incident_report_windows", "incident_reports", "incident_report_revisions", "incident_report_events", "incident_deliveries")) {
            files.remove("data/" + table.replace('_', '-') + ".json"); counts.remove(table);
        }
        files.put("manifest.json", JSON.writeValueAsBytes(manifest));
        var preview = imports.stage(upload(zip(files))); imports.execute(preview.stagingId());
        assertThat(contextRows.findBySeasonId(fixture.season().getId())).isEmpty(); assertThat(windowRows.findByContextId(contextId)).isEmpty();
        assertThat(reportRows.findById(activeReport)).isEmpty(); verifyNoInteractions(discord);
    }

    UUID linkedPublication() {
        clock.set(NOW.plusSeconds(3600));windows.closeDue(windowId,null);
        return tx.execute(status -> {
            var config=configs.findById(configId).orElseThrow();config.setStewardsChannelId("600000000000000006");config.setIncidentStaffRoleIds("500000000000000005");config.setStewardsForumTagIds("900000000000000009");
            var row=deliveryRows.findByOperationKey(activeReport+":DISCLOSURE").orElseThrow();
            var mapping=threadRows.save(org.ctc.domain.model.IncidentThreadPublication.capture(row,config.getStewardsChannelId(),config.getIncidentStaffRoleIds(),config.getStewardsForumTagIds(),org.ctc.domain.service.IncidentThreadProjection.capture(row).messages().size()));
            for(int step=0;step<3;step++) {
                var token=UUID.randomUUID();row.claim(token,delivery.reviewGeneration(),clock.instant(),clock.instant().plusSeconds(600));long version=row.getVersion();
                mapping.accept(mapping.stage(),0,step==0?"601":"801",step==0?"600":step==2?"801":null);
                row.continueDisclosure(token,version,delivery.reviewGeneration(),clock.instant());
            }
            row.reconcileMissing(row.getVersion(),"test-previous-thread-manager","Test old review cannot authorize restored thread steps",clock.instant());
            assertThat(row.isRestoreReviewed()).isTrue();
            deliveryRows.findByOperationKey(activeReport+":RECEIPT").orElseThrow().supersede(clock.instant());
            return mapping.getId();
        });
    }
    @Test void givenLinkedThreadReferencesAndPrivateConfiguration_whenRestoring_thenKeepExactMappingAndFactsButPauseAllSending() throws Exception {
        var id=linkedPublication();byte[] archive=org.junit.jupiter.api.Assertions.assertDoesNotThrow(this::export);byte[] before=unzip(archive).get("data/incident-thread-publications.json");
        var preview=imports.stage(upload(archive));imports.execute(preview.stagingId());
        assertThat(unzip(export()).get("data/incident-thread-publications.json")).isEqualTo(before);
        UUID operation=tx.execute(status -> {
            var mapping=threadRows.findById(id).orElseThrow();assertThat(mapping.getPrivateThreadId()).isEqualTo("600");assertThat(mapping.getPrivateStarterId()).isEqualTo("601");assertThat(mapping.getPublicThreadId()).isEqualTo("801");assertThat(mapping.getReferenceMessageId()).isNull();assertThat(mapping.getDelivery().getPresentationVersion()).isEqualTo(1);assertThat(mapping.getDelivery().isRestoreReviewed()).isFalse();assertThat(mapping.getDelivery().getReport().getWindow().isDeliveryPaused()).isTrue();assertThat(mapping.getDelivery().getRevision().getPayload().getDescription()).isEqualTo("Test private correction");
            var config=configs.findById(configId).orElseThrow();assertThat(config.getStewardsChannelId()).isEqualTo("600000000000000006");assertThat(config.getIncidentStaffRoleIds()).isEqualTo("500000000000000005");assertThat(config.getStewardsForumTagIds()).isEqualTo("900000000000000009");return mapping.getDelivery().getId();
        });
        assertThat(threadPublication.dispatch(operation)).isFalse();verifyNoInteractions(discord);
    }
    @ParameterizedTest @ValueSource(strings={"thread-owner","configuration","presentation-version"})
    void givenLinkedThreadArchiveWithForgedOwnerOrMalformedConfiguration_whenStagingAndExecuting_thenRejectBeforeAnyWipe(String cause) throws Exception {
        var id=linkedPublication();var valid=export();var files=unzip(valid);
        String file=cause.equals("thread-owner")?"data/incident-thread-publications.json":cause.equals("presentation-version")?"data/incident-deliveries.json":"data/discord-global-config.json";
        var rows=(ArrayNode)JSON.readTree(files.get(file));var row=(ObjectNode)rows.get(0);
        if(cause.equals("thread-owner"))row.put("delivery",UUID.randomUUID().toString());
        else if(cause.equals("presentation-version"))row.put("presentationVersion",2);
        else row.put("incidentStaffRoleIds",500);
        files.put(file,JSON.writeValueAsBytes(rows));var broken=zip(files);
        assertThatThrownBy(() -> imports.stage(upload(broken))).isInstanceOfSatisfying(BackupArchiveException.class,error -> assertThat(error.reason()).isEqualTo(BackupArchiveException.Reason.DATA_MISMATCH));verify(imports,never()).wipeAllTables(anyMap());
        var preview=imports.stage(upload(valid));Files.write(ROOT.resolve("staging/upload-"+preview.stagingId()+".zip"),broken);clearInvocations(imports);
        assertThatThrownBy(() -> imports.execute(preview.stagingId())).isInstanceOf(BackupImportException.class).cause().isInstanceOfSatisfying(BackupArchiveException.class,error -> assertThat(error.reason()).isEqualTo(BackupArchiveException.Reason.DATA_MISMATCH));verify(imports,never()).wipeAllTables(anyMap());assertThat(threadRows.findById(id)).isPresent();verifyNoInteractions(discord);imports.deleteStagingFile(preview.stagingId());
    }
    @Test void givenLinkedThreadCapableRuntimeAndARealSchemaNineArchive_whenRestoring_thenInventNoPublicationAndKeepOldOutboxPaused() throws Exception {
        var files=unzip(export());var manifest=(ObjectNode)JSON.readTree(files.get("manifest.json"));manifest.put("schema_version",9);((ObjectNode)manifest.get("table_counts")).remove("incident_thread_publications");files.remove("data/incident-thread-publications.json");
        var configs=(ArrayNode)JSON.readTree(files.get("data/discord-global-config.json"));for(var config:configs)((ObjectNode)config).remove(java.util.List.of("stewardsChannelId","incidentStaffRoleIds","stewardsForumTagIds"));files.put("data/discord-global-config.json",JSON.writeValueAsBytes(configs));files.put("manifest.json",JSON.writeValueAsBytes(manifest));
        var preview=imports.stage(upload(zip(files)));imports.execute(preview.stagingId());assertThat(threadRows.count()).isZero();
        tx.executeWithoutResult(status -> {assertThat(windowRows.findById(windowId).orElseThrow().isDeliveryPaused()).isTrue();assertThat(this.configs.findById(configId).orElseThrow().getStewardsChannelId()).isEmpty();});verifyNoInteractions(discord);
    }

    UUID queuedRuling() {
        linkedPublication(); rulings.saveDraft(activeReport, 0, "Test public ruling one", "", manager, epoch.managementGeneration());
        return rulings.publish(activeReport, 1, 1, manager, epoch.managementGeneration());
    }
    void confirmRuling(UUID id) {
        tx.executeWithoutResult(status -> {
            var row = rulingPublications.findById(id).orElseThrow(); var token = UUID.randomUUID();
            row.claim(token, epoch.managementGeneration(), clock.instant(), clock.instant().plusSeconds(600));
            row.acknowledge(token, 1, epoch.managementGeneration(), clock.instant(), "950");
            row.getRevision().getRuling().confirmPublication(row.getRevision().getRevisionNumber(), row.getPreviousRevisionNumber(), "950");
        });
    }
    @ParameterizedTest @ValueSource(strings = {"SENT", "UNCERTAIN_CORRECTION", "REVIEWED_PENDING"})
    void rulingPublicationEvidenceRoundTripsExactlyAndLosesPriorResumeGrants(String outcome) throws Exception {
        UUID id = queuedRuling();
        if (!outcome.equals("REVIEWED_PENDING")) confirmRuling(id);
        if (outcome.equals("UNCERTAIN_CORRECTION")) {
            clock.set(clock.instant().plusSeconds(1));
            rulings.saveDraft(activeReport, 3, "Test public corrected ruling", "Test explicit correction reason", manager, epoch.managementGeneration());
            id = rulings.publish(activeReport, 2, 4, manager, epoch.managementGeneration());
        }
        UUID selected = id;
        if (!outcome.equals("SENT")) tx.executeWithoutResult(status -> {
            var row = rulingPublications.findById(selected).orElseThrow(); var token = UUID.randomUUID();
            row.claim(token, epoch.managementGeneration(), clock.instant(), clock.instant().plusSeconds(600));
            row.fail(token, 1, epoch.managementGeneration(), clock.instant(), "TEST_DELIVERY", outcome.equals("UNCERTAIN_CORRECTION"), clock.instant().plusSeconds(60));
            if (outcome.equals("REVIEWED_PENDING")) row.reconcileMissing(2, "test-previous-manager", "Test earlier remote review", clock.instant());
        });
        byte[] archive = export(); var before = unzip(archive);
        var preview = imports.stage(upload(archive)); imports.execute(preview.stagingId()); var after = unzip(export());
        for (String table : java.util.List.of("incident-rulings", "incident-ruling-revisions", "incident-ruling-publications"))
            assertThat(after.get("data/" + table + ".json")).isEqualTo(before.get("data/" + table + ".json"));
        tx.executeWithoutResult(status -> {
            var row = rulingPublications.findById(selected).orElseThrow(); assertThat(row.isRestoreReviewed()).isFalse();
            assertThat(row.getRevision().getRuling().getReport().getId()).isEqualTo(activeReport);
            assertThat(row.getRevision().getRuling().getReport().getWindow().isDeliveryPaused()).isTrue();
            if (outcome.equals("UNCERTAIN_CORRECTION")) {
                assertThat(row.getState()).isEqualTo(org.ctc.domain.model.IncidentDeliveryState.UNCERTAIN);
                assertThat(row.getPreviousMessageId()).isEqualTo("950"); assertThat(row.getRevision().getRuling().getPublishedRevisionNumber()).isEqualTo(1);
            }
        });
        assertThat(rulings.dispatch(selected)).isFalse(); verifyNoInteractions(discord);
    }
    @ParameterizedTest @ValueSource(strings = {"owner", "destination", "content", "review-grant", "stale-previous", "message-owner"})
    void malformedRulingPublicationEvidenceRejectsAtStageAndExecuteBeforeWiping(String cause) throws Exception {
        UUID id = queuedRuling();
        if (cause.equals("message-owner")) confirmRuling(id);
        if (cause.equals("stale-previous")) {
            confirmRuling(id); clock.set(clock.instant().plusSeconds(1));
            rulings.saveDraft(activeReport, 3, "Test public ruling two", "Test correction two", manager, epoch.managementGeneration());
            UUID second = rulings.publish(activeReport, 2, 4, manager, epoch.managementGeneration()); confirmRuling(second);
            clock.set(clock.instant().plusSeconds(1)); rulings.saveDraft(activeReport, 6, "Test public ruling three", "Test correction three", manager, epoch.managementGeneration());
            id = rulings.publish(activeReport, 3, 7, manager, epoch.managementGeneration());
        }
        var valid = export(); var files = unzip(valid); var rows = (ArrayNode) JSON.readTree(files.get("data/incident-ruling-publications.json"));
        ObjectNode selected = null; for (var row : rows) if (row.path("id").asText().equals(id.toString())) selected = (ObjectNode) row;
        assertThat(selected).isNotNull();
        switch (cause) {
            case "owner" -> selected.put("revision", UUID.randomUUID().toString());
            case "destination" -> selected.put("channelId", "999");
            case "content" -> selected.put("content", "Test unapproved substituted judgment");
            case "review-grant" -> selected.put("restoreReviewed", true);
            case "message-owner" -> {
                selected.put("messageId", "801");
                var roots = (ArrayNode) JSON.readTree(files.get("data/incident-rulings.json"));
                ((ObjectNode) roots.get(0)).put("publishedMessageId", "801"); files.put("data/incident-rulings.json", JSON.writeValueAsBytes(roots));
            }
            case "stale-previous" -> {
                String firstText = org.ctc.domain.service.IncidentRulingProjection.render(activeReport, firstReceipt.caseReference(), "Test public ruling one");
                selected.put("previousRevisionNumber", 1); selected.put("previousContent", firstText);
            }
            default -> throw new AssertionError(cause);
        }
        files.put("data/incident-ruling-publications.json", JSON.writeValueAsBytes(rows)); byte[] broken = zip(files);
        clearInvocations(imports);
        assertThatThrownBy(() -> imports.stage(upload(broken))).isInstanceOfSatisfying(BackupArchiveException.class,
                error -> assertThat(error.reason()).isEqualTo(BackupArchiveException.Reason.DATA_MISMATCH));
        verify(imports, never()).wipeAllTables(anyMap());
        var preview = imports.stage(upload(valid)); Files.write(ROOT.resolve("staging/upload-" + preview.stagingId() + ".zip"), broken); clearInvocations(imports);
        assertThatThrownBy(() -> imports.execute(preview.stagingId())).isInstanceOf(BackupImportException.class).cause()
                .isInstanceOfSatisfying(BackupArchiveException.class, error -> assertThat(error.reason()).isEqualTo(BackupArchiveException.Reason.DATA_MISMATCH));
        verify(imports, never()).wipeAllTables(anyMap()); imports.deleteStagingFile(preview.stagingId()); verifyNoInteractions(discord);
    }
    @Test void realSchemaTenArchivePreservesLegacyKnownThreadsAndInventsNoRulingsOrNewCaptionProof() throws Exception {
        UUID mappingId = linkedPublication(); var files = unzip(export()); var manifest = (ObjectNode) JSON.readTree(files.get("manifest.json"));
        manifest.put("schema_version", 10); var counts = (ObjectNode) manifest.get("table_counts");
        for (String table : java.util.List.of("incident_rulings", "incident_ruling_revisions", "incident_ruling_publications")) {
            counts.remove(table); files.remove("data/" + table.replace('_', '-') + ".json");
        }
        var deliveries = (ArrayNode) JSON.readTree(files.get("data/incident-deliveries.json"));
        for (var row : deliveries) ((ObjectNode) row).remove(java.util.List.of("pairingLabel", "raceLabel", "presentationVersion"));
        files.put("data/incident-deliveries.json", JSON.writeValueAsBytes(deliveries)); files.put("manifest.json", JSON.writeValueAsBytes(manifest));
        var preview = imports.stage(upload(zip(files))); imports.execute(preview.stagingId());
        assertThat(rulingRows.count()).isZero(); assertThat(rulingRevisions.count()).isZero(); assertThat(rulingPublications.count()).isZero();
        tx.executeWithoutResult(status -> {
            var mapping = threadRows.findById(mappingId).orElseThrow(); assertThat(mapping.getPublicThreadId()).isEqualTo("801");
            assertThat(mapping.getDelivery().getPairingLabel()).isNull(); assertThat(mapping.getDelivery().getRaceLabel()).isNull();
            String body = String.join("", org.ctc.domain.service.IncidentThreadProjection.capture(mapping.getDelivery()).messages());
            assertThat(body).contains("Test private correction").doesNotContain("Pairing:", "Race:");
            assertThat(mapping.getDelivery().getReport().getWindow().isDeliveryPaused()).isTrue();
        }); verifyNoInteractions(discord);
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
        else if (corruption.startsWith("window-")) {
            var rows=(ArrayNode) JSON.readTree(files.get("data/incident-report-windows.json"));
            ((ObjectNode)rows.get(0)).put(corruption.equals("window-pause")?"deliveryPaused":"deliveryResumedBy",corruption.equals("window-pause")?"false":"forged-reviewer");
            files.put("data/incident-report-windows.json",JSON.writeValueAsBytes(rows));
        }
        else if (corruption.startsWith("delivery-")) {
            var rows = (ArrayNode) JSON.readTree(files.get("data/incident-deliveries.json")); var delivery = (ObjectNode) rows.get(0);
            if (corruption.equals("delivery-owner")) delivery.put("report", UUID.randomUUID().toString());
            else if (corruption.equals("delivery-destination")) delivery.put("channelId", "999");
            else if(corruption.equals("delivery-review-grant")) delivery.put("restoreReviewed",true);
            else if (corruption.equals("delivery-claim")) delivery.put("state", "CLAIMED");
            else {
                for (var revision : JSON.readTree(files.get("data/incident-report-revisions.json"))) {
                    if (!revision.path("report").asText().equals(delivery.path("report").asText())) { delivery.put("revision", revision.path("id").asText()); break; }
                }
            }
            files.put("data/incident-deliveries.json", JSON.writeValueAsBytes(rows));
        } else if (corruption.equals("request")) {
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
