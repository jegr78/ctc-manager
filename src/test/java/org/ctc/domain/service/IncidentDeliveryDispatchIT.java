package org.ctc.domain.service;

import java.util.*;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
class IncidentDeliveryDispatchIT extends IncidentDeliveryDispatchFixture {
    @Test
    void givenSeveralDueReceipts_whenScanningWithABoundedBatch_thenReturnOnlyTheRequestedNumberAndRejectInvalidLimits() {
        receipt();receipt();receipt();assertThat(delivery.dueReceipts(1)).hasSize(1);
        for(int limit:new int[]{0,26}){assertThatThrownBy(() -> delivery.dueReceipts(limit)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(() -> delivery.dueWindows(limit)).isInstanceOf(IllegalArgumentException.class);}
    }


    @Test
    void givenCommittedReport_whenDispatching_thenCommitTheClaimBeforeHttpAndAcknowledgeItsExactRemoteResultOnce() {
        UUID id=receipt();when(sender.send(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var claimed=call.getArgument(0,IncidentDeliveryClaim.class);assertThat(claimed.toString()).doesNotContain("private",actor.userId());
            assertThat(state(id)).isEqualTo(IncidentDeliveryState.CLAIMED);return new IncidentSendResult(IncidentSendResult.Disposition.SENT,"500",null);
        });
        assertThat(delivery.dispatch(id)).isTrue();assertThat(state(id)).isEqualTo(IncidentDeliveryState.SENT);assertThat(delivery.dispatch(id)).isFalse();verify(sender,times(1)).send(any());
        tx.executeWithoutResult(status -> {var row=deliveryRows.findById(id).orElseThrow();assertThat(row.getMessageId()).isEqualTo("500");assertThat(row.getAttempts()).isEqualTo(1);assertThat(row.getReport().getLatestRevisionNumber()).isEqualTo(1);});
    }

    @ParameterizedTest
    @EnumSource(value=IncidentSendResult.Disposition.class,names={"REJECTED","UNCERTAIN"})
    void givenFailedPublicDelivery_whenRecordingOutcome_thenRetainTheReportAndRetryOnlyKnownRejection(IncidentSendResult.Disposition outcome) {
        UUID id=receipt();when(sender.send(any())).thenReturn(new IncidentSendResult(outcome,null,"DISCORD_SEND"));
        assertThat(delivery.dispatch(id)).isTrue();assertThat(state(id)).isEqualTo(outcome==IncidentSendResult.Disposition.REJECTED?IncidentDeliveryState.FAILED:IncidentDeliveryState.UNCERTAIN);
        clock.set(NOW.plusSeconds(61));when(sender.send(any())).thenReturn(new IncidentSendResult(IncidentSendResult.Disposition.SENT,"500",null));
        assertThat(delivery.dispatch(id)).isEqualTo(outcome==IncidentSendResult.Disposition.REJECTED);
        tx.executeWithoutResult(status -> {var row=deliveryRows.findById(id).orElseThrow();assertThat(row.getReport().getState()).isEqualTo(IncidentReportState.ACTIVE);assertThat(row.getAttempts()).isEqualTo(outcome==IncidentSendResult.Disposition.REJECTED?2:1);assertThat(deliveryRows.findByReportWindowId(windowId)).hasSize(1);});
    }

    @Test
    void givenDueWindowAndSeparateDisclosureOperations_whenScanning_thenRecoverClosuresButSendOnlyNeutralReceipts() {
        UUID first=receipt(),second=receipt();assertThat(delivery.dueWindows(10)).isEmpty();clock.set(NOW.plusSeconds(3600));assertThat(delivery.dueWindows(10)).contains(windowId);
        assertThat(disclosure.freezeDueWindow(windowId)).isTrue();assertThat(delivery.dueReceipts(10)).containsExactlyInAnyOrder(first,second);
        UUID detailed=tx.execute(status -> deliveryRows.findByReportWindowId(windowId).stream().filter(row -> row.getOperation()==IncidentDeliveryOperation.DISCLOSURE).findFirst().orElseThrow().getId());
        assertThat(delivery.dispatch(detailed)).isFalse();verifyNoInteractions(sender);
    }

    @Test
    void givenDetailsAlreadyConfirmed_whenDispatchingAnUnsentGenericNotice_thenSupersedeWithoutCreatingALateSignal() {
        UUID id=receipt();clock.set(NOW.plusSeconds(3600));disclosure.freezeDueWindow(windowId);
        tx.executeWithoutResult(status -> {var detail=deliveryRows.findByReportWindowId(windowId).stream().filter(row -> row.getOperation()==IncidentDeliveryOperation.DISCLOSURE).findFirst().orElseThrow();var token=UUID.randomUUID();detail.claim(token,epoch.managementGeneration(),clock.instant(),clock.instant().plusSeconds(120));detail.acknowledge(token,1,epoch.managementGeneration(),clock.instant(),"501");});
        assertThat(delivery.dispatch(id)).isFalse();assertThat(state(id)).isEqualTo(IncidentDeliveryState.SUPERSEDED);verifyNoInteractions(sender);
    }

    @Test
    void givenExpiredClaimFromAnotherProcess_whenRecovering_thenQuarantineItsUnknownOutcomeWithoutResending() {
        UUID id=receipt();tx.executeWithoutResult(status -> deliveryRows.findById(id).orElseThrow().claim(UUID.randomUUID(),"previous-runtime:0",NOW,NOW.plusSeconds(120)));
        clock.set(NOW.plusSeconds(120));assertThat(delivery.dueReceipts(10)).contains(id);assertThat(delivery.dispatch(id)).isFalse();assertThat(state(id)).isEqualTo(IncidentDeliveryState.UNCERTAIN);verifyNoInteractions(sender);
    }

    @Test
    void givenPendingWorkAndANewServiceInstance_whenRecoveringAfterRestart_thenUsePersistedWorkInsteadOfAnInMemoryQueue() {
        UUID id=receipt();var restarted=new IncidentDeliveryService(deliveryRows,windows,imports,transactionManager,em,clock,new PickSessionEpoch(),sender,rulingPublications);
        assertThat(restarted.dueReceipts(10)).contains(id);assertThat(restarted.dispatch(id)).isTrue();assertThat(state(id)).isEqualTo(IncidentDeliveryState.SENT);
    }

    @Test
    void givenRestoredReportingContext_whenDiscoveringOrDispatching_thenKeepItsOriginalQueueStatePaused() {
        UUID id=receipt();tx.executeWithoutResult(status -> ReflectionTestUtils.setField(windowRows.findById(windowId).orElseThrow(),"deliveryPaused",true));
        assertThat(delivery.dueReceipts(10)).doesNotContain(id);assertThat(delivery.dispatch(id)).isFalse();assertThat(state(id)).isEqualTo(IncidentDeliveryState.PENDING);verifyNoInteractions(sender);
    }
}
