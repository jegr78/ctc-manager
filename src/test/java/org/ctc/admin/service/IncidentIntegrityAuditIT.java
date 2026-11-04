package org.ctc.admin.service;

import java.util.UUID;
import org.ctc.admin.dto.DataAuditFinding.Category;
import org.ctc.domain.service.IncidentIntegrationFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class IncidentIntegrityAuditIT extends IncidentIntegrationFixture {
    @Autowired DataAuditService audit;
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

    private UUID submit() {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        return reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload).reportId();
    }
}
