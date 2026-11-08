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
    final IncidentContextService contexts = mock(IncidentContextService.class);
    final IncidentDisclosureService disclosure = new IncidentDisclosureService(deliveries, revisions, mock(IncidentWindowService.class), contexts);
    final IncidentReport report = IncidentUnitFixture.id(new IncidentReport(f.window, f.race, "400000000000000004", f.party()));
    final IncidentPayload payload = new IncidentPayload("Test PRIVATE guest", "00:00", "Test PRIVATE final description", "");
    final IncidentReportRevision revision;
    IncidentDisclosureServiceTest() {
        when(contexts.resolve(any())).thenReturn(f.target());
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
    void newDisclosuresFreezeThePairingAndConcreteLegWithoutFollowingLaterNamesOrRaceOrder() {
        report.freeze(); f.window.close(IncidentUnitFixture.NOW.plusSeconds(3600));
        var queued = disclosure.captureClosedWindow(f.window, List.of(report)).getFirst();
        String body = String.join("", IncidentThreadProjection.capture(queued).messages());
        assertThat(body).contains("**Pairing:** TIH vs TIA", "**Race:** Leg 1 · Test Incident season · Test Incident round");
        assertThat(body).doesNotContain(f.race.getId().toString());
        assertThat(queued.getPresentationVersion()).isEqualTo(1);
        f.home.setShortName("T-CHANGED"); f.away.setShortName("T-OTHER"); f.day.setSortIndex(99); f.season.setName("Test changed season");
        var earlier = new Race(); earlier.setId(UUID.randomUUID()); earlier.setMatch(f.match); earlier.setMatchday(f.day);
        earlier.setDateTime(java.time.LocalDateTime.of(2030, 1, 1, 0, 0)); f.match.getRaces().addFirst(earlier);
        assertThat(String.join("", IncidentThreadProjection.capture(queued).messages())).isEqualTo(body).doesNotContain("T-CHANGED", "T-OTHER");
    }

    @Test void givenAnUnscheduledRaceWithoutTrack_whenFreezing_thenUseActualSeasonAndLegWithoutInventedFacts() {
        f.race.setDateTime(null); f.race.setTrack(null);
        report.freeze(); f.window.close(IncidentUnitFixture.NOW.plusSeconds(3600));
        var queued = disclosure.captureClosedWindow(f.window, List.of(report)).getFirst();
        assertThat(queued.getRaceLabel()).isEqualTo("Leg 1 · Test Incident season · Test Incident round");
        assertThat(queued.getPresentationVersion()).isEqualTo(1);
    }

    @Test
    void givenOpenOrUnfrozenWindow_whenCapturing_thenRejectBeforeEnqueue() {
        assertThatThrownBy(() -> disclosure.captureClosedWindow(f.window, List.of(report))).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
        f.window.close(IncidentUnitFixture.NOW.plusSeconds(3600));
        assertThatThrownBy(() -> disclosure.captureClosedWindow(f.window, List.of(report))).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
        verify(deliveries, never()).save(any());
    }

    @Test void givenPreviouslyCapturedLegacyDisclosure_whenClosingAgain_thenKeepItsExactEnvelopeWithoutUpgrade() {
        report.freeze(); f.window.close(IncidentUnitFixture.NOW.plusSeconds(3600));
        var legacy = IncidentUnitFixture.id(IncidentDelivery.capture(report, revision, IncidentDeliveryOperation.DISCLOSURE, f.window.getClosedAt()));
        var original = IncidentThreadProjection.capture(legacy);
        when(deliveries.findByOperationKey(legacy.getOperationKey())).thenReturn(Optional.of(legacy));
        var captured = disclosure.captureClosedWindow(f.window, List.of(report));
        assertThat(captured).containsExactly(legacy);
        assertThat(legacy.getPresentationVersion()).isNull();
        assertThat(IncidentThreadProjection.capture(legacy)).isEqualTo(original);
        verify(deliveries, never()).save(any());
        verifyNoInteractions(contexts);
    }
}
