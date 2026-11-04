package org.ctc.domain.service;

import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("integration")
@AutoConfigureMockMvc
class IncidentThreadRecoveryIT extends IncidentThreadDispatchFixture {
    @Autowired MockMvc mvc;
    @BeforeEach void remoteProof() {
        when(threadSender.verify(any(),anyString())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var claim=call.getArgument(0,IncidentThreadClaim.class);
            return switch(claim.work().stage()) {
                case PRIVATE_STARTER -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT,"701",null,null);
                case PRIVATE_THREAD -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT,"701","701",null);
                case PUBLIC_STARTER -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT,"801",null,null);
                case PUBLIC_THREAD -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT,"801","801",null);
                default -> throw new AssertionError(claim.work().stage());
            };
        });
    }
    long version(UUID id) {return tx.execute(status -> deliveryRows.findById(id).orElseThrow().getVersion());}
    UUID unknownPublic() {
        var id=disclosure();assertThat(publication.dispatch(id)).isTrue();assertThat(publication.dispatch(id)).isTrue();
        doReturn(new IncidentThreadResult(IncidentSendResult.Disposition.UNCERTAIN,null,null,"TEST_UNKNOWN")).when(threadSender).send(any());assertThat(publication.dispatch(id)).isTrue();return id;
    }
    @Test void givenAVerifiedExistingPublicStarter_whenAdopting_thenRetainPrivateSuccessAndAdvanceOnlyThatStepWithoutPosting() {
        var id=unknownPublic();clearInvocations(threadSender);
        assertDoesNotThrow(() -> publication.reconcile(id,version(id),manager,"Test matched permanent case marker",true,"801",epoch.managementGeneration()));
        assertThat(state(id)).isEqualTo(IncidentDeliveryState.PENDING);assertThat(stage(id)).isEqualTo(IncidentThreadStage.PUBLIC_THREAD);
        tx.executeWithoutResult(status -> {var mapping=threadRows.findByDeliveryId(id).orElseThrow();assertThat(mapping.getPrivateThreadId()).isEqualTo("701");assertThat(mapping.getPublicStarterId()).isEqualTo("801");assertThat(mapping.getDelivery().getReconciledBy()).isEqualTo(manager.id());});verify(threadSender,never()).send(any());
    }
    @Test void givenAProvenMissingPublicStep_whenReviewing_thenMakeOnlyThatStepRetryable() {
        var id=unknownPublic();doReturn(new IncidentThreadResult(IncidentSendResult.Disposition.REJECTED,null,null,"PROVEN_MISSING")).when(threadSender).verify(any(),eq(""));
        assertDoesNotThrow(() -> publication.reconcile(id,version(id),manager,"Test complete target search found no starter",true,"",epoch.managementGeneration()));assertThat(stage(id)).isEqualTo(IncidentThreadStage.PUBLIC_STARTER);assertThat(state(id)).isEqualTo(IncidentDeliveryState.PENDING);
        tx.executeWithoutResult(status -> assertThat(threadRows.findByDeliveryId(id).orElseThrow().getPrivateThreadId()).isEqualTo("701"));
    }
    @ParameterizedTest @ValueSource(strings={"actor","review","reason","version","generation","remote","prior"})
    void givenUntrustedStaleOrUnverifiedReview_whenReconciling_thenKeepTheUnknownOutcomeAndItsPreviousIds(String cause) {
        var id=unknownPublic();long version=version(id);
        if(cause.equals("remote"))doReturn(new IncidentThreadResult(IncidentSendResult.Disposition.REJECTED,null,null,"VERIFICATION_FAILED")).when(threadSender).verify(any(),eq("801"));
        if(cause.equals("prior"))doAnswer(call -> {var claim=call.getArgument(0,IncidentThreadClaim.class);return new IncidentThreadResult(IncidentSendResult.Disposition.SENT,claim.work().stage()==IncidentThreadStage.PRIVATE_STARTER?"799":"801",null,null);}).when(threadSender).verify(any(),anyString());
        assertThatThrownBy(() -> publication.reconcile(id,cause.equals("version")?0:version,cause.equals("actor")?null:manager,cause.equals("reason")?"":"Test current review",!cause.equals("review"),"801",cause.equals("generation")?"old-runtime":epoch.managementGeneration())).isInstanceOf(BusinessRuleException.class);
        assertThat(state(id)).isEqualTo(IncidentDeliveryState.UNCERTAIN);assertThat(stage(id)).isEqualTo(IncidentThreadStage.PUBLIC_STARTER);assertThat(version(id)).isEqualTo(version);
    }
    @Test void givenADisclosureWithPartialRemoteSuccess_whenUsingTheGenericReceiptReview_thenRejectTheBypass() {
        var id=unknownPublic();assertThatThrownBy(() -> delivery.reconcile(id,version(id),manager,"Test receipt recovery cannot reset thread steps",true,"",epoch.managementGeneration())).isInstanceOf(BusinessRuleException.class);assertThat(state(id)).isEqualTo(IncidentDeliveryState.UNCERTAIN);
    }
    @Test void givenPartialThreadSuccess_whenOpeningNativeManagement_thenExposeBothTheCurrentStepAndVerifiedAdoptionControl() throws Exception {
        var id=unknownPublic();mvc.perform(get("/admin/incident-deliveries/"+id)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Existing message or thread ID"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Public starter")));
        mvc.perform(get("/admin/incident-contexts/"+contextId+"/deliveries")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("https://discord.com/channels/"+GUILD+"/701")));
    }
    @Test @WithMockUser("test-thread-manager")
    void givenVerifiedThreadRecovery_whenPostingForgedActorAndState_thenUseTheTrustedPrincipalAndCapturedStep() throws Exception {
        var id=unknownPublic();mvc.perform(post("/admin/incident-deliveries/"+id).param("expectedVersion",Long.toString(version(id))).param("generation",epoch.managementGeneration()).param("reviewed","true").param("reason","Test exact bot case marker verified").param("decision","CONFIRM_SENT").param("messageId","801").param("actor","forged-thread-reviewer").param("state","SENT"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/incident-contexts/"+contextId+"/deliveries"));
        assertThat(stage(id)).isEqualTo(IncidentThreadStage.PUBLIC_THREAD);assertThat(state(id)).isEqualTo(IncidentDeliveryState.PENDING);tx.executeWithoutResult(status -> assertThat(deliveryRows.findById(id).orElseThrow().getReconciledBy()).isEqualTo("test-thread-manager"));
    }
    @Test void givenRestoredPartialWork_whenResuming_thenRequireFreshRemoteReviewOfPreviousSuccessAndTheUnresolvedStep() {
        var id=unknownPublic();tx.executeWithoutResult(status -> ReflectionTestUtils.setField(windowRows.findById(windowId).orElseThrow(),"deliveryPaused",true));
        long windowVersion=tx.execute(status -> windowRows.findById(windowId).orElseThrow().getVersion());
        assertThatThrownBy(() -> delivery.resumeWindow(windowId,windowVersion,manager,"Test whole-context confirmation is insufficient",true,epoch.managementGeneration())).isInstanceOf(BusinessRuleException.class);
        var receipt=tx.execute(status -> deliveryRows.findByReportWindowId(windowId).stream().filter(row -> row.getOperation()==IncidentDeliveryOperation.RECEIPT).findFirst().orElseThrow().getId());
        delivery.reconcile(receipt,version(receipt),manager,"Test reviewed the separate restored receipt",true,"",epoch.managementGeneration());
        assertDoesNotThrow(() -> publication.reconcile(id,version(id),manager,"Test checked every existing thread step",true,"801",epoch.managementGeneration()));
        assertDoesNotThrow(() -> publication.reconcile(id,version(id),manager,"Test verified the existing match thread",true,"801",epoch.managementGeneration()));
        doReturn(new IncidentThreadResult(IncidentSendResult.Disposition.REJECTED,null,null,"PROVEN_MISSING")).when(threadSender).verify(any(),eq(""));
        assertDoesNotThrow(() -> publication.reconcile(id,version(id),manager,"Test private reference is verified missing",true,"",epoch.managementGeneration()));
        assertDoesNotThrow(() -> delivery.resumeWindow(windowId,windowVersion,manager,"Test fresh complete remote review",true,epoch.managementGeneration()));
        tx.executeWithoutResult(status -> assertThat(windowRows.findById(windowId).orElseThrow().isDeliveryPaused()).isFalse());
    }
    @Test void givenOneReviewedMissingRestoredStep_whenItSucceeds_thenBlockTheNextStepUntilItsOwnFreshRemoteReview() {
        var id=disclosure();tx.executeWithoutResult(status -> ReflectionTestUtils.setField(windowRows.findById(windowId).orElseThrow(),"deliveryPaused",true));
        doReturn(new IncidentThreadResult(IncidentSendResult.Disposition.REJECTED,null,null,"PROVEN_MISSING")).when(threadSender).verify(any(),eq(""));
        assertDoesNotThrow(() -> publication.reconcile(id,version(id),manager,"Test reviewed initial restored private starter",true,"",epoch.managementGeneration()));
        var receipt=tx.execute(status -> deliveryRows.findByReportWindowId(windowId).stream().filter(row -> row.getOperation()==IncidentDeliveryOperation.RECEIPT).findFirst().orElseThrow().getId());
        delivery.reconcile(receipt,version(receipt),manager,"Test reviewed the separate receipt",true,"",epoch.managementGeneration());
        long windowVersion=tx.execute(status -> windowRows.findById(windowId).orElseThrow().getVersion());delivery.resumeWindow(windowId,windowVersion,manager,"Test reviewed this first missing step",true,epoch.managementGeneration());
        assertThat(publication.dispatch(id)).isTrue();assertThat(stage(id)).isEqualTo(IncidentThreadStage.PRIVATE_THREAD);
        assertThat(publication.dispatch(id)).isFalse();assertThat(publication.dueDisclosures(10)).doesNotContain(id);
    }
}
