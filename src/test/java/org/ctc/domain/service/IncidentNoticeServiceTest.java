package org.ctc.domain.service;

import java.util.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentNoticeServiceTest {
    final IncidentUnitFixture f = new IncidentUnitFixture();
    final IncidentDeliveryRepository deliveries = mock(IncidentDeliveryRepository.class);
    final IncidentNoticeService notices = new IncidentNoticeService(deliveries);
    final IncidentReport report = IncidentUnitFixture.id(new IncidentReport(f.window, f.race, "400000000000000004", f.party()));
    final IncidentPayload payload = new IncidentPayload("Test PRIVATE DRIVER", "PRIVATE REPLAY", "PRIVATE DESCRIPTION", "https://example.invalid/PRIVATE-FOOTAGE");
    final IncidentReportRevision revision;
    IncidentNoticeServiceTest() { f.openWindow(); report.appendRevision(); revision = IncidentUnitFixture.id(new IncidentReportRevision(report, 1, 1, payload, report.getAuthorId(), IncidentUnitFixture.NOW, UUID.randomUUID())); when(deliveries.save(any())).thenAnswer(call -> IncidentUnitFixture.id(call.getArgument(0))); }

    @Test
    void givenPrivateFirstSubmission_whenEnqueuing_thenCaptureOneNeutralOperationWithoutASecondSignalOnReplay() {
        var queued = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> notices.enqueueFirstSubmission(report, revision));
        assertThat(queued.getOperation()).isEqualTo(IncidentDeliveryOperation.RECEIPT);
        assertThat(queued.getOperationKey()).isEqualTo(report.getId() + ":RECEIPT");
        assertThat(queued.getRevision()).isSameAs(revision); assertThat(queued.getState()).isEqualTo(IncidentDeliveryState.PENDING);
        assertThat(queued.getGuildId()).isEqualTo(IncidentUnitFixture.GUILD); assertThat(queued.getChannelId()).isEqualTo(IncidentUnitFixture.CHANNEL);
        when(deliveries.findByOperationKey(queued.getOperationKey())).thenReturn(Optional.of(queued));
        assertThat(notices.enqueueFirstSubmission(report, revision)).isSameAs(queued); verify(deliveries, times(1)).save(any());
        assertThat(IncidentNoticeService.neutralText()).contains("report", "received").doesNotContain("PRIVATE", report.getAuthorId(), report.getCaseReference(), f.home.getName(), f.home.getDiscordRoleId());
        assertThat(queued.getNonce()).hasSizeLessThanOrEqualTo(25); assertThat(queued.toString()).doesNotContain("PRIVATE", report.getAuthorId());
    }

    @Test
    void givenEditedOrWithdrawnReport_whenConsideringAReceipt_thenKeepTheFirstRevisionAndItsOneOperation() {
        var first = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> notices.enqueueFirstSubmission(report, revision));
        when(deliveries.findByOperationKey(first.getOperationKey())).thenReturn(Optional.of(first));
        report.appendRevision(); report.withdraw();
        var replay = notices.enqueueFirstSubmission(report, revision);
        assertThat(replay).isSameAs(first); assertThat(replay.getRevision().getRevisionNumber()).isEqualTo(1); verify(deliveries, times(1)).save(any());
    }

    @Test
    void givenAnotherReportsRevision_whenEnqueuing_thenRejectInsteadOfCapturingForeignPrivateHistory() {
        var other = IncidentUnitFixture.id(new IncidentReport(f.window, f.race, "400000000000000005", f.party()));
        other.appendRevision();
        var ownRevision=IncidentUnitFixture.id(new IncidentReportRevision(other,1,1,payload,other.getAuthorId(),IncidentUnitFixture.NOW,UUID.randomUUID()));
        var existing=IncidentUnitFixture.id(IncidentDelivery.capture(other,ownRevision,IncidentDeliveryOperation.RECEIPT,IncidentUnitFixture.NOW));
        when(deliveries.findByOperationKey(other.getId()+":RECEIPT")).thenReturn(Optional.of(existing));
        assertThatThrownBy(() -> notices.enqueueFirstSubmission(other, revision)).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
        verify(deliveries, never()).save(any());
    }
}
