package org.ctc.domain.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentMatchRef;
import org.ctc.domain.model.IncidentEventKind;
import org.ctc.domain.model.IncidentPayload;
import org.ctc.domain.model.IncidentReportEvent;
import org.ctc.domain.model.IncidentReportState;
import org.ctc.domain.model.IncidentWindowState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.assertj.core.api.Assertions.*;

@Tag("integration")
class IncidentPersistenceIT extends IncidentIntegrationFixture {
    @Test
    void givenCommittedReport_whenEditingWithdrawingAndRecoveringAfterCutoff_thenKeepExactHistoryAndOriginalReceipt() {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        UUID request = UUID.randomUUID(); var original = reports.submit(windowId, fixture.race().getId(), 1, request, actor, payload);
        var correction = new IncidentPayload("Test corrected guest", "Lap 2, 01:00", "Test corrected facts", "");
        var edited = reports.edit(original.reportId(), 1, 1, UUID.randomUUID(), actor, correction);
        var another = reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload);
        reports.withdraw(original.reportId(), 1, 2, UUID.randomUUID(), actor);
        clock.set(NOW.plusSeconds(3600));
        assertThat(reports.submit(windowId, fixture.race().getId(), 0, request, actor, null)).isEqualTo(original);
        assertThat(edited.reportId()).isEqualTo(original.reportId()); assertThat(another.reportId()).isNotEqualTo(original.reportId());
        tx.executeWithoutResult(status -> {
            var saved = reportRows.findById(original.reportId()).orElseThrow();
            assertThat(saved.getCaseReference()).isEqualTo(original.caseReference());
            assertThat(saved.getState()).isEqualTo(IncidentReportState.WITHDRAWN); assertThat(saved.getVersion()).isEqualTo(3);
            assertThat(saved.getParty()).isEqualTo(actor.party()); assertThat(saved.getFrozenRevisionNumber()).isNull();
            var revisions = revisionRows.findByReportIdOrderByRevisionNumberAsc(original.reportId());
            assertThat(revisions).hasSize(2); assertThat(revisions.getFirst().getPayload()).isEqualTo(payload);
            assertThat(revisions.getLast().getPayload()).isEqualTo(correction);
            assertThat(eventRows.findByRequestId(request).orElseThrow().receipt()).isEqualTo(original);
            assertThat(reportRows.findByWindowIdOrderByCaseReferenceAsc(windowId)).hasSize(2);
        });
    }

    @Test
    void givenExpiredWindow_whenClosing_thenUseServerTimeAndFreezeOnlyTheActiveLatestRevision() {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        var active = reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload);
        var withdrawn = reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload);
        reports.withdraw(withdrawn.reportId(), 1, 1, UUID.randomUUID(), actor);
        assertThat(windows.closeDue(windowId, NOW.plusSeconds(7200))).isFalse();
        clock.set(NOW.plusSeconds(3600)); assertThat(windows.closeDue(windowId, NOW)).isTrue();
        assertThat(windows.closeDue(windowId, NOW.plusSeconds(7200))).isFalse();
        tx.executeWithoutResult(status -> {
            assertThat(windowRows.findById(windowId).orElseThrow().getState()).isEqualTo(IncidentWindowState.CLOSED);
            assertThat(reportRows.findById(active.reportId()).orElseThrow().getFrozenRevisionNumber()).isEqualTo(1);
            assertThat(reportRows.findById(withdrawn.reportId()).orElseThrow().getFrozenRevisionNumber()).isNull();
            assertThat(eventRows.findByWindowIdOrderByAcceptedAtAscIdAsc(windowId)).filteredOn(row -> row.getReport() == null)
                    .extracting(IncidentReportEvent::getKind).containsExactly(IncidentEventKind.OPENED, IncidentEventKind.CLOSED);
        });
        assertThatThrownBy(() -> reports.edit(active.reportId(), 2, 1, UUID.randomUUID(), actor, payload))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("closed");
    }

    @Test
    void givenFutureDatesBeyondTimestampRange_whenOpeningAndChanging_thenPersistDeadlineAndOriginalPlannedStart() {
        tx.executeWithoutResult(status -> races.findById(fixture.race().getId()).orElseThrow().setDateTime(LocalDateTime.of(2060, 11, 3, 20, 0)));
        var preview = windows.previewDeadline(contextId); Instant chosen = Instant.parse("2060-11-04T19:00:00Z");
        assertThat(preview.defaultDeadline()).isEqualTo(chosen);
        windows.open(contextId, chosen, 0, manager); windows.changeDeadline(contextId, chosen.plusSeconds(7200), 1, "Test explicit adjustment", manager);
        tx.executeWithoutResult(status -> races.findById(fixture.race().getId()).orElseThrow().setDateTime(LocalDateTime.of(2061, 11, 3, 20, 0)));
        assertThat(windows.previewDeadline(contextId).plannedStart()).isEqualTo(preview.plannedStart());
        tx.executeWithoutResult(status -> {
            var saved = windowRows.findById(windowId).orElseThrow();
            assertThat(saved.getDeadline()).isEqualTo(chosen.plusSeconds(7200)); assertThat(saved.getVersion()).isEqualTo(2);
            assertThat(eventRows.findByWindowIdOrderByAcceptedAtAscIdAsc(windowId)).hasSize(2);
        });
    }

    @Test
    void givenUnrelatedLegacyMatchSharingChannel_whenBinding_thenRefuseBeforeCreatingAnotherContext() {
        var other = tx.execute(status -> {
            var data = helper.createFullSeasonFixture("TICother" + UUID.randomUUID().toString().substring(0, 5));
            data.match().setDiscordChannelId(CHANNEL); data.race().setDateTime(LocalDateTime.of(2035, 1, 1, 20, 0));
            extras.add(data); return data;
        });
        long count = contextRows.count();
        assertThatThrownBy(() -> contexts.bind(IncidentMatchRef.match(other.match().getId()), GUILD, CHANNEL, manager))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ambiguous");
        assertThat(contextRows.count()).isEqualTo(count); assertThat(reportRows.findByWindowIdOrderByCaseReferenceAsc(windowId)).isEmpty();
    }
}
