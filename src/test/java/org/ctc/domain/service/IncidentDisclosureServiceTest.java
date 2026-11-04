package org.ctc.domain.service;

import java.util.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentDisclosureServiceTest {
    final IncidentUnitFixture f = new IncidentUnitFixture();
    final IncidentDeliveryRepository deliveries = mock(IncidentDeliveryRepository.class);
    final IncidentReportRevisionRepository revisions = mock(IncidentReportRevisionRepository.class);
    final IncidentDisclosureService disclosure = new IncidentDisclosureService(deliveries, revisions, mock(IncidentWindowService.class));
    final IncidentReport report = IncidentUnitFixture.id(new IncidentReport(f.window, f.race, "400000000000000004", f.party()));
    final IncidentPayload payload = new IncidentPayload("Test PRIVATE guest", "00:00", "Test PRIVATE final description", "");
    final IncidentReportRevision revision;
    IncidentDisclosureServiceTest() {
        f.openWindow(); report.appendRevision();
        revision = IncidentUnitFixture.id(new IncidentReportRevision(report, 1, 1, payload, report.getAuthorId(), IncidentUnitFixture.NOW, UUID.randomUUID()));
        when(revisions.findByReportIdAndRevisionNumber(report.getId(), 1)).thenReturn(Optional.of(revision));
        when(deliveries.save(any())).thenAnswer(call -> IncidentUnitFixture.id(call.getArgument(0)));
    }

    @Test
    void givenFinalActiveAndWithdrawnReports_whenCapturingAClosedWindow_thenQueueOnlyImmutableActiveRevisions() {
        var withdrawn = IncidentUnitFixture.id(new IncidentReport(f.window, f.race, report.getAuthorId(), f.party())); withdrawn.appendRevision(); withdrawn.withdraw();
        report.freeze(); f.window.close(IncidentUnitFixture.NOW.plusSeconds(3600));
        var queued = org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> disclosure.captureClosedWindow(f.window, List.of(report, withdrawn)));
        assertThat(queued).hasSize(1); var item = queued.getFirst();
        assertThat(item.getOperation()).isEqualTo(IncidentDeliveryOperation.DISCLOSURE); assertThat(item.getRevision()).isSameAs(revision);
        assertThat(item.getCapturedAt()).isEqualTo(f.window.getClosedAt()); assertThat(item.getPartyLabel()).isEqualTo(f.party().getLabel());
        report.appendRevision(); org.springframework.test.util.ReflectionTestUtils.setField(report, "party", new IncidentParty(IncidentPartyType.TEAM, f.away.getId(), "Test changed attribution", f.away.getDiscordRoleId()));
        assertThat(item.getRevision().getPayload().getDescription()).isEqualTo("Test PRIVATE final description"); assertThat(item.getPartyLabel()).isEqualTo(f.home.getName());
        when(deliveries.findByOperationKey(item.getOperationKey())).thenReturn(Optional.of(item));
        assertThat(disclosure.captureClosedWindow(f.window, List.of(report, withdrawn))).containsExactly(item); verify(deliveries, times(1)).save(any());
    }

    @Test
    void givenOpenOrUnfrozenWindow_whenCapturing_thenRejectBeforeEnqueue() {
        assertThatThrownBy(() -> disclosure.captureClosedWindow(f.window, List.of(report))).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
        f.window.close(IncidentUnitFixture.NOW.plusSeconds(3600));
        assertThatThrownBy(() -> disclosure.captureClosedWindow(f.window, List.of(report))).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
        verify(deliveries, never()).save(any());
    }
}
