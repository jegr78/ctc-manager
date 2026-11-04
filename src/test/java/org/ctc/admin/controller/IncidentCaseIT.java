package org.ctc.admin.controller;

import java.util.UUID;
import org.ctc.admin.service.IncidentCaseAdminService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.IncidentPayload;
import org.ctc.domain.repository.IncidentRulingRepository;
import org.ctc.domain.repository.IncidentRulingRevisionRepository;
import org.ctc.domain.service.IncidentIntegrationFixture;
import org.ctc.domain.service.IncidentRulingService;
import org.ctc.domain.service.PickSessionEpoch;
import org.ctc.discord.DiscordRestClient;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Tag("integration")
class IncidentCaseIT extends IncidentIntegrationFixture {
    @Autowired MockMvc mvc;
    @Autowired IncidentCaseAdminService cases;
    @Autowired IncidentRulingService rulings;
    @Autowired IncidentRulingRepository rulingRows;
    @Autowired IncidentRulingRevisionRepository rulingRevisions;
    @Autowired PickSessionEpoch epoch;
    @MockitoBean DiscordRestClient discord;

    @Test
    void permanentWithdrawnCaseKeepsEveryReportRevisionAndTrustedAuthor() throws Exception {
        UUID report = submitted();
        reports.edit(report, 1, 1, UUID.randomUUID(), actor, new IncidentPayload("Test second driver", "Lap 2", "Test later report", ""));
        reports.withdraw(report, 1, 2, UUID.randomUUID(), actor);
        var page = assertDoesNotThrow(() -> cases.view(report));
        assertThat(page.reportId()).isEqualTo(report);
        var response = mvc.perform(get(path(report))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var html = Jsoup.parse(response);
        assertThat(html.text()).contains("Withdrawn", payload.getDescription(), "Test later report", actor.userId(), "SUBMITTED", "EDITED", "WITHDRAWN");
        assertThat(html.select("script").stream().map(element -> element.data()).toList()).noneMatch(value -> value.contains("Test <private>"));
        assertThat(html.select("a[href='/admin/incident-contexts/" + contextId + "']")).isNotEmpty();
        verifyNoInteractions(discord);
    }

    @Test
    @org.springframework.security.test.context.support.WithMockUser("test-steward")
    void nativeDraftStoresThePrincipalAndPreviewsEscapedTextWithoutPublishingOrScoring() throws Exception {
        UUID report = submitted();
        assertDoesNotThrow(() -> cases.view(report));
        mvc.perform(post(path(report) + "/ruling/save").principal(() -> "test-steward")
                .param("expectedVersion", "0").param("generation", epoch.managementGeneration())
                .param("text", "Test ruling <script>alert('test')</script>").param("reason", "Test initial decision")
                .param("actorId", "forged-reporter").param("publishedMessageId", "999"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path(report)));
        tx.executeWithoutResult(status -> {
            var root = rulingRows.findByReportId(report).orElseThrow();
            var revisions = rulingRevisions.findByRulingIdOrderByRevisionNumberAsc(root.getId());
            assertThat(revisions).hasSize(1); assertThat(revisions.getFirst().getActorId()).isEqualTo("test-steward");
            assertThat(root.getPublishedMessageId()).isNull();
            assertThat(races.findById(fixture.race().getId()).orElseThrow().getResults()).isEmpty();
        });
        String html = mvc.perform(get(path(report))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Test ruling &lt;script&gt;").doesNotContain("<script>alert('test')</script>");
        assertThat(Jsoup.parse(html).text()).contains("Public preview", "No ruling published", "test-steward");
        verifyNoInteractions(discord);
    }

    @Test
    void persistedDraftHistorySurvivesLaterDraftsAndRejectedStaleWrites() {
        UUID report = submitted();
        rulings.saveDraft(report, 0, "Test first ruling", "", manager, epoch.managementGeneration());
        rulings.saveDraft(report, 1, "Test second ruling", "Test correction", manager, epoch.managementGeneration());
        assertThatThrownBy(() -> rulings.saveDraft(report, 1, "Test stale ruling", "", manager, epoch.managementGeneration()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
        tx.executeWithoutResult(status -> {
            var root = rulingRows.findByReportId(report).orElseThrow();
            assertThat(root.getVersion()).isEqualTo(2);
            assertThat(rulingRevisions.findByRulingIdOrderByRevisionNumberAsc(root.getId())).extracting("text")
                    .containsExactly("Test first ruling", "Test second ruling");
        });
        verifyNoInteractions(discord);
    }

    @Test
    void staleNativeSubmissionRetainsItsOwnTextAndVersionForExplicitReload() throws Exception {
        UUID report = submitted();
        rulings.saveDraft(report, 0, "Test accepted draft", "", manager, epoch.managementGeneration());
        assertDoesNotThrow(() -> cases.view(report));
        String response = mvc.perform(post(path(report) + "/ruling/save").principal(() -> "test-steward")
                .param("expectedVersion", "0").param("generation", epoch.managementGeneration())
                .param("text", "Test stale entered draft").param("reason", "Test entered reason"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("rulingDraftForm"))
                .andReturn().getResponse().getContentAsString();
        var html = Jsoup.parse(response);
        assertThat(html.selectFirst("textarea[name=text]").text()).isEqualTo("Test stale entered draft");
        assertThat(html.selectFirst("input[name=expectedVersion]").val()).isEqualTo("0");
        assertThat(html.text()).contains("changed", "Reload current case");
        verifyNoInteractions(discord);
    }

    @Test
    void explicitPublicationQueuesTheExactSavedVersionAndKnownThreadWithoutSendingInsideTheRequest() {
        UUID report = submitted();
        rulings.saveDraft(report, 0, "Test explicitly approved ruling", "", manager, epoch.managementGeneration());
        clock.set(NOW.plusSeconds(3600)); windows.closeDue(windowId, null);
        tx.executeWithoutResult(status -> {
            var delivery = em.createQuery("select d from IncidentDelivery d where d.operationKey=:key", org.ctc.domain.model.IncidentDelivery.class)
                    .setParameter("key", report + ":DISCLOSURE").getSingleResult();
            var mapping = org.ctc.domain.model.IncidentThreadPublication.capture(delivery, "600", "500", "", 1);
            em.persist(mapping);
            mapping.accept(org.ctc.domain.model.IncidentThreadStage.PRIVATE_STARTER, 0, "701", "701");
            mapping.accept(org.ctc.domain.model.IncidentThreadStage.PUBLIC_STARTER, 0, "801", null);
            mapping.accept(org.ctc.domain.model.IncidentThreadStage.PUBLIC_THREAD, 0, "801", "801");
            mapping.accept(org.ctc.domain.model.IncidentThreadStage.PRIVATE_REFERENCE, 0, "901", null);
            var token = UUID.randomUUID(); delivery.claim(token, epoch.managementGeneration(), clock.instant(), clock.instant().plusSeconds(600));
            delivery.acknowledge(token, 1, epoch.managementGeneration(), clock.instant(), "801");
        });
        UUID work = assertDoesNotThrow(() -> rulings.publish(report, 1, 1, manager, epoch.managementGeneration()));
        assertThat(work).isNotNull();
        tx.executeWithoutResult(status -> {
            var publication = em.find(org.ctc.domain.model.IncidentRulingPublication.class, work);
            assertThat(publication.getRevision().getText()).isEqualTo("Test explicitly approved ruling");
            assertThat(publication.getChannelId()).isEqualTo("801");
            assertThat(publication.getState()).isEqualTo(org.ctc.domain.model.IncidentDeliveryState.PENDING);
            assertThat(publication.getRequestedBy()).isEqualTo(manager.id());
            var root = rulingRows.findByReportId(report).orElseThrow();
            assertThat(root.getRequestedRevisionNumber()).isEqualTo(1); assertThat(root.getPublishedRevisionNumber()).isNull();
        });
        verifyNoInteractions(discord);
    }

    @Test void missingImmutableRulingHistoryCannotBePresentedAsAnEmptyNewDraft() {
        UUID report = submitted(); rulings.saveDraft(report, 0, "Test permanent ruling", "", manager, epoch.managementGeneration());
        tx.executeWithoutResult(status -> em.createNativeQuery("DELETE FROM incident_ruling_revisions WHERE ruling_id=(SELECT id FROM incident_rulings WHERE report_id=:id)")
                .setParameter("id", report).executeUpdate());
        assertThatThrownBy(() -> cases.view(report)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("history");
    }

    private UUID submitted() {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        return reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload).reportId();
    }
    private static String path(UUID report) { return "/admin/incidents/" + report; }
}
