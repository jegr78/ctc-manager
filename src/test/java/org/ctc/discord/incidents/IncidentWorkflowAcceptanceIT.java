package org.ctc.discord.incidents;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.ctc.admin.service.IncidentCaseAdminService;
import org.ctc.admin.service.TemplatePreviewService;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.picks.PickGatewayInteraction;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.*;
import org.ctc.integration.OutboundPolicy;
import org.ctc.sitegen.SiteGeneratorService;
import org.ctc.testsupport.SitegenTestDir;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
@ExtendWith(OutputCaptureExtension.class)
class IncidentWorkflowAcceptanceIT extends IncidentIntakeIntegrationFixture {
    static final Path ROOT = SitegenTestDir.create("incident-acceptance");
    static final String ORIGINAL = "PRIVATE-293-original-7f81";
    static final String FINAL = "PRIVATE-293-final-93a2";
    static final String WITHDRAWN = "PRIVATE-293-withdrawn-a91c";
    static final String RULING = "Test approved steward decision 293";
    static final String REASON = "PRIVATE-293-management-reason-826d";
    @DynamicPropertySource
    static void paths(DynamicPropertyRegistry properties) {
        properties.add("app.upload-dir", () -> ROOT.resolve("uploads").toString());
        properties.add("app.backup.staging-dir", () -> ROOT.resolve("staging").toString());
        properties.add("app.backup.import-backups-dir", () -> ROOT.resolve("imports").toString());
        properties.add("ctc.site.output-dir", () -> ROOT.resolve("site").toString());
    }
    @Autowired BackupArchiveService archives;
    @Autowired BackupImportService backupImports;
    @Autowired IncidentDeliveryService receipts;
    @Autowired IncidentThreadPublicationService disclosures;
    @Autowired IncidentRulingService rulings;
    @Autowired IncidentCaseAdminService cases;
    @Autowired IncidentDeliveryRepository deliveries;
    @Autowired IncidentRulingPublicationRepository publications;
    @Autowired SiteGeneratorService site;
    @Autowired TemplatePreviewService previews;
    @MockitoBean DiscordRestClient discord;
    @MockitoBean IncidentReceiptSender receiptPort;
    @MockitoBean IncidentThreadSender threadPort;
    @MockitoBean IncidentRulingSender rulingPort;

    String previousParent, previousStaff, previousTags;
    @org.junit.jupiter.api.BeforeEach void rememberSettings() {
        tx.executeWithoutResult(status -> {
            var config = configs.findById(configId).orElseThrow();
            previousParent = config.getStewardsChannelId(); previousStaff = config.getIncidentStaffRoleIds(); previousTags = config.getStewardsForumTagIds();
        });
    }
    @org.junit.jupiter.api.AfterEach void restoreSettings() {
        tx.executeWithoutResult(status -> configs.findById(configId).ifPresent(config -> {
            config.setStewardsChannelId(previousParent); config.setIncidentStaffRoleIds(previousStaff); config.setStewardsForumTagIds(previousTags);
        }));
    }

    @Override LocalDateTime plannedRaceStart() { return LocalDateTime.ofInstant(NOW.minusSeconds(3600), ZoneId.of("Europe/Berlin")); }
    @Override Instant openingDeadline() { return windows.previewDeadline(contextId).defaultDeadline(); }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void givenTeamOrOrganization_whenCompletingPrivateIntakeThroughRestore_thenRetainHistoryAndPublishOnlyApprovedFacts(
            boolean sharedOrganization, CapturedOutput output) throws Exception {
        // given
        Files.createDirectories(ROOT.resolve("uploads"));
        UUID partyId = fixture.homeTeam().getId();
        if (sharedOrganization) {
            var organization = parent(); partyId = organization.getId();
            tx.executeWithoutResult(status -> {
                var managed = teams.findById(organization.getId()).orElseThrow();
                for (UUID team : List.of(fixture.homeTeam().getId(), fixture.awayTeam().getId())) {
                    var row = teams.findById(team).orElseThrow(); row.setParentTeam(managed); row.setDiscordRoleId(null);
                }
            });
        }
        var defaultDeadline = NOW.minusSeconds(3600).plusSeconds(24 * 3600);
        Instant actualDefault = tx.execute(status -> windowRows.findById(windowId).orElseThrow().getDeadline());
        assertThat(actualDefault).isEqualTo(defaultDeadline);
        var deadline = NOW.plusSeconds(120);
        windows.changeDeadline(contextId, deadline, 1, "Test explicit cutoff", manager);
        List<String> neutralPayloads = new ArrayList<>();
        List<IncidentThreadClaim> steps = new ArrayList<>();
        List<IncidentRulingClaim> approved = new ArrayList<>();
        var sequence = new AtomicInteger(500);
        var realReceiptAdapter = new DiscordIncidentNoticeSender(discord, configs, new OutboundPolicy(true, new MockEnvironment()));
        when(receiptPort.send(any())).thenAnswer(call -> realReceiptAdapter.send(call.getArgument(0)));
        when(discord.fetchAccessChannel(CHANNEL)).thenReturn(new DiscordRestClient.AccessChannel(CHANNEL, GUILD, 0, List.of()));
        when(discord.createBotMessage(eq(CHANNEL), anyString(), anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            String content = call.getArgument(2); neutralPayloads.add(content);
            return new DiscordRestClient.BotMessage(Integer.toString(sequence.getAndIncrement()), CHANNEL, content, null, null);
        });
        when(threadPort.send(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            IncidentThreadClaim claim = call.getArgument(0); steps.add(claim);
            return switch (claim.work().stage()) {
                case PRIVATE_STARTER -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT, "701", null, null);
                case PRIVATE_THREAD -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT, "701", "701", null);
                case PUBLIC_STARTER -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT, "801", null, null);
                case PUBLIC_THREAD -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT, "801", "801", null);
                case PRIVATE_REFERENCE -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT, "901", null, null);
                default -> throw new AssertionError("Unexpected chunk or completed step");
            };
        });
        when(rulingPort.send(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            approved.add(call.getArgument(0)); return new IncidentSendResult(IncidentSendResult.Disposition.SENT, "950", null);
        });

        // when: actual Gateway confirmations, replay, edit and withdrawal
        var client = flow().draft(fixture.race().getId(), ORIGINAL);
        String confirmation = client.button("Confirm report"); client.click("Confirm report");
        String receipt = client.message().content();
        client.send(PickGatewayInteraction.Kind.BUTTON, confirmation, Map.of());
        assertThat(client.message().content()).isEqualTo(receipt);
        UUID active = rows().getFirst().getId();
        client.edit(active, FINAL).click("Confirm changes");
        flow().draft(fixture.race().getId(), WITHDRAWN).click("Confirm report");
        UUID withdrawn = rows().stream().filter(row -> !row.getId().equals(active)).findFirst().orElseThrow().getId();
        client.command().click("My reports").select(withdrawn).click("Withdraw report").click("Confirm withdrawal");
        assertThat(firstRequests()).isEqualTo(2);
        for (UUID delivery : receipts.dueReceipts(10)) assertThat(receipts.dispatch(delivery)).isTrue();
        assertThat(neutralPayloads).containsExactly(IncidentNoticeService.neutralText(), IncidentNoticeService.neutralText());
        assertThat(disclosures.dueDisclosures(10)).isEmpty();
        verifyNoInteractions(threadPort, rulingPort);
        assertPrivateAbsent(flow(OTHER, CHANNEL).command().click("My reports").message().content());
        roles.put(OTHER, java.util.Set.of("999999999999999999"));
        assertPrivateAbsent(flow(OTHER, CHANNEL).command().text());
        String preview = previews.renderPreview("settings", "<p th:text='${carName}'></p>");
        assertThat(preview).contains("Mazda RX-Vision GT3"); assertPrivateAbsent(preview);
        var generated = site.generate(); assertThat(generated.hasErrors()).isFalse();
        var artifacts = new ArrayList<Path>();
        try (var paths = Files.walk(ROOT.resolve("site"))) { paths.filter(Files::isRegularFile).forEach(artifacts::add); }
        assertThat(artifacts).anyMatch(path -> path.toString().endsWith(".html"));
        boolean fixtureRendered = false;
        for (Path artifact : artifacts) {
            String content = new String(Files.readAllBytes(artifact), StandardCharsets.UTF_8);
            assertPrivateAbsent(content);
            fixtureRendered |= content.contains(fixture.season().getName());
        }
        assertThat(fixtureRendered).as("the synthetic incident season must be public output").isTrue();
        assertPrivateAbsent(output.getAll());
        var lateEdit = client.edit(active, "Test rejected late change");
        clock.set(deadline); lateEdit.click("Confirm changes");
        assertPrivateAbsent(lateEdit.text());
        assertThat(latestDescription(active)).isEqualTo(FINAL);

        // then: exact cutoff discloses only the final active revision
        windows.closeDue(windowId, null);
        verifyNoInteractions(threadPort, rulingPort);
        tx.executeWithoutResult(status -> {
            var config = configs.findById(configId).orElseThrow(); config.setStewardsChannelId("600000000000000006"); config.setIncidentStaffRoleIds("500000000000000005"); config.setStewardsForumTagIds("");
            var match = matches.findById(fixture.match().getId()).orElseThrow(); match.setHomeScore(120); match.setAwayScore(80);
        });
        UUID disclosure = tx.execute(status -> deliveries.findByOperationKey(active + ":DISCLOSURE").orElseThrow().getId());
        for (int index = 0; index < 5; index++) assertThat(disclosures.dispatch(disclosure)).isTrue();
        assertThat(disclosures.dispatch(disclosure)).isFalse();
        assertThat(steps).hasSize(5);
        String publicBody = steps.stream().filter(claim -> claim.work().stage() == IncidentThreadStage.PUBLIC_STARTER).findFirst().orElseThrow().work().content();
        assertThat(publicBody).contains(FINAL, "Test involved guest", rows().getFirst().getParty().getLabel()).doesNotContain(ORIGINAL, WITHDRAWN, USER, manager.id(), REASON);
        rulings.saveDraft(active, 0, RULING, REASON, manager, epoch.managementGeneration());
        verifyNoInteractions(rulingPort);
        UUID publication = rulings.publish(active, 1, 1, manager, epoch.managementGeneration());
        assertThat(rulings.dispatch(publication)).isTrue();
        assertThat(approved).singleElement().satisfies(claim -> assertThat(claim.content()).contains(RULING).doesNotContain(USER, manager.id(), REASON));
        var revisionIds = tx.execute(status -> revisionRows.findByReportIdOrderByRevisionNumberAsc(active).stream().map(IncidentReportRevision::getId).toList());
        var before = cases.view(active);
        var bytes = new ByteArrayOutputStream(); archives.writeZip(bytes, clock.instant());
        rulings.saveDraft(active, 3, "Test post-backup draft", "Test later correction", manager, epoch.managementGeneration());
        tx.executeWithoutResult(status -> matches.findById(fixture.match().getId()).orElseThrow().setHomeScore(999));
        var staged = backupImports.stage(new MockMultipartFile("file", "test-incidents.zip", "application/zip", bytes.toByteArray()));
        backupImports.execute(staged.stagingId());
        UUID expectedParty = partyId;
        tx.executeWithoutResult(status -> {
            var window = windowRows.findById(windowId).orElseThrow();
            assertThat(window.getPlannedStart()).isEqualTo(NOW.minusSeconds(3600));
            assertThat(window.getPlannedStartLocal()).isEqualTo(plannedRaceStart());
            assertThat(window.getDeadline()).isEqualTo(deadline); assertThat(window.isDeliveryPaused()).isTrue();
            var report = reportRows.findById(active).orElseThrow();
            assertThat(report.getAuthorId()).isEqualTo(USER); assertThat(report.getFrozenRevisionNumber()).isEqualTo(2);
            assertThat(report.getParty().getId()).isEqualTo(expectedParty);
            assertThat(report.getParty().getType()).isEqualTo(sharedOrganization ? IncidentPartyType.ORGANIZATION : IncidentPartyType.TEAM);
            assertThat(reportRows.findById(withdrawn).orElseThrow().getState()).isEqualTo(IncidentReportState.WITHDRAWN);
            assertThat(revisionRows.findByReportIdOrderByRevisionNumberAsc(active)).extracting(IncidentReportRevision::getId).containsExactlyElementsOf(revisionIds);
            assertThat(deliveries.findByReportWindowId(windowId)).hasSize(3).allSatisfy(row -> {
                assertThat(row.getState()).isEqualTo(IncidentDeliveryState.SENT); assertThat(row.isRestoreReviewed()).isFalse();
            });
            assertThat(publications.findById(publication).orElseThrow().getState()).isEqualTo(IncidentDeliveryState.SENT);
            var match = matches.findById(fixture.match().getId()).orElseThrow();
            assertThat(match.getHomeScore()).isEqualTo(120); assertThat(match.getAwayScore()).isEqualTo(80);
        });
        var restored = cases.view(active);
        assertThat(restored.reportHistory()).isEqualTo(before.reportHistory());
        assertThat(restored.rulingHistory()).isEqualTo(before.rulingHistory());
        assertThat(restored.privateThreadUrl()).endsWith("/701"); assertThat(restored.publicThreadUrl()).endsWith("/801");
        assertThat(restored.rulingUrl()).endsWith("/801/950"); assertThat(restored.paused()).isTrue();
        clearInvocations(receiptPort, threadPort, rulingPort);
        assertThat(receipts.dueReceipts(10)).isEmpty(); assertThat(disclosures.dispatch(disclosure)).isFalse();
        assertThat(rulings.dispatch(publication)).isFalse(); verifyNoInteractions(receiptPort, threadPort, rulingPort);
    }

    private static void assertPrivateAbsent(String value) {
        assertThat(value).doesNotContain(ORIGINAL, FINAL, WITHDRAWN, USER, REASON, "Test involved guest", "https://example.invalid/video");
    }
}
