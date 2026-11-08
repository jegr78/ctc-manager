package org.ctc.domain.service;

import java.util.UUID;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
class IncidentThreadDispatchIT extends IncidentThreadDispatchFixture {
    @org.springframework.beans.factory.annotation.Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test void givenNewDisclosure_whenCapturingSteps_thenKeepCanonicalNoncesAndUseReadableFootersOnBothSides() {
        var id = disclosure(); var sent = new java.util.ArrayList<IncidentThreadClaim.Work>();
        doAnswer(call -> {
            var work = call.getArgument(0, IncidentThreadClaim.class).work(); sent.add(work);
            return switch (work.stage()) {
                case PRIVATE_STARTER -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT, "701", null, null);
                case PRIVATE_THREAD -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT, "701", "701", null);
                case PUBLIC_STARTER -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT, "801", null, null);
                case PUBLIC_THREAD -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT, "801", "801", null);
                case PRIVATE_REFERENCE -> new IncidentThreadResult(IncidentSendResult.Disposition.SENT, "702", null, null);
                default -> throw new AssertionError(work.stage());
            };
        }).when(threadSender).send(any());
        for (int step = 0; step < 5; step++) assertThat(publication.dispatch(id)).isTrue();
        assertThat(sent).hasSize(5).allSatisfy(work -> {
            assertThat(work.content()).endsWith("\n-# ||" + work.marker() + "||").hasSizeLessThanOrEqualTo(2000);
            assertThat(work.title()).endsWith(" · " + IncidentDisplay.reference(work.caseReference())).hasSizeLessThanOrEqualTo(100);
            assertThat(work.marker()).contains(work.caseReference());
        });
        assertThat(sent.getLast().content()).startsWith("**Incident ").contains("https://discord.com/channels/");
        assertThat(state(id)).isEqualTo(IncidentDeliveryState.SENT);
    }

    @Test void givenMigratedDatabase_whenWritingPresentationMetadata_thenRejectUnknownVersionsAndReceiptUpgrades() {
        var id = disclosure();
        assertThat(jdbc.queryForObject("SELECT presentation_version FROM incident_deliveries WHERE id=?", Integer.class, id)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE incident_deliveries SET presentation_version=2 WHERE id=?", id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class).satisfies(failure -> assertThat(failure.getMessage()).containsIgnoringCase("chk_incident_presentation_version"));
        var receipt = tx.execute(status -> deliveryRows.findByReportWindowId(windowId).stream()
                .filter(row -> row.getOperation() == IncidentDeliveryOperation.RECEIPT).findFirst().orElseThrow().getId());
        assertThatThrownBy(() -> jdbc.update("UPDATE incident_deliveries SET presentation_version=1 WHERE id=?", receipt))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class).satisfies(failure -> assertThat(failure.getMessage()).containsIgnoringCase("chk_incident_presentation_version"));
        assertThatCode(() -> jdbc.update("UPDATE incident_deliveries SET presentation_version=NULL WHERE id=?", id)).doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT presentation_version FROM incident_deliveries WHERE id=?", Integer.class, id)).isNull();
    }
    @Test void givenAFrozenActiveReport_whenDispatchingItsPair_thenCommitEachStepBeforeSendingTheNextAndRetainBothReferences() {
        var id=disclosure();assertThat(publication.dueDisclosures(1)).containsExactly(id);
        var stages=new IncidentThreadStage[]{IncidentThreadStage.PRIVATE_THREAD,IncidentThreadStage.PUBLIC_STARTER,IncidentThreadStage.PUBLIC_THREAD,IncidentThreadStage.PRIVATE_REFERENCE,IncidentThreadStage.COMPLETE};
        for(var expected:stages){assertThat(publication.dispatch(id)).isTrue();assertThat(stage(id)).isEqualTo(expected);}
        assertThat(state(id)).isEqualTo(IncidentDeliveryState.SENT);assertThat(publication.dispatch(id)).isFalse();assertThat(publication.dueDisclosures(10)).doesNotContain(id);
        tx.executeWithoutResult(status -> {var mapping=threadRows.findByDeliveryId(id).orElseThrow();assertThat(mapping.getPrivateThreadId()).isEqualTo("701");assertThat(mapping.getPublicThreadId()).isEqualTo("801");assertThat(mapping.getReferenceMessageId()).isEqualTo("702");assertThat(mapping.getDelivery().getMessageId()).isEqualTo("801");});verify(threadSender,times(5)).send(any());
    }
    @Test void givenAWindowBeforeCutoffOrAReceiptOperation_whenDispatching_thenCreateNoMappingAndMakeNoExternalCall() {
        var receipt=receipt();assertThat(publication.dueDisclosures(10)).isEmpty();assertThat(publication.dispatch(receipt)).isFalse();assertThat(threadRows.findByDeliveryId(receipt)).isEmpty();verifyNoInteractions(threadSender);
    }
    @ParameterizedTest @EnumSource(value=IncidentSendResult.Disposition.class,names={"REJECTED","UNCERTAIN"})
    void givenPrivateSuccessAndPublicFailure_whenRestarting_thenRetainThePrivateThreadAndRetryOnlyAProvenRejection(IncidentSendResult.Disposition failure) {
        var id=disclosure();assertThat(publication.dispatch(id)).isTrue();assertThat(publication.dispatch(id)).isTrue();
        doReturn(new IncidentThreadResult(failure,null,null,"TEST_FAILURE")).when(threadSender).send(any());assertThat(publication.dispatch(id)).isTrue();assertThat(stage(id)).isEqualTo(IncidentThreadStage.PUBLIC_STARTER);
        assertThat(state(id)).isEqualTo(failure==IncidentSendResult.Disposition.REJECTED?IncidentDeliveryState.FAILED:IncidentDeliveryState.UNCERTAIN);
        clock.set(clock.instant().plusSeconds(61));assertThat(publication.dispatch(id)).isEqualTo(failure==IncidentSendResult.Disposition.REJECTED);
        tx.executeWithoutResult(status -> {var mapping=threadRows.findByDeliveryId(id).orElseThrow();assertThat(mapping.getPrivateThreadId()).isEqualTo("701");assertThat(mapping.getPublicThreadId()).isNull();});
    }
    @Test void givenAnotherWorkerOwnsTheClaim_whenDispatchingAgain_thenNeverMakeASecondExternalAttempt() {
        var id=disclosure();doAnswer(call -> {assertThat(publication.dispatch(id)).isFalse();return new IncidentThreadResult(IncidentSendResult.Disposition.SENT,"701",null,null);}).when(threadSender).send(any());
        assertThat(publication.dispatch(id)).isTrue();assertThat(stage(id)).isEqualTo(IncidentThreadStage.PRIVATE_THREAD);verify(threadSender,times(1)).send(any());
    }
    @Test void givenPublicDetailsCompleteAndPrivateReferenceStillMissing_whenDispatchingAnOldReceipt_thenSupersedeItWithoutAPost() {
        var id=disclosure();for(int step=0;step<4;step++)assertThat(publication.dispatch(id)).isTrue();
        var receipt=tx.execute(status -> deliveryRows.findByReportWindowId(windowId).stream().filter(row -> row.getOperation()==IncidentDeliveryOperation.RECEIPT).findFirst().orElseThrow().getId());
        clearInvocations(sender);assertThat(delivery.dispatch(receipt)).isFalse();assertThat(state(receipt)).isEqualTo(IncidentDeliveryState.SUPERSEDED);verifyNoInteractions(sender);
    }
    @Test void givenAnOutstandingGenericReceiptClaim_whenPublishing_thenWaitForItsOutcomeBeforeCreatingDetails() {
        var id=disclosure();tx.executeWithoutResult(status -> {var receipt=deliveryRows.findByReportWindowId(windowId).stream().filter(row -> row.getOperation()==IncidentDeliveryOperation.RECEIPT).findFirst().orElseThrow();receipt.claim(UUID.randomUUID(),epoch.managementGeneration(),clock.instant(),clock.instant().plusSeconds(120));});
        assertThat(publication.dispatch(id)).isFalse();verifyNoInteractions(threadSender);assertThat(state(id)).isEqualTo(IncidentDeliveryState.PENDING);
    }
    @Test void givenRestoredPausedWork_whenDispatching_thenKeepTheOriginalStateAndDoNotSend() {
        var id=disclosure();tx.executeWithoutResult(status -> ReflectionTestUtils.setField(windowRows.findById(windowId).orElseThrow(),"deliveryPaused",true));assertThat(publication.dispatch(id)).isFalse();assertThat(publication.dueDisclosures(10)).isEmpty();verifyNoInteractions(threadSender);
    }
    @Test void givenTheSameFrozenCaseWithARecreatedDeliveryRow_whenPlanningAgain_thenKeepItsPermanentStepNonceAndMarker() {
        var id=disclosure();var sent=new java.util.ArrayList<IncidentThreadClaim.Work>();
        doAnswer(call -> {sent.add(call.getArgument(0,IncidentThreadClaim.class).work());return new IncidentThreadResult(IncidentSendResult.Disposition.REJECTED,null,null,"TEST_NOT_POSTED");}).when(threadSender).send(any());
        assertThat(publication.dispatch(id)).isTrue();
        var replacement=tx.execute(status -> {
            var previous=deliveryRows.findById(id).orElseThrow();var report=previous.getReport();var revision=previous.getRevision();var at=previous.getCapturedAt();var pairingLabel=previous.getPairingLabel();var raceLabel=previous.getRaceLabel();
            threadRows.delete(threadRows.findByDeliveryId(id).orElseThrow());threadRows.flush();deliveryRows.delete(previous);deliveryRows.flush();
            var recreated=IncidentDelivery.capture(report,revision,IncidentDeliveryOperation.DISCLOSURE,at);
            if(previous.getPresentationVersion()!=null)recreated.freezeReadableIdentity(pairingLabel,raceLabel);
            else if(pairingLabel!=null)recreated.freezePublicIdentity(pairingLabel,raceLabel);
            return deliveryRows.saveAndFlush(recreated).getId();
        });
        assertThat(replacement).isNotEqualTo(id);assertThat(publication.dispatch(replacement)).isTrue();assertThat(sent).hasSize(2);
        assertThat(sent.get(1).nonce()).isEqualTo(sent.get(0).nonce());assertThat(sent.get(1).content()).isEqualTo(sent.get(0).content());
    }
}
