package org.ctc.admin.service;

import java.util.UUID;
import org.ctc.admin.dto.DataAuditFinding.Category;
import org.ctc.domain.service.IncidentIntegrationFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class IncidentIntegrityAuditIT extends IncidentIntegrationFixture {
    @Autowired DataAuditService audit;
    @Autowired org.ctc.domain.service.IncidentRulingService rulings;
    @Autowired org.ctc.domain.repository.IncidentRulingRepository rulingRows;
    @Autowired org.ctc.domain.repository.IncidentRulingRevisionRepository rulingRevisions;
    @Autowired org.ctc.domain.service.PickSessionEpoch epoch;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.ctc.domain.repository.IncidentDeliveryRepository deliveries;
    @Autowired org.ctc.domain.repository.IncidentThreadPublicationRepository publications;

    @Test
    void validPermanentHistoryHasNoIncidentFindingAndIsUnchanged() {
        var id = submit(); var rows = reportRows.count();
        assertThat(audit.audit().findings(Category.INCIDENT_REPORTS)).isEmpty();
        assertThat(reportRows.count()).isEqualTo(rows);
        assertThat(reportRows.findById(id).orElseThrow().getVersion()).isEqualTo(1);
        assertThat(revisionRows.findByReportIdOrderByRevisionNumberAsc(id)).hasSize(1);
    }

    @Test
    void historicalVersionMismatchIsLocatedWithoutPrivatePayloadOrRepairs() {
        var id = submit();
        jdbc.update("update incident_reports set aggregate_version = 99 where id = ?", id);
        var rows = reportRows.count();
        assertThat(audit.audit().findings(Category.INCIDENT_REPORTS)).singleElement().satisfies(finding -> {
            assertThat(finding.subject()).contains(id.toString());
            assertThat(finding.evidence()).doesNotContain(payload.getDescription()).doesNotContain(payload.getEvidenceLinks());
        });
        assertThat(reportRows.count()).isEqualTo(rows);
        assertThat(reportRows.findById(id).orElseThrow().getVersion()).isEqualTo(99);
        assertThat(revisionRows.findByReportIdOrderByRevisionNumberAsc(id)).hasSize(1);
    }

    @Test void contradictoryThreadConfigurationIsLocatedWithoutRepairingItsPermanentMappingOrLeakingFacts() {
        var report=submit();clock.set(NOW.plusSeconds(3600));windows.closeDue(windowId,null);
        var mapping=tx.execute(status -> {
            var row=deliveries.findByOperationKey(report+":DISCLOSURE").orElseThrow();
            return publications.saveAndFlush(org.ctc.domain.model.IncidentThreadPublication.capture(row,"600","500","",org.ctc.domain.service.IncidentThreadProjection.capture(row).messages().size())).getId();
        });
        assertThat(audit.audit().findings(Category.INCIDENT_REPORTS)).isEmpty();
        jdbc.update("update incident_thread_publications set staff_role_ids = ? where id = ?","500,500",mapping);long count=publications.count();
        assertThat(audit.audit().findings(Category.INCIDENT_REPORTS)).singleElement().satisfies(finding -> {assertThat(finding.subject()).contains(mapping.toString());assertThat(finding.evidence()).doesNotContain(payload.getDescription(),payload.getEvidenceLinks());});
        assertThat(publications.count()).isEqualTo(count);assertThat(publications.findById(mapping).orElseThrow().getStaffRoleIds()).isEqualTo("500,500");
    }

    @Test void contradictoryRulingVersionIsLocatedWithoutRepairingItsTextOrAttribution() {
        var report = submit(); rulings.saveDraft(report, 0, "Test PRIVATE steward judgment", "Test internal reason", manager, epoch.managementGeneration());
        UUID root = tx.execute(status -> rulingRows.findByReportId(report).orElseThrow().getId());
        assertThat(audit.audit().findings(Category.INCIDENT_REPORTS)).isEmpty();
        jdbc.update("UPDATE incident_rulings SET aggregate_version=99 WHERE id=?", root);
        assertThat(audit.audit().findings(Category.INCIDENT_REPORTS)).singleElement().satisfies(finding -> {
            assertThat(finding.subject()).contains(root.toString()); assertThat(finding.evidence()).doesNotContain("Test PRIVATE steward judgment", "Test internal reason");
        });
        tx.executeWithoutResult(status -> assertThat(rulingRows.findById(root).orElseThrow().getVersion()).isEqualTo(99));
    }

    @Test void actualClockObservationsDoNotMakeAnOtherwiseValidRulingHistoryUnrestorable() {
        UUID report = submit(); rulings.saveDraft(report, 0, "Test first clock observation", "", manager, epoch.managementGeneration());
        clock.set(NOW.minusSeconds(1));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> rulings.saveDraft(report, 1, "Test later version after clock correction", "", manager, epoch.managementGeneration()));
        assertThat(audit.audit().findings(Category.INCIDENT_REPORTS)).isEmpty();
        tx.executeWithoutResult(status -> {
            var root = rulingRows.findByReportId(report).orElseThrow(); var history = rulingRevisions.findByRulingIdOrderByRevisionNumberAsc(root.getId());
            assertThat(history).extracting(row -> row.getRevisionNumber()).containsExactly(1, 2);
            assertThat(history.getFirst().getAcceptedAt()).isEqualTo(NOW); assertThat(history.getLast().getAcceptedAt()).isEqualTo(NOW.minusSeconds(1));
        });
    }

    @Test
    void givenAnExpiredOpenWindow_whenAuditing_thenReportTheDueClosureWithoutChangingHistory() {
        UUID report = submit();
        clock.set(NOW.plusSeconds(3600));
        assertThat(audit.audit().findings(Category.INCIDENT_REPORTS)).singleElement().satisfies(finding -> {
            assertThat(finding.subject()).contains(windowId.toString());
            assertThat(finding.evidence()).contains("OPEN", "deadline").doesNotContain(payload.getDescription(), actor.userId());
        });
        assertThat(windowRows.findById(windowId).orElseThrow().getState()).isEqualTo(org.ctc.domain.model.IncidentWindowState.OPEN);
        assertThat(windowRows.findById(windowId).orElseThrow().getVersion()).isEqualTo(1);
        assertThat(reportRows.findById(report).orElseThrow().getFrozenRevisionNumber()).isNull();
        assertThat(revisionRows.findByReportIdOrderByRevisionNumberAsc(report)).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FAILED", "UNCERTAIN"})
    void givenAnUnresolvedReceipt_whenAuditing_thenReportReviewWithoutChangingDeliveryEvidence(String outcome) {
        UUID report = submit();
        UUID id = tx.execute(status -> {
            var row = deliveries.findByOperationKey(report + ":RECEIPT").orElseThrow();
            var token = UUID.randomUUID();
            row.claim(token, "test-audit-generation", NOW, NOW.plusSeconds(600));
            row.fail(token, 1, "test-audit-generation", NOW, "TEST_REJECTION", outcome.equals("UNCERTAIN"), NOW.plusSeconds(60));
            return row.getId();
        });
        assertThat(audit.audit().findings(Category.INCIDENT_REPORTS)).singleElement().satisfies(finding -> {
            assertThat(finding.subject()).contains(id.toString());
            assertThat(finding.evidence()).contains(outcome).doesNotContain(payload.getDescription(), actor.userId());
        });
        tx.executeWithoutResult(status -> {
            var row = deliveries.findById(id).orElseThrow();
            assertThat(row.getState().name()).isEqualTo(outcome);
            assertThat(row.getVersion()).isEqualTo(2);
            assertThat(row.getAttempts()).isEqualTo(1);
            assertThat(row.getMessageId()).isNull();
        });
    }

    @Test
    void givenAPartialThreadPair_whenAuditing_thenLocateItsRetainedStepWithoutPublishingOrRepairing() {
        UUID report = submit(); clock.set(NOW.plusSeconds(3600)); windows.closeDue(windowId, null);
        UUID id = tx.execute(status -> {
            var row = deliveries.findByOperationKey(report + ":DISCLOSURE").orElseThrow();
            var mapping = org.ctc.domain.model.IncidentThreadPublication.capture(row, "600", "500", "", 1);
            var token = UUID.randomUUID();
            row.claim(token, "test-audit-generation", clock.instant(), clock.instant().plusSeconds(600));
            mapping.accept(org.ctc.domain.model.IncidentThreadStage.PRIVATE_STARTER, 0, "701", null);
            row.continueDisclosure(token, 1, "test-audit-generation", clock.instant());
            return publications.saveAndFlush(mapping).getId();
        });
        assertThat(audit.audit().findings(Category.INCIDENT_REPORTS)).singleElement().satisfies(finding -> {
            assertThat(finding.subject()).contains(id.toString());
            assertThat(finding.evidence()).contains("PARTIAL").doesNotContain(payload.getDescription(), actor.userId());
        });
        tx.executeWithoutResult(status -> {
            var mapping = publications.findById(id).orElseThrow();
            assertThat(mapping.getPrivateStarterId()).isEqualTo("701");
            assertThat(mapping.getPrivateThreadId()).isNull();
            assertThat(mapping.getPublicStarterId()).isNull();
            assertThat(mapping.getDelivery().getState()).isEqualTo(org.ctc.domain.model.IncidentDeliveryState.PENDING);
        });
    }

    @Test
    void givenAnAmbiguousSharedRole_whenAuditing_thenReportItButAcceptOneCommonOrganization() {
        submit();
        tx.executeWithoutResult(status -> {
            teams.findById(fixture.homeTeam().getId()).orElseThrow().setDiscordRoleId("300");
            teams.findById(fixture.awayTeam().getId()).orElseThrow().setDiscordRoleId("300");
        });
        assertThat(audit.audit().findings(Category.INCIDENT_REPORTS)).singleElement().satisfies(finding -> {
            assertThat(finding.subject()).contains(contextId.toString());
            assertThat(finding.evidence()).contains("role").doesNotContain(payload.getDescription(), actor.userId());
        });
        try {
            tx.executeWithoutResult(status -> {
                var home = teams.findById(fixture.homeTeam().getId()).orElseThrow();
                var away = teams.findById(fixture.awayTeam().getId()).orElseThrow();
                away.setParentTeam(home); away.setDiscordRoleId(null);
            });
            assertThat(audit.audit().findings(Category.INCIDENT_REPORTS)).isEmpty();
        } finally {
            tx.executeWithoutResult(status -> teams.findById(fixture.awayTeam().getId()).orElseThrow().setParentTeam(null));
        }
    }

    private UUID submit() {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        return reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload).reportId();
    }
}
