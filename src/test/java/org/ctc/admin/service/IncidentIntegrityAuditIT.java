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

    private UUID submit() {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        return reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload).reportId();
    }
}
