package org.ctc.domain.service;

import java.util.concurrent.*;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
class IncidentThreadFailureIT extends IncidentThreadDispatchFixture {
    @Autowired JdbcTemplate jdbc;
    @Test void givenPrivateSuccessThenRemotePublicSuccessButSqlFailure_whenRecovering_thenPreservePriorIdsAndNeverBlindlyRepost() throws Exception {
        var id=disclosure();assertThat(publication.dispatch(id)).isTrue();assertThat(publication.dispatch(id)).isTrue();clearInvocations(threadSender);
        doAnswer(call -> {jdbc.execute("ALTER TABLE incident_thread_publications ADD CONSTRAINT ctc_test_threads_ack CHECK (public_starter_id IS NULL)");return new IncidentThreadResult(IncidentSendResult.Disposition.SENT,"801",null,null);}).when(threadSender).send(any());
        try {assertThatThrownBy(() -> publication.dispatch(id)).isInstanceOf(RuntimeException.class);assertThat(state(id)).isEqualTo(IncidentDeliveryState.CLAIMED);tx.executeWithoutResult(status -> {var mapping=threadRows.findByDeliveryId(id).orElseThrow();assertThat(mapping.getPrivateThreadId()).isEqualTo("701");assertThat(mapping.getPublicStarterId()).isNull();});}
        finally {jdbc.execute("ALTER TABLE incident_thread_publications DROP CONSTRAINT ctc_test_threads_ack");}
        assertThat(imports.tryLock()).isTrue();try {imports.awaitWritersDrained();}finally{imports.unlock();}
        clock.set(clock.instant().plusSeconds(601));assertThat(publication.dispatch(id)).isFalse();assertThat(state(id)).isEqualTo(IncidentDeliveryState.UNCERTAIN);verify(threadSender,times(1)).send(any());
    }
    @Test void givenAnAdmittedClaimDuringHttp_whenAnotherWorkerAndRestoreArrive_thenKeepOneAttemptAndHoldAdmissionThroughCommit() throws Exception {
        var id=disclosure();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(call -> {entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();return new IncidentThreadResult(IncidentSendResult.Disposition.SENT,"701",null,null);}).when(threadSender).send(any());
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            var first=workers.submit(() -> publication.dispatch(id));
            try {
                assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();assertThat(workers.submit(() -> publication.dispatch(id)).get(5,TimeUnit.SECONDS)).isFalse();
                assertThat(imports.tryLock()).isTrue();try {var drain=workers.submit(() -> {imports.awaitWritersDrained();return true;});assertThatThrownBy(() -> drain.get(150,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);release.countDown();assertThat(first.get(10,TimeUnit.SECONDS)).isTrue();assertThat(drain.get(10,TimeUnit.SECONDS)).isTrue();assertThat(stage(id)).isEqualTo(IncidentThreadStage.PRIVATE_THREAD);}finally{imports.unlock();}
            } finally {release.countDown();}
        }
        verify(threadSender,times(1)).send(any());
    }
    @Test void givenSenderFailure_whenDispatching_thenPersistUncertaintyAndReleaseAdmission() throws Exception {
        var id=disclosure();doThrow(new IllegalStateException("Test private transport failure")).when(threadSender).send(any());assertThat(publication.dispatch(id)).isTrue();assertThat(state(id)).isEqualTo(IncidentDeliveryState.UNCERTAIN);
        assertThat(imports.tryLock()).isTrue();try {imports.awaitWritersDrained();}finally{imports.unlock();}assertThat(publication.dispatch(id)).isFalse();
    }
    @Test void givenRestoreChangesTheGenerationAfterTheClaim_whenAResponseReturns_thenRefuseStaleCompletionAndKeepPausedState() {
        var id=disclosure();doAnswer(call -> {epoch.afterRestore(null);tx.executeWithoutResult(status -> ReflectionTestUtils.setField(windowRows.findById(windowId).orElseThrow(),"deliveryPaused",true));return new IncidentThreadResult(IncidentSendResult.Disposition.SENT,"701",null,null);}).when(threadSender).send(any());
        assertThatThrownBy(() -> publication.dispatch(id)).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);assertThat(state(id)).isEqualTo(IncidentDeliveryState.CLAIMED);assertThat(publication.dispatch(id)).isFalse();verify(threadSender,times(1)).send(any());
    }
    @Test void givenAnExpiredClaimBeforeTheRemoteResponse_whenAcknowledging_thenKeepTheStepUncertainWithoutStoringUnprovenIds() {
        var id=disclosure();doAnswer(call -> {clock.set(clock.instant().plusSeconds(601));return new IncidentThreadResult(IncidentSendResult.Disposition.SENT,"701",null,null);}).when(threadSender).send(any());
        assertThatThrownBy(() -> publication.dispatch(id)).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);assertThat(state(id)).isEqualTo(IncidentDeliveryState.CLAIMED);assertThat(publication.dispatch(id)).isFalse();assertThat(state(id)).isEqualTo(IncidentDeliveryState.UNCERTAIN);
        tx.executeWithoutResult(status -> assertThat(threadRows.findByDeliveryId(id).orElseThrow().getPrivateStarterId()).isNull());verify(threadSender,times(1)).send(any());
    }
}
