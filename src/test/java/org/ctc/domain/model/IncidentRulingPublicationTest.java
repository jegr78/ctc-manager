package org.ctc.domain.model;

import java.time.Instant;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentManagementActor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class IncidentRulingPublicationTest {
    final Instant now = Instant.parse("2035-01-02T20:00:00Z");
    final UUID reportId = UUID.randomUUID(), token = UUID.randomUUID();
    final IncidentManagementActor manager = new IncidentManagementActor("test-internal-steward");
    final IncidentReport report = mock(IncidentReport.class);
    final IncidentReportWindow window = mock(IncidentReportWindow.class);
    final IncidentThreadPublication thread = mock(IncidentThreadPublication.class);
    IncidentRuling root;
    IncidentRulingRevision revision;
    final String content = org.ctc.domain.service.IncidentRulingProjection.render(reportId, "INC-CASE-TEST", "Test saved ruling");

    @BeforeEach void fixture() {
        when(report.getCaseReference()).thenReturn("INC-CASE-TEST");
        when(report.getId()).thenReturn(reportId); when(report.getWindow()).thenReturn(window);
        when(report.getState()).thenReturn(IncidentReportState.ACTIVE); when(report.getFrozenRevisionNumber()).thenReturn(1);
        when(window.getState()).thenReturn(IncidentWindowState.CLOSED);
        when(window.getClosedAt()).thenReturn(now); when(window.getDeadline()).thenReturn(now);
        var delivery = mock(IncidentDelivery.class); when(delivery.getReport()).thenReturn(report); when(delivery.getGuildId()).thenReturn("100");
        when(thread.getDelivery()).thenReturn(delivery); when(thread.getPublicThreadId()).thenReturn("800"); when(thread.publicComplete()).thenReturn(true);
        when(thread.getId()).thenReturn(UUID.randomUUID());
        root = IncidentRuling.forReport(report); ReflectionTestUtils.setField(root, "id", UUID.randomUUID());
        revision = root.draft(0, "Test saved ruling", "", manager, now); ReflectionTestUtils.setField(revision, "id", UUID.randomUUID());
        root.requestPublication(1, 1);
    }
    private IncidentRulingPublication captured() {
        return assertDoesNotThrow(() -> IncidentRulingPublication.capture(revision, thread, content, null, manager, now));
    }

    @ParameterizedTest @ValueSource(strings = {"missing-report", "missing-id"})
    void aRulingRequiresItsPermanentReportBeforeAnAggregateIsCreated(String cause) {
        if (cause.equals("missing-id")) when(report.getId()).thenReturn(null);
        assertThatThrownBy(() -> IncidentRuling.forReport(cause.equals("missing-report") ? null : report)).isInstanceOf(BusinessRuleException.class);
    }

    @Test void aClockBeforeTheRecordedClosureCannotApprovePublicRulingWork() {
        when(window.getClosedAt()).thenReturn(now.plusSeconds(1)); when(window.getDeadline()).thenReturn(now.plusSeconds(1));
        assertThatThrownBy(() -> IncidentRulingPublication.capture(revision, thread, content, null, manager, now)).isInstanceOf(BusinessRuleException.class);
    }

    @Test void duplicateOrInvalidRulingApprovalsCannotAdvanceTheAggregate() {
        assertThatThrownBy(() -> root.requestPublication(2, 1)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> root.requestPublication(2, 0)).isInstanceOf(BusinessRuleException.class);
        assertThat(root.getVersion()).isEqualTo(2);
    }
    @Test void confirmationRequiresTheActualPreviouslyConfirmedVersion() {
        assertThatThrownBy(() -> root.confirmPublication(1, 99, "900")).isInstanceOf(BusinessRuleException.class);
        assertThat(root.getPublishedRevisionNumber()).isNull();
    }
    @Test void aConfirmedRulingRequiresAnInternalReasonBeforeAnotherVersionCanBeSaved() {
        root.confirmPublication(1, null, "900");
        assertThatThrownBy(() -> root.draft(3, "Test correction", "", manager, now)).isInstanceOf(BusinessRuleException.class);
        assertThat(root.getLatestRevisionNumber()).isEqualTo(1);
    }
    @Test void draftAttributionMustIdentifyAManager() {
        assertThatThrownBy(() -> root.draft(2, "Test next draft", "", new IncidentManagementActor(""), now)).isInstanceOf(BusinessRuleException.class);
        assertThat(root.getLatestRevisionNumber()).isEqualTo(1);
    }
    @Test void exhaustedDraftCountersCannotWrapOrCreateARevision() {
        ReflectionTestUtils.setField(root, "version", Long.MAX_VALUE); ReflectionTestUtils.setField(root, "latestRevisionNumber", Integer.MAX_VALUE);
        assertThatThrownBy(() -> root.draft(Long.MAX_VALUE, "Test next draft", "", manager, now)).isInstanceOf(BusinessRuleException.class);
        assertThat(root.getVersion()).isEqualTo(Long.MAX_VALUE); assertThat(root.getLatestRevisionNumber()).isEqualTo(Integer.MAX_VALUE);
    }
    @Test void publicationRequiresTrustedAttributionAndAPersistedRuling() {
        assertThatThrownBy(() -> IncidentRulingPublication.capture(revision, thread, content, null, new IncidentManagementActor(""), now)).isInstanceOf(BusinessRuleException.class);
        ReflectionTestUtils.setField(root, "id", null);
        assertThatThrownBy(() -> IncidentRulingPublication.capture(revision, thread, content, null, manager, now)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void pausedContextsCannotAcceptPublicationApprovalOrClaims() {
        var row = captured(); when(window.isDeliveryPaused()).thenReturn(true);
        assertThatThrownBy(() -> IncidentRulingPublication.capture(revision, thread, content, null, manager, now)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> row.claim(token, "runtime:0", now, now.plusSeconds(60))).isInstanceOf(BusinessRuleException.class);
        assertThat(row.getState()).isEqualTo(IncidentDeliveryState.PENDING);
    }
    @Test void aCorrectionMustCaptureItsPreviouslyConfirmedContent() {
        root.confirmPublication(1, null, "900");
        var next = root.draft(3, "Test correction", "Test reviewed correction", manager, now);
        ReflectionTestUtils.setField(next, "id", UUID.randomUUID()); root.requestPublication(4, 2);
        String nextContent = org.ctc.domain.service.IncidentRulingProjection.render(reportId, "INC-CASE-TEST", next.getText());
        assertThatThrownBy(() -> IncidentRulingPublication.capture(next, thread, nextContent, null, manager, now)).isInstanceOf(BusinessRuleException.class);
    }
    @Test void staleOrAnonymousReviewCannotGrantRetryAuthority() {
        var row = captured();
        assertThatThrownBy(() -> row.requireReview(1, now)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> row.reconcileMissing(0, "", "Test review", now)).isInstanceOf(BusinessRuleException.class);
        assertThat(row.isRestoreReviewed()).isFalse();
    }
    @Test void claimExpiryRequiresTheActualLeaseBoundaryAndKeepsTheOutcomeUnknown() {
        var row = captured(); assertThat(row.expireClaim(now)).isFalse();
        row.claim(token, "runtime:0", now, now.plusSeconds(60)); assertThat(row.expireClaim(now.plusSeconds(59))).isFalse();
        assertThat(row.getState()).isEqualTo(IncidentDeliveryState.CLAIMED);
        assertThat(row.expireClaim(now.plusSeconds(60))).isTrue(); assertThat(row.getState()).isEqualTo(IncidentDeliveryState.UNCERTAIN);
    }
    @Test void invalidFailureMetadataCannotBecomeARecordedRemoteOutcome() {
        var row = captured(); row.claim(token, "runtime:0", now, now.plusSeconds(60));
        assertThatThrownBy(() -> row.fail(token, 1, "runtime:0", now, "invalid code", false, now.plusSeconds(90))).isInstanceOf(BusinessRuleException.class);
        assertThat(row.getState()).isEqualTo(IncidentDeliveryState.CLAIMED);
    }

    @Test void approvedSnapshotKeepsItsOwnTextAndStableNonceAcrossRuntimeRecapture() {
        var row = captured(); var second = captured();
        assertThat(row.getContent()).isEqualTo(content); assertThat(row.getState()).isEqualTo(IncidentDeliveryState.PENDING);
        assertThat(row.getNonce()).isEqualTo(second.getNonce()).matches("[A-Za-z0-9_-]{25}");
        root.draft(2, "Test later unapproved draft", "", manager, now.plusSeconds(1));
        assertThat(row.getContent()).isEqualTo(content); assertThat(row.getMessageId()).isNull();
    }

    @Test void validClaimConfirmsExactlyOneMessageWithoutImplicitlyChangingTheAggregate() {
        var row = captured(); row.claim(token, "runtime:0", now, now.plusSeconds(60));
        assertThat(row.getState()).isEqualTo(IncidentDeliveryState.CLAIMED); assertThat(row.getAttempts()).isEqualTo(1);
        row.acknowledge(token, 1, "runtime:0", now.plusSeconds(1), "900");
        assertThat(row.getState()).isEqualTo(IncidentDeliveryState.SENT); assertThat(row.getMessageId()).isEqualTo("900");
        assertThat(root.getPublishedRevisionNumber()).isNull();
    }

    @Test void uncertainCorrectionRetainsItsPriorConfirmedRulingAndKnownMessage() {
        root.confirmPublication(1, null, "900");
        var correction = root.draft(3, "Test correction", "Test reviewed correction reason", manager, now.plusSeconds(2));
        ReflectionTestUtils.setField(correction, "id", UUID.randomUUID()); root.requestPublication(4, 2);
        var row = assertDoesNotThrow(() -> IncidentRulingPublication.capture(correction, thread, org.ctc.domain.service.IncidentRulingProjection.render(reportId, "INC-CASE-TEST", "Test correction"), content, manager, now.plusSeconds(2)));
        row.claim(token, "runtime:0", now.plusSeconds(2), now.plusSeconds(60));
        row.fail(token, 1, "runtime:0", now.plusSeconds(3), "SEND_UNCERTAIN", true, now.plusSeconds(90));
        assertThat(row.getState()).isEqualTo(IncidentDeliveryState.UNCERTAIN);
        assertThatThrownBy(() -> row.supersede(now.plusSeconds(100))).isInstanceOf(BusinessRuleException.class);
        assertThat(row.getPreviousContent()).isEqualTo(content); assertThat(row.getPreviousMessageId()).isEqualTo("900");
        assertThat(root.getPublishedRevisionNumber()).isEqualTo(1); assertThat(root.getPublishedMessageId()).isEqualTo("900");
        assertThatThrownBy(() -> row.claim(UUID.randomUUID(), "runtime:0", now.plusSeconds(100), now.plusSeconds(160)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"token", "version", "generation", "expired", "paused", "message"})
    void staleOrInvalidAcknowledgementCannotConfirmARuling(String cause) {
        var row = captured(); row.claim(token, "runtime:0", now, now.plusSeconds(60));
        if (cause.equals("paused")) when(window.isDeliveryPaused()).thenReturn(true);
        assertThatThrownBy(() -> row.acknowledge(cause.equals("token") ? UUID.randomUUID() : token,
                cause.equals("version") ? 0 : 1, cause.equals("generation") ? "old-runtime" : "runtime:0",
                cause.equals("expired") ? now.plusSeconds(60) : now.plusSeconds(1), cause.equals("message") ? "wrong" : "900"))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(row.getState()).isEqualTo(IncidentDeliveryState.CLAIMED); assertThat(row.getMessageId()).isNull();
    }
}
