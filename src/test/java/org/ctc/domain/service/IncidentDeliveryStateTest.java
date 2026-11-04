package org.ctc.domain.service;

import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class IncidentDeliveryStateTest {
    final IncidentUnitFixture f = new IncidentUnitFixture();
    final UUID token = UUID.randomUUID();
    final String generation = "test-runtime:0";
    final IncidentDelivery delivery;
    IncidentDeliveryStateTest() {
        f.openWindow(); var report = IncidentUnitFixture.id(new IncidentReport(f.window, f.race, "400", f.party())); report.appendRevision();
        var revision = IncidentUnitFixture.id(new IncidentReportRevision(report, 1, 1, new IncidentPayload("Test PRIVATE guest", "00:00", "Test PRIVATE description", ""), "400", IncidentUnitFixture.NOW, UUID.randomUUID()));
        delivery = IncidentUnitFixture.id(IncidentDelivery.capture(report, revision, IncidentDeliveryOperation.RECEIPT, IncidentUnitFixture.NOW));
    }
    void claim() { assertDoesNotThrow(() -> delivery.claim(token, generation, IncidentUnitFixture.NOW, IncidentUnitFixture.NOW.plusSeconds(120))); }

    @Test
    void givenDuePendingReceipt_whenClaimingAndAcknowledging_thenRetainOneConfirmedRemoteOutcome() {
        claim(); assertThat(delivery.getState()).isEqualTo(IncidentDeliveryState.CLAIMED); assertThat(delivery.getAttempts()).isEqualTo(1); assertThat(delivery.getVersion()).isEqualTo(1);
        assertDoesNotThrow(() -> delivery.acknowledge(token, 1, generation, IncidentUnitFixture.NOW.plusSeconds(1), "500000000000000005"));
        assertThat(delivery.getState()).isEqualTo(IncidentDeliveryState.SENT); assertThat(delivery.getMessageId()).isEqualTo("500000000000000005"); assertThat(delivery.getVersion()).isEqualTo(2);
        assertThatThrownBy(() -> delivery.claim(UUID.randomUUID(), generation, IncidentUnitFixture.NOW.plusSeconds(2), IncidentUnitFixture.NOW.plusSeconds(122))).isInstanceOf(BusinessRuleException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"future", "restore", "sent", "claimed", "uncertain", "token", "generation", "lease"})
    void givenUnavailableWork_whenClaiming_thenDenyBeforeStartingAnotherAttempt(String cause) {
        if (cause.equals("future")) ReflectionTestUtils.setField(delivery,"nextAttemptAt",IncidentUnitFixture.NOW.plusSeconds(1));
        if (cause.equals("restore")) ReflectionTestUtils.setField(f.window,"deliveryPaused",true);
        if (java.util.Set.of("sent","claimed","uncertain").contains(cause)) ReflectionTestUtils.setField(delivery,"state",IncidentDeliveryState.valueOf(cause.toUpperCase(java.util.Locale.ROOT)));
        assertThatThrownBy(() -> delivery.claim(cause.equals("token")?null:token,cause.equals("generation")?"":generation,IncidentUnitFixture.NOW,cause.equals("lease")?IncidentUnitFixture.NOW:IncidentUnitFixture.NOW.plusSeconds(120))).isInstanceOf(BusinessRuleException.class);
        assertThat(delivery.getAttempts()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"token", "generation", "version", "expired", "restore", "message"})
    void givenLostOrExpiredClaim_whenAcknowledging_thenDoNotFabricateDeliverySuccess(String cause) {
        claim(); if(cause.equals("restore")) ReflectionTestUtils.setField(f.window,"deliveryPaused",true);
        assertThatThrownBy(() -> delivery.acknowledge(cause.equals("token")?UUID.randomUUID():token,cause.equals("version")?0:1,cause.equals("generation")?"other-runtime":generation,IncidentUnitFixture.NOW.plusSeconds(cause.equals("expired")?120:1),cause.equals("message")?"untrusted":"500000000000000005")).isInstanceOf(BusinessRuleException.class);
        assertThat(delivery.getState()).isEqualTo(IncidentDeliveryState.CLAIMED); assertThat(delivery.getMessageId()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false,true})
    void givenRejectedOrUnknownSend_whenRecordingOutcome_thenRetryOnlyTheProvenRejectedAttempt(boolean uncertain) {
        claim(); assertDoesNotThrow(() -> delivery.fail(token,1,generation,IncidentUnitFixture.NOW.plusSeconds(1),"DISCORD_SEND",uncertain,IncidentUnitFixture.NOW.plusSeconds(61)));
        assertThat(delivery.getState()).isEqualTo(uncertain?IncidentDeliveryState.UNCERTAIN:IncidentDeliveryState.FAILED); assertThat(delivery.getMessageId()).isNull();
        assertThatThrownBy(() -> delivery.claim(UUID.randomUUID(),generation,IncidentUnitFixture.NOW.plusSeconds(60),IncidentUnitFixture.NOW.plusSeconds(180))).isInstanceOf(BusinessRuleException.class);
        if(uncertain) assertThatThrownBy(() -> delivery.claim(UUID.randomUUID(),generation,IncidentUnitFixture.NOW.plusSeconds(61),IncidentUnitFixture.NOW.plusSeconds(181))).isInstanceOf(BusinessRuleException.class);
        else { assertDoesNotThrow(() -> delivery.claim(UUID.randomUUID(),generation,IncidentUnitFixture.NOW.plusSeconds(61),IncidentUnitFixture.NOW.plusSeconds(181))); assertThat(delivery.getAttempts()).isEqualTo(2); }
    }

    @Test
    void givenAbandonedClaim_whenItsLeaseExpires_thenQuarantineInsteadOfBlindlyCreatingAnotherMessage() {
        claim(); boolean early = assertDoesNotThrow(() -> delivery.expireClaim(IncidentUnitFixture.NOW.plusSeconds(119))); assertThat(early).isFalse();
        boolean expired = assertDoesNotThrow(() -> delivery.expireClaim(IncidentUnitFixture.NOW.plusSeconds(120))); assertThat(expired).isTrue(); assertThat(delivery.getState()).isEqualTo(IncidentDeliveryState.UNCERTAIN); assertThat(delivery.getFailureCode()).isEqualTo("CLAIM_EXPIRED");
        assertThatThrownBy(() -> delivery.claim(UUID.randomUUID(),generation,IncidentUnitFixture.NOW.plusSeconds(121),IncidentUnitFixture.NOW.plusSeconds(241))).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenRestoredClaim_whenRecovering_thenKeepItsStatePausedForReconciliation() {
        claim(); ReflectionTestUtils.setField(f.window,"deliveryPaused",true);
        boolean changed = assertDoesNotThrow(() -> delivery.expireClaim(IncidentUnitFixture.NOW.plusSeconds(120)));
        assertThat(changed).isFalse(); assertThat(delivery.getState()).isEqualTo(IncidentDeliveryState.CLAIMED);
    }

    @Test
    void givenPendingGenericNoticeAfterDetailsWereDisclosed_whenSuperseding_thenKeepTheOriginalOperationButPreventALateSignal() {
        boolean changed = assertDoesNotThrow(() -> delivery.supersede(IncidentUnitFixture.NOW.plusSeconds(3600)));
        assertThat(changed).isTrue(); assertThat(delivery.getState()).isEqualTo(IncidentDeliveryState.SUPERSEDED); assertThat(delivery.getRevision().getRevisionNumber()).isEqualTo(1);
    }

    @Test
    void givenHistoricalSentReceipt_whenSuperseding_thenKeepItsConfirmedMessageAndHistory() {
        claim(); assertDoesNotThrow(() -> delivery.acknowledge(token,1,generation,IncidentUnitFixture.NOW.plusSeconds(1),"500"));
        boolean changed = assertDoesNotThrow(() -> delivery.supersede(IncidentUnitFixture.NOW.plusSeconds(3600)));
        assertThat(changed).isFalse(); assertThat(delivery.getState()).isEqualTo(IncidentDeliveryState.SENT); assertThat(delivery.getMessageId()).isEqualTo("500");
    }
}
