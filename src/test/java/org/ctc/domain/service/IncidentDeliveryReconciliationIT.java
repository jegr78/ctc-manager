package org.ctc.domain.service;

import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
class IncidentDeliveryReconciliationIT extends IncidentDeliveryDispatchFixture {
    UUID uncertain() {UUID id=receipt();tx.executeWithoutResult(status -> {var row=deliveryRows.findById(id).orElseThrow();var token=UUID.randomUUID();row.claim(token,epoch.managementGeneration(),NOW,NOW.plusSeconds(120));row.fail(token,1,epoch.managementGeneration(),NOW,"SEND_UNCERTAIN",true,NOW.plusSeconds(60));});return id;}
    void pause() {tx.executeWithoutResult(status -> ReflectionTestUtils.setField(windowRows.findById(windowId).orElseThrow(),"deliveryPaused",true));}

    @Test
    void givenVerifiedMissingRemoteReceipt_whenManagementReconciles_thenKeepItsOriginalIdentityAndMakeOnlyThatOperationRetryable() {
        UUID id=uncertain();assertDoesNotThrow(() -> delivery.reconcile(id,2,manager,"Test verified remote absence",true,"",epoch.managementGeneration()));
        assertThat(state(id)).isEqualTo(IncidentDeliveryState.PENDING);verifyNoInteractions(sender);
        tx.executeWithoutResult(status -> {var row=deliveryRows.findById(id).orElseThrow();assertThat(row.getReconciledBy()).isEqualTo(manager.id());assertThat(row.getReconciliationReason()).isEqualTo("Test verified remote absence");assertThat(row.getRevision().getRevisionNumber()).isEqualTo(1);});
    }
    @Test
    void givenVerifiedExistingBotReceipt_whenManagementReconciles_thenRecordItsMessageWithoutSendingAgain() {
        UUID id=uncertain();when(sender.verify(any(),eq("500"))).thenAnswer(call -> {assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();return new IncidentSendResult(IncidentSendResult.Disposition.SENT,"500",null);});
        assertDoesNotThrow(() -> delivery.reconcile(id,2,manager,"Test matched the existing receipt",true,"500",epoch.managementGeneration()));assertThat(state(id)).isEqualTo(IncidentDeliveryState.SENT);verify(sender,never()).send(any());
    }
    @ParameterizedTest
    @ValueSource(strings={"actor","review","reason","version","active","sent","unverified","foreign-message"})
    void givenUnreviewedStaleOrUnconfirmedResolution_whenManagementSubmits_thenPreserveTheOriginalDeliveryState(String cause) {
        UUID id=java.util.Set.of("active","sent").contains(cause)?receipt():uncertain();long version=2;
        if(cause.equals("active") || cause.equals("sent")) {tx.executeWithoutResult(status -> {var row=deliveryRows.findById(id).orElseThrow();var token=UUID.randomUUID();row.claim(token,epoch.managementGeneration(),NOW,NOW.plusSeconds(120));if(cause.equals("sent"))row.acknowledge(token,1,epoch.managementGeneration(),NOW,"500");});if(cause.equals("active"))version=1;}
        if(cause.equals("unverified"))when(sender.verify(any(),anyString())).thenReturn(new IncidentSendResult(IncidentSendResult.Disposition.REJECTED,null,"VERIFICATION_FAILED"));
        if(cause.equals("foreign-message"))when(sender.verify(any(),anyString())).thenReturn(new IncidentSendResult(IncidentSendResult.Disposition.SENT,"501",null));
        long expected=cause.equals("version")?0:version;var before=state(id);
        assertThatThrownBy(() -> delivery.reconcile(id,expected,cause.equals("actor")?null:manager,cause.equals("reason")?"":"Test reviewed resolution",!cause.equals("review"),java.util.Set.of("unverified","foreign-message").contains(cause)?"500":"",epoch.managementGeneration())).isInstanceOf(BusinessRuleException.class);
        assertThat(state(id)).isEqualTo(before);verify(sender,never()).send(any());
    }
    @Test
    void givenRestoredContext_whenResuming_thenRequireFreshReviewOfEveryUnresolvedOperationBeforeEnablingDispatch() {
        UUID id=uncertain();pause();assertThatThrownBy(() -> delivery.resumeWindow(windowId,1,manager,"Test reviewed restore",true,epoch.managementGeneration())).isInstanceOf(BusinessRuleException.class);
        assertDoesNotThrow(() -> delivery.reconcile(id,2,manager,"Test checked this restored operation",true,"",epoch.managementGeneration()));assertDoesNotThrow(() -> delivery.resumeWindow(windowId,1,manager,"Test reviewed complete restored context",true,epoch.managementGeneration()));
        assertThat(delivery.dispatch(id)).isTrue();assertThat(state(id)).isEqualTo(IncidentDeliveryState.SENT);
    }
    @Test
    void givenRestoredPendingWorkWithoutAFreshReview_whenResuming_thenKeepDeliveryPausedUntilItHasBeenReviewed() {
        UUID id=receipt();pause();
        assertThatThrownBy(() -> delivery.resumeWindow(windowId,1,manager,"Test whole-context review alone is insufficient",true,epoch.managementGeneration())).isInstanceOf(BusinessRuleException.class);
        assertDoesNotThrow(() -> delivery.reconcile(id,0,manager,"Test this pending receipt is verified missing",true,"",epoch.managementGeneration()));
        assertDoesNotThrow(() -> delivery.resumeWindow(windowId,1,manager,"Test all pending restored work reviewed",true,epoch.managementGeneration()));
    }
    @Test
    void givenRestoredPendingDisclosure_whenVerifyingANeutralReceipt_thenRejectBeforeReadingOrSendingAnyMessage() {
        UUID id=receipt();clock.set(NOW.plusSeconds(3600));disclosure.freezeDueWindow(windowId);pause();
        UUID detail=tx.execute(status -> deliveryRows.findByReportWindowId(windowId).stream().filter(row -> row.getOperation()==IncidentDeliveryOperation.DISCLOSURE).findFirst().orElseThrow().getId());
        assertThatThrownBy(() -> delivery.reconcile(detail,0,manager,"Test disclosure cannot reuse neutral message verification",true,"500",epoch.managementGeneration())).isInstanceOf(BusinessRuleException.class);
        assertThat(state(detail)).isEqualTo(IncidentDeliveryState.PENDING);verifyNoInteractions(sender);
    }
    @Test
    void givenAnotherResolutionDuringRemoteVerification_whenTheFirstResultReturns_thenRejectItsStaleVersion() {
        UUID id=uncertain();when(sender.verify(any(),eq("500"))).thenAnswer(call -> {delivery.reconcile(id,2,manager,"Test intervening missing receipt resolution",true,"",epoch.managementGeneration());return new IncidentSendResult(IncidentSendResult.Disposition.SENT,"500",null);});
        assertThatThrownBy(() -> delivery.reconcile(id,2,manager,"Test first remote verification",true,"500",epoch.managementGeneration())).isInstanceOf(BusinessRuleException.class);assertThat(state(id)).isEqualTo(IncidentDeliveryState.PENDING);
    }
    @ParameterizedTest
    @ValueSource(strings={"actor","review","reason","version"})
    void givenInvalidContextResumption_whenManagementSubmits_thenKeepDeliveryPaused(String cause) {
        UUID id=uncertain();pause();assertDoesNotThrow(() -> delivery.reconcile(id,2,manager,"Test checked restored pending work",true,"",epoch.managementGeneration()));
        assertThatThrownBy(() -> delivery.resumeWindow(windowId,cause.equals("version")?0:1,cause.equals("actor")?null:manager,cause.equals("reason")?"":"Test reviewed restore",!cause.equals("review"),epoch.managementGeneration())).isInstanceOf(BusinessRuleException.class);
        tx.executeWithoutResult(status -> assertThat(windowRows.findById(windowId).orElseThrow().isDeliveryPaused()).isTrue());
    }
    @ParameterizedTest
    @ValueSource(booleans={false,true})
    void givenAReviewFormFromBeforeRestore_whenSubmittingIt_thenRequireANewManagementReview(boolean resumption) {
        UUID id=uncertain();pause();String reviewedGeneration=epoch.managementGeneration();epoch.afterRestore(null);
        if(resumption)assertThatThrownBy(() -> delivery.resumeWindow(windowId,1,manager,"Test old restored review",true,reviewedGeneration)).isInstanceOf(BusinessRuleException.class);
        else assertThatThrownBy(() -> delivery.reconcile(id,2,manager,"Test old restored review",true,"",reviewedGeneration)).isInstanceOf(BusinessRuleException.class);
        assertThat(state(id)).isEqualTo(IncidentDeliveryState.UNCERTAIN);verifyNoInteractions(sender);
    }

}
