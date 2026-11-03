package org.ctc.domain.service;

import java.util.*;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.IncidentDeliveryRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@Import(IncidentDeliveryPersistenceIT.FaultConfiguration.class)
class IncidentDeliveryPersistenceIT extends IncidentIntegrationFixture {
    @Autowired IncidentDeliveryRepository deliveries;
    @Autowired IncidentDisclosureService disclosure;
    @Autowired Faults faults;
    @Autowired JdbcTemplate jdbc;

    @Test
    void givenAcceptedReport_whenEditingWithdrawingAndReplaying_thenCommitExactlyOneFirstSubmissionReceipt() {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager); UUID request = UUID.randomUUID();
        var saved = reports.submit(windowId, fixture.race().getId(), 1, request, actor, payload);
        reports.edit(saved.reportId(), 1, 1, UUID.randomUUID(), actor, new IncidentPayload("Test correction", "Lap 2", "Test PRIVATE correction", ""));
        reports.withdraw(saved.reportId(), 1, 2, UUID.randomUUID(), actor);
        assertThat(reports.submit(windowId, fixture.race().getId(), 0, request, actor, null)).isEqualTo(saved);
        tx.executeWithoutResult(status -> {
            var queued = deliveries.findByReportWindowId(windowId); assertThat(queued).hasSize(1);
            assertThat(queued.getFirst().getOperation()).isEqualTo(IncidentDeliveryOperation.RECEIPT);
            assertThat(queued.getFirst().getRevision().getRevisionNumber()).isEqualTo(1);
            assertThat(queued.getFirst().getState()).isEqualTo(IncidentDeliveryState.PENDING);
        });
    }

    @Test
    void givenFailureBeforeSubmissionCommit_whenEnqueueing_thenRollBackReportRevisionEventAndDeliveryTogether() {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager); faults.submit = true;
        try { assertThatThrownBy(() -> reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload)).isInstanceOf(BusinessRuleException.class); }
        finally { faults.submit = false; }
        tx.executeWithoutResult(status -> { assertThat(reportRows.findByWindowIdOrderByCaseReferenceAsc(windowId)).isEmpty(); assertThat(deliveries.findByReportWindowId(windowId)).isEmpty(); });
    }

    @Test
    void givenChangedDeadlineAndFinalRevisions_whenClosing_thenAtomicallyFreezeOnlyActiveConfirmedCases() {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        var active = reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload);
        reports.edit(active.reportId(), 1, 1, UUID.randomUUID(), actor, new IncidentPayload("Test guest", "Lap 2", "Test PRIVATE final revision", ""));
        var withdrawn = reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload);
        reports.withdraw(withdrawn.reportId(), 1, 1, UUID.randomUUID(), actor);
        windows.changeDeadline(contextId, NOW.plusSeconds(7200), 1, "Test reviewed extension", manager);
        clock.set(NOW.plusSeconds(3600)); assertThat(disclosure.freezeDueWindow(windowId)).isFalse();
        clock.set(NOW.plusSeconds(7200)); faults.close = true;
        try { assertThatThrownBy(() -> disclosure.freezeDueWindow(windowId)).isInstanceOf(BusinessRuleException.class); }
        finally { faults.close = false; }
        tx.executeWithoutResult(status -> { assertThat(windowRows.findById(windowId).orElseThrow().getState()).isEqualTo(IncidentWindowState.OPEN); assertThat(reportRows.findById(active.reportId()).orElseThrow().getFrozenRevisionNumber()).isNull(); assertThat(deliveries.findByReportWindowId(windowId)).hasSize(2); });
        assertThat(disclosure.freezeDueWindow(windowId)).isTrue(); assertThat(disclosure.freezeDueWindow(windowId)).isFalse();
        tx.executeWithoutResult(status -> {
            var queued = deliveries.findByReportWindowId(windowId); assertThat(queued).hasSize(3);
            var detailed = queued.stream().filter(row -> row.getOperation()==IncidentDeliveryOperation.DISCLOSURE).toList(); assertThat(detailed).hasSize(1);
            var frozen = detailed.getFirst(); assertThat(frozen.getReport().getId()).isEqualTo(active.reportId());
            assertThat(frozen.getRevision().getRevisionNumber()).isEqualTo(2); assertThat(frozen.getRevision().getPayload().getDescription()).isEqualTo("Test PRIVATE final revision");
            assertThat(frozen.getCapturedAt()).isEqualTo(NOW.plusSeconds(7200));
        });
    }

    @Test
    void givenForeignPrivateRevision_whenChangingADeliveryDirectly_thenTheDatabaseRejectsCrossReportOwnership() {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        var one = reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload);
        var two = reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload);
        UUID foreign = tx.execute(status -> revisionRows.findByReportIdAndRevisionNumber(two.reportId(), 1).orElseThrow().getId());
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> jdbc.update("UPDATE incident_deliveries SET revision_id=? WHERE report_id=?", foreign, one.reportId())))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FaultConfiguration { @Bean Faults incidentDeliveryTestFaults() { return new Faults(); } }
    static class Faults {
        boolean submit, close;
        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        public void submitted(IncidentSubmissionAccepted event) { if (submit) throw new BusinessRuleException("Test receipt transaction failure"); }
        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        public void closed(IncidentWindowClosed event) { if (close) throw new BusinessRuleException("Test disclosure transaction failure"); }
    }
}
