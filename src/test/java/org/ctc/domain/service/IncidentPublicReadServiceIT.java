package org.ctc.domain.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.sitegen.IncidentPageGenerator;
import org.ctc.sitegen.SiteGeneratorService;
import org.ctc.sitegen.model.GenerationContext;
import org.ctc.sitegen.model.SiteSlugs;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
class IncidentPublicReadServiceIT extends IncidentIntegrationFixture {
    @Autowired IncidentPublicReadService reads;
    @Autowired IncidentRulingService rulings;
    @Autowired IncidentRulingRepository rulingRows;
    @Autowired IncidentRulingPublicationRepository publications;
    @Autowired IncidentDeliveryRepository deliveries;
    @Autowired IncidentThreadPublicationRepository threads;
    @Autowired PickSessionEpoch epoch;
    @Autowired IncidentPageGenerator page;
    @MockitoBean IncidentRulingSender sender;

    record Work(UUID report, UUID publication) { }
    Work queued() {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        UUID report = reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload).reportId();
        rulings.saveDraft(report, 0, "Public final verdict <script>unsafe()</script>", "INTERNAL-REVIEW-NOTE", manager, epoch.managementGeneration());
        clock.set(NOW.plusSeconds(3600));windows.closeDue(windowId, null);
        tx.executeWithoutResult(status -> {
            var disclosure = deliveries.findByOperationKey(report + ":DISCLOSURE").orElseThrow();
            var mapping = threads.save(IncidentThreadPublication.capture(disclosure, "600", "500", "", 1));
            mapping.accept(IncidentThreadStage.PRIVATE_STARTER, 0, "701", "701");
            mapping.accept(IncidentThreadStage.PUBLIC_STARTER, 0, "801", null);
            mapping.accept(IncidentThreadStage.PUBLIC_THREAD, 0, "801", "801");
            mapping.accept(IncidentThreadStage.PRIVATE_REFERENCE, 0, "901", null);
            var token = UUID.randomUUID();disclosure.claim(token, epoch.managementGeneration(), clock.instant(), clock.instant().plusSeconds(600));
            disclosure.acknowledge(token, 1, epoch.managementGeneration(), clock.instant(), "801");
            deliveries.findByOperationKey(report + ":RECEIPT").orElseThrow().supersede(clock.instant());
        });
        return new Work(report, rulings.publish(report, 1, 1, manager, epoch.managementGeneration()));
    }
    void confirm(Work work) {
        when(sender.send(any())).thenReturn(new IncidentSendResult(IncidentSendResult.Disposition.SENT, "950", null));
        assertThat(rulings.dispatch(work.publication())).isTrue();
    }

    @Test
    void givenUnconfirmedRuling_whenReading_thenKeepReportAndVerdictOutOfPublicExport() {
        queued();
        assertThat(reads.forSeason(fixture.season().getId())).isEmpty();
    }

    @Test
    void givenConfirmedRulingWithNewDraft_whenReadingAndRendering_thenPublishExactConfirmedTextAndNoPrivateMetadata() throws Exception {
        var work = queued();confirm(work);
        rulings.saveDraft(work.report(), 3, "UNAPPROVED-SECRET-DRAFT", "INTERNAL-CORRECTION-REASON", manager, epoch.managementGeneration());
        var publicCases = reads.forSeason(fixture.season().getId());
        assertThat(publicCases).singleElement().satisfies(item -> {
            assertThat(item.description()).isEqualTo(payload.getDescription());
            assertThat(item.evidenceReferences()).isEqualTo(payload.getEvidenceLinks());
            assertThat(item.ruling()).isEqualTo("Public final verdict <script>unsafe()</script>");
            assertThat(item.rulingVersion()).isEqualTo(1);
        });
        Path out = Path.of("target/incident-public-preview", UUID.randomUUID().toString()).toAbsolutePath();Files.createDirectories(out);
        var result = new SiteGeneratorService.GenerationResult();
        var season = tx.execute(status -> seasons.findById(fixture.season().getId()).orElseThrow());
        page.generate(new GenerationContext(out, season, "", "", false, null, new SiteSlugs(Map.of(), Map.of(), List.of())), result);
        Path htmlFile;
        try (var files = Files.walk(out)) { htmlFile = files.filter(path -> path.getFileName().toString().equals("incidents.html")).findFirst().orElseThrow(); }
        String html = Files.readString(htmlFile);
        assertThat(html).contains("Public final verdict &lt;script&gt;unsafe()&lt;/script&gt;", "Incident report", "Stewards' ruling")
                .doesNotContain("<script>unsafe()", "INTERNAL-REVIEW-NOTE", "UNAPPROVED-SECRET-DRAFT", "INTERNAL-CORRECTION-REASON",
                        actor.userId(), manager.id(), "[CTC ", "discord.com/channels/");
        var document = Jsoup.parse(html);
        assertThat(document.select(".public-incident")).hasSize(1);
        assertThat(document.select(".public-incident-ruling").text()).contains("Public final verdict");
        assertThat(document.select(".subnav-link.active").text()).isEqualTo("Incidents & rulings");
        Files.writeString(Path.of("target/incident-public-preview/latest.txt"), htmlFile.toString());
    }

    @Test
    void givenConfirmedCase_whenReadingOtherSeason_thenDoNotCrossPublishIt() {
        var work = queued();confirm(work);
        var other = tx.execute(status -> helper.createFullSeasonFixture("TIP" + UUID.randomUUID().toString().substring(0, 7)));
        extras.add(other);
        assertThat(reads.forSeason(other.season().getId())).isEmpty();
        assertThat(reads.forSeason(fixture.season().getId())).hasSize(1);
    }

    @Test
    void givenUncertainCorrection_whenReading_thenKeepLastConfirmedVerdict() {
        var work = queued();confirm(work);
        rulings.saveDraft(work.report(), 3, "UNCONFIRMED-CORRECTION", "Internal reason", manager, epoch.managementGeneration());
        UUID correction = rulings.publish(work.report(), 2, 4, manager, epoch.managementGeneration());
        when(sender.send(any())).thenReturn(new IncidentSendResult(IncidentSendResult.Disposition.UNCERTAIN, null, "SEND_UNCERTAIN"));
        assertThat(rulings.dispatch(correction)).isTrue();
        assertThat(reads.forSeason(fixture.season().getId())).singleElement().satisfies(item ->
                assertThat(item.ruling()).isEqualTo("Public final verdict <script>unsafe()</script>"));
    }
}
