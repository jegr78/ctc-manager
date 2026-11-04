package org.ctc.domain.service;

import java.util.UUID;
import java.util.concurrent.*;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.IncidentDeliveryState;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
class IncidentWorkerRestoreAdmissionIT extends IncidentDeliveryDispatchFixture {
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Test
    void givenRemoteSuccessButSqlAcknowledgementFailure_whenRecovering_thenKeepAnUncertainClaimWithoutResendingAndReleaseAdmission() {
        UUID id=receipt();
        when(sender.send(any())).thenAnswer(call -> {
            jdbc.execute("ALTER TABLE incident_deliveries ADD CONSTRAINT ctc_test_incident_ack_failure CHECK (state <> 'SENT')");
            return new IncidentSendResult(IncidentSendResult.Disposition.SENT,"500",null);
        });
        try {assertThatThrownBy(() -> delivery.dispatch(id)).isInstanceOf(RuntimeException.class);assertThat(state(id)).isEqualTo(IncidentDeliveryState.CLAIMED);}
        finally {jdbc.execute("ALTER TABLE incident_deliveries DROP CONSTRAINT ctc_test_incident_ack_failure");}
        assertThat(imports.tryLock()).isTrue();try {try(var executor=Executors.newVirtualThreadPerTaskExecutor()){org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> executor.submit(() -> {imports.awaitWritersDrained();return true;}).get(5,TimeUnit.SECONDS));}}finally{imports.unlock();}
        clock.set(NOW.plusSeconds(601));assertThat(delivery.dispatch(id)).isFalse();assertThat(state(id)).isEqualTo(IncidentDeliveryState.UNCERTAIN);verify(sender,times(1)).send(any());
    }

    @Test
    void givenAdmittedHttpDispatch_whenRestoreDrainsWriters_thenWaitThroughTheOutcomeTransactionAndReleaseAdmission() throws Exception {
        UUID id=receipt();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(sender.send(any())).thenAnswer(call -> {entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();return new IncidentSendResult(IncidentSendResult.Disposition.SENT,"500",null);});
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            var dispatch=workers.submit(() -> delivery.dispatch(id));
            try {
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();assertThat(imports.tryLock()).isTrue();
                try {var drain=workers.submit(() -> {imports.awaitWritersDrained();return true;});assertThatThrownBy(() -> drain.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);release.countDown();assertThat(dispatch.get(10,TimeUnit.SECONDS)).isTrue();assertThat(drain.get(10,TimeUnit.SECONDS)).isTrue();assertThat(state(id)).isEqualTo(IncidentDeliveryState.SENT);}
                finally {imports.unlock();}
            } finally {release.countDown();}
        }
    }
    @Test
    void givenRestoreAlreadyHoldingAdmission_whenDispatching_thenDoNotClaimOrCallTheExternalSender() {
        UUID id=receipt();assertThat(imports.tryLock()).isTrue();try {assertThat(delivery.dispatch(id)).isFalse();assertThat(state(id)).isEqualTo(IncidentDeliveryState.PENDING);verifyNoInteractions(sender);} finally {imports.unlock();}
    }
    @Test
    void givenSenderFailure_whenDispatching_thenPersistUncertaintyAndReleaseWriterAdmissionOnTheErrorPath() {
        UUID id=receipt();when(sender.send(any())).thenThrow(new IllegalStateException("Test PRIVATE sender failure"));
        assertThat(delivery.dispatch(id)).isTrue();assertThat(state(id)).isEqualTo(IncidentDeliveryState.UNCERTAIN);assertThat(imports.tryLock()).isTrue();try {org.junit.jupiter.api.Assertions.assertDoesNotThrow(imports::awaitWritersDrained);} finally {imports.unlock();}
    }
    @Test
    void givenChangedRestoreGenerationAfterClaim_whenAResponseReturns_thenRefuseStaleCompletionWithoutChangingRestoredState() {
        UUID id=receipt();when(sender.send(any())).thenAnswer(call -> {epoch.afterRestore(null);tx.executeWithoutResult(status -> ReflectionTestUtils.setField(windowRows.findById(windowId).orElseThrow(),"deliveryPaused",true));return new IncidentSendResult(IncidentSendResult.Disposition.SENT,"500",null);});
        assertThatThrownBy(() -> delivery.dispatch(id)).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);assertThat(state(id)).isEqualTo(IncidentDeliveryState.CLAIMED);
    }
}
