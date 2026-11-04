package org.ctc.domain.service;

import java.util.UUID;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Tag("integration")
class IncidentRulingDeliveryIT extends IncidentIntegrationFixture {
    @Autowired IncidentRulingService rulings;
    @Autowired IncidentDeliveryService deliveryService;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired IncidentRulingRepository rulingRows;
    @Autowired IncidentRulingPublicationRepository publications;
    @Autowired IncidentDeliveryRepository deliveries;
    @Autowired IncidentThreadPublicationRepository threads;
    @Autowired PickSessionEpoch epoch;
    @Autowired ImportLockService imports;
    @MockitoBean IncidentRulingSender sender;

    record Work(UUID report, UUID publication) { }
    Work queued() {
        windows.open(contextId, NOW.plusSeconds(3600), 0, manager);
        UUID report = reports.submit(windowId, fixture.race().getId(), 1, UUID.randomUUID(), actor, payload).reportId();
        rulings.saveDraft(report, 0, "Test explicitly approved ruling", "", manager, epoch.managementGeneration());
        clock.set(NOW.plusSeconds(3600)); windows.closeDue(windowId, null);
        tx.executeWithoutResult(status -> {
            var detail = deliveries.findByOperationKey(report + ":DISCLOSURE").orElseThrow();
            var mapping = threads.save(IncidentThreadPublication.capture(detail, "600", "500", "", 1));
            mapping.accept(IncidentThreadStage.PRIVATE_STARTER, 0, "701", "701");
            mapping.accept(IncidentThreadStage.PUBLIC_STARTER, 0, "801", null);
            mapping.accept(IncidentThreadStage.PUBLIC_THREAD, 0, "801", "801");
            mapping.accept(IncidentThreadStage.PRIVATE_REFERENCE, 0, "901", null);
            var token = UUID.randomUUID(); detail.claim(token, epoch.managementGeneration(), clock.instant(), clock.instant().plusSeconds(600));
            detail.acknowledge(token, 1, epoch.managementGeneration(), clock.instant(), "801");
            deliveries.findByOperationKey(report + ":RECEIPT").orElseThrow().supersede(clock.instant());
        });
        return new Work(report, rulings.publish(report, 1, 1, manager, epoch.managementGeneration()));
    }
    IncidentDeliveryState state(UUID id) { return tx.execute(status -> publications.findById(id).orElseThrow().getState()); }

    @Test void committedClaimPrecedesHttpAndOnlyItsAcknowledgedResultChangesTheConfirmedRuling() {
        var work = queued();
        when(sender.send(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            var claim = call.getArgument(0, IncidentRulingClaim.class);
            assertThat(state(work.publication())).isEqualTo(IncidentDeliveryState.CLAIMED);
            assertThat(claim.content()).contains("Test explicitly approved ruling").doesNotContain(manager.id());
            assertThat(claim.thread().work().threadId()).isEqualTo("801");
            return new IncidentSendResult(IncidentSendResult.Disposition.SENT, "950", null);
        });
        assertThat(rulings.dispatch(work.publication())).isTrue(); assertThat(state(work.publication())).isEqualTo(IncidentDeliveryState.SENT);
        assertThat(rulings.dispatch(work.publication())).isFalse(); verify(sender, times(1)).send(any());
        tx.executeWithoutResult(status -> {
            var root = rulingRows.findByReportId(work.report()).orElseThrow();
            assertThat(root.getPublishedRevisionNumber()).isEqualTo(1); assertThat(root.getPublishedMessageId()).isEqualTo("950");
            assertThat(races.findById(fixture.race().getId()).orElseThrow().getResults()).isEmpty();
        });
    }
    @Test void aLaterUnapprovedDraftDoesNotRewriteAnAlreadyApprovedSnapshotDuringHttp() {
        var work = queued();
        when(sender.send(any())).thenAnswer(call -> {
            rulings.saveDraft(work.report(), 2, "Test later unapproved draft", "", manager, epoch.managementGeneration());
            return new IncidentSendResult(IncidentSendResult.Disposition.SENT, "950", null);
        });
        assertThat(rulings.dispatch(work.publication())).isTrue();
        tx.executeWithoutResult(status -> {
            var root = rulingRows.findByReportId(work.report()).orElseThrow();
            assertThat(root.getLatestRevisionNumber()).isEqualTo(2); assertThat(root.getPublishedRevisionNumber()).isEqualTo(1);
            assertThat(publications.findById(work.publication()).orElseThrow().getContent()).contains("Test explicitly approved ruling").doesNotContain("Test later unapproved draft");
        });
    }
    @Test void uncertainCorrectionKeepsThePriorConfirmedMessageAndRequiresExplicitReconciliation() {
        var work = queued(); when(sender.send(any())).thenReturn(new IncidentSendResult(IncidentSendResult.Disposition.SENT, "950", null));
        assertThat(rulings.dispatch(work.publication())).isTrue();
        assertThatThrownBy(() -> rulings.saveDraft(work.report(), 3, "Test correction", "", manager, epoch.managementGeneration())).isInstanceOf(BusinessRuleException.class);
        rulings.saveDraft(work.report(), 3, "Test correction", "Test reviewed correction", manager, epoch.managementGeneration());
        UUID correction = rulings.publish(work.report(), 2, 4, manager, epoch.managementGeneration());
        doReturn(new IncidentSendResult(IncidentSendResult.Disposition.UNCERTAIN, null, "SEND_UNCERTAIN")).when(sender).send(any());
        assertThat(rulings.dispatch(correction)).isTrue(); assertThat(state(correction)).isEqualTo(IncidentDeliveryState.UNCERTAIN);
        assertThat(rulings.dispatch(correction)).isFalse();
        tx.executeWithoutResult(status -> {
            var root = rulingRows.findByReportId(work.report()).orElseThrow();
            assertThat(root.getPublishedRevisionNumber()).isEqualTo(1); assertThat(root.getPublishedMessageId()).isEqualTo("950");
            assertThat(publications.findById(correction).orElseThrow().getPreviousMessageId()).isEqualTo("950");
        });
        when(sender.verify(any(), eq("950"))).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new IncidentSendResult(IncidentSendResult.Disposition.SENT, "950", null);
        });
        assertDoesNotThrow(() -> rulings.reconcile(work.report(), correction, 5, 2, "CONFIRM_SENT", "950", manager,
                "Test reviewed exact correction", true, epoch.managementGeneration()));
        assertThat(state(correction)).isEqualTo(IncidentDeliveryState.SENT);
        tx.executeWithoutResult(status -> assertThat(rulingRows.findByReportId(work.report()).orElseThrow().getPublishedRevisionNumber()).isEqualTo(2));
    }
    @Test void aResponseAfterLeaseExpiryCannotAcknowledgeOrTriggerAnAutomaticResend() {
        var work = queued(); when(sender.send(any())).thenAnswer(call -> {
            clock.set(clock.instant().plusSeconds(600)); return new IncidentSendResult(IncidentSendResult.Disposition.SENT, "950", null);
        });
        assertThatThrownBy(() -> rulings.dispatch(work.publication())).isInstanceOf(BusinessRuleException.class);
        assertThat(state(work.publication())).isEqualTo(IncidentDeliveryState.CLAIMED);
        assertThat(rulings.dispatch(work.publication())).isFalse(); assertThat(state(work.publication())).isEqualTo(IncidentDeliveryState.UNCERTAIN);
        verify(sender, times(1)).send(any());
    }
    @ParameterizedTest @ValueSource(strings = {"paused", "import"})
    void pausedOrRestoringContextsDoNotClaimOrSendTheirRulings(String cause) {
        var work = queued();
        if (cause.equals("paused")) tx.executeWithoutResult(status -> ReflectionTestUtils.setField(windowRows.findById(windowId).orElseThrow(), "deliveryPaused", true));
        else assertThat(imports.tryLock()).isTrue();
        try { assertThat(rulings.dispatch(work.publication())).isFalse(); assertThat(state(work.publication())).isEqualTo(IncidentDeliveryState.PENDING); verifyNoInteractions(sender); }
        finally { if (cause.equals("import")) imports.unlock(); }
    }
    @Test void restoredContextCannotResumeWithoutFreshRulingDeliveryReview() {
        queued(); tx.executeWithoutResult(status -> ReflectionTestUtils.setField(windowRows.findById(windowId).orElseThrow(), "deliveryPaused", true));
        assertThatThrownBy(() -> deliveryService.resumeWindow(windowId, 2, manager, "Test reviewed restored context", true, epoch.managementGeneration()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ruling");
        boolean paused = tx.execute(status -> windowRows.findById(windowId).orElseThrow().isDeliveryPaused());
        assertThat(paused).isTrue();
    }
    @Test
    @org.springframework.security.test.context.support.WithMockUser("test-native-steward")
    void nativeReviewUsesBothExpectedVersionsAndRecordsTheTrustedManager() throws Exception {
        var work = queued();
        when(sender.verify(any(), eq(""))).thenReturn(new IncidentSendResult(IncidentSendResult.Disposition.REJECTED, null, "PROVEN_MISSING"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/admin/incidents/" + work.report() + "/ruling/reconcile")
                .param("expectedVersion", "2").param("publicationId", work.publication().toString()).param("publicationVersion", "0")
                .param("decision", "RETRY_MISSING").param("messageId", "").param("reason", "Test reviewed missing ruling")
                .param("reviewed", "true").param("generation", epoch.managementGeneration()).param("actorId", "forged-role"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
        tx.executeWithoutResult(status -> {
            var row = publications.findById(work.publication()).orElseThrow();
            assertThat(row.isRestoreReviewed()).isTrue(); assertThat(row.getReconciledBy()).isEqualTo("test-native-steward");
        });
        verify(sender).verify(any(), eq("")); verify(sender, never()).send(any());
    }
    @Test void nativeCaseDistinguishesQueuedWorkFromConfirmedRulingAndOffersItsReviewForm() throws Exception {
        var work = queued();
        String response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/admin/incidents/" + work.report()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse().getContentAsString();
        var html = org.jsoup.Jsoup.parse(response);
        assertThat(html.text()).contains("No ruling published", "Ruling delivery", "Waiting", "Review ruling delivery");
        assertThat(html.select("form[action$='/ruling/reconcile'] input[name=publicationId]").val()).isEqualTo(work.publication().toString());
        assertThat(html.select("form[action$='/ruling/reconcile'] input[name=publicationVersion]").val()).isEqualTo("0");
    }

    @Test void remoteSuccessFollowedBySqlFailureRetainsAnUnknownClaimAndNeverBlindlyResends() {
        var work = queued();
        when(sender.send(any())).thenAnswer(call -> {
            jdbc.execute("ALTER TABLE incident_ruling_publications ADD CONSTRAINT ctc_test_ruling_ack CHECK (state <> 'SENT')");
            return new IncidentSendResult(IncidentSendResult.Disposition.SENT, "950", null);
        });
        try { assertThatThrownBy(() -> rulings.dispatch(work.publication())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
        finally { jdbc.execute("ALTER TABLE incident_ruling_publications DROP CONSTRAINT ctc_test_ruling_ack"); }
        assertThat(state(work.publication())).isEqualTo(IncidentDeliveryState.CLAIMED);
        tx.executeWithoutResult(status -> assertThat(rulingRows.findByReportId(work.report()).orElseThrow().getPublishedMessageId()).isNull());
        clock.set(clock.instant().plusSeconds(600));
        assertThat(rulings.dispatch(work.publication())).isFalse(); assertThat(state(work.publication())).isEqualTo(IncidentDeliveryState.UNCERTAIN);
        verify(sender, times(1)).send(any());
    }
    @Test void competingWorkersCannotSendTheSameApprovedRulingTwice() throws Exception {
        var work = queued(); var entered = new java.util.concurrent.CountDownLatch(1); var release = new java.util.concurrent.CountDownLatch(1);
        when(sender.send(any())).thenAnswer(call -> {
            entered.countDown(); assertThat(release.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            return new IncidentSendResult(IncidentSendResult.Disposition.SENT, "950", null);
        });
        try (var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = workers.submit(() -> rulings.dispatch(work.publication()));
            try { assertThat(entered.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue(); assertThat(rulings.dispatch(work.publication())).isFalse(); }
            finally { release.countDown(); }
            assertThat(pending.get(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        verify(sender, times(1)).send(any()); assertThat(state(work.publication())).isEqualTo(IncidentDeliveryState.SENT);
    }
    @Test void draftingPublishingAndUncertainCorrectionLeaveExistingRacePointsAndMatchScoresUntouched() {
        record Scores(UUID driver, UUID result) { }
        var scoreRows = tx.execute(status -> {
            var driver = helper.createDriver("TestRuling_" + UUID.randomUUID(), "Test ruling driver");
            var race = races.findById(fixture.race().getId()).orElseThrow();
            var result = new RaceResult(race, driver, 1, 1, true); result.setPointsRace(17); result.setPointsQuali(5); result.setPointsFl(2); result.setPointsTotal(24);
            em.persist(result); var match = matches.findById(fixture.match().getId()).orElseThrow(); match.setHomeScore(120); match.setAwayScore(80);
            return new Scores(driver.getId(), result.getId());
        });
        try {
            var work = queued(); when(sender.send(any())).thenReturn(new IncidentSendResult(IncidentSendResult.Disposition.SENT, "950", null));
            assertThat(rulings.dispatch(work.publication())).isTrue();
            rulings.saveDraft(work.report(), 3, "Test correction without scoring command", "Test reviewed correction", manager, epoch.managementGeneration());
            UUID correction = rulings.publish(work.report(), 2, 4, manager, epoch.managementGeneration());
            doReturn(new IncidentSendResult(IncidentSendResult.Disposition.UNCERTAIN, null, "SEND_UNCERTAIN")).when(sender).send(any());
            assertThat(rulings.dispatch(correction)).isTrue();
            tx.executeWithoutResult(status -> {
                var result = em.find(RaceResult.class, scoreRows.result());
                assertThat(java.util.List.of(result.getPosition(), result.getQualiPosition(), result.getPointsRace(), result.getPointsQuali(), result.getPointsFl(), result.getPointsTotal()))
                        .containsExactly(1, 1, 17, 5, 2, 24);
                var match = matches.findById(fixture.match().getId()).orElseThrow(); assertThat(match.getHomeScore()).isEqualTo(120); assertThat(match.getAwayScore()).isEqualTo(80);
            });
        } finally { tx.executeWithoutResult(status -> { em.remove(em.find(RaceResult.class, scoreRows.result())); em.flush(); em.remove(em.find(Driver.class, scoreRows.driver())); }); }
    }

    @ParameterizedTest @ValueSource(strings = {"null", "disposition", "missing-id", "invalid-id", "failure-code"})
    void malformedAdapterOutcomesBecomeUncertainWithoutConfirmingTheRuling(String cause) {
        var work = queued();
        IncidentSendResult result = switch (cause) {
            case "null" -> null;
            case "disposition" -> new IncidentSendResult(null, null, "TEST_REPLY");
            case "missing-id" -> new IncidentSendResult(IncidentSendResult.Disposition.SENT, null, null);
            case "invalid-id" -> new IncidentSendResult(IncidentSendResult.Disposition.SENT, "untrusted-id", null);
            case "failure-code" -> new IncidentSendResult(IncidentSendResult.Disposition.REJECTED, null, "invalid code");
            default -> throw new AssertionError(cause);
        };
        doReturn(result).when(sender).send(any());
        boolean handled = assertDoesNotThrow(() -> rulings.dispatch(work.publication())); assertThat(handled).isTrue();
        assertThat(state(work.publication())).isEqualTo(IncidentDeliveryState.UNCERTAIN);
        tx.executeWithoutResult(status -> assertThat(rulingRows.findByReportId(work.report()).orElseThrow().getPublishedMessageId()).isNull());
    }
    @Test void aChangedKnownTargetAfterHttpCannotAdvanceTheConfirmedRuling() {
        var work = queued(); UUID mapping = tx.execute(status -> publications.findById(work.publication()).orElseThrow().getThreadPublication().getId());
        when(sender.send(any())).thenAnswer(call -> {
            jdbc.update("UPDATE incident_thread_publications SET public_starter_id='999',public_thread_id='999' WHERE id=?", mapping);
            return new IncidentSendResult(IncidentSendResult.Disposition.SENT, "950", null);
        });
        try { assertThatThrownBy(() -> rulings.dispatch(work.publication())).isInstanceOf(BusinessRuleException.class); }
        finally { jdbc.update("UPDATE incident_thread_publications SET public_starter_id='801',public_thread_id='801' WHERE id=?", mapping); }
        assertThat(state(work.publication())).isEqualTo(IncidentDeliveryState.CLAIMED);
        tx.executeWithoutResult(status -> assertThat(rulingRows.findByReportId(work.report()).orElseThrow().getPublishedMessageId()).isNull());
    }
    @Test void aChangedRestoreGenerationAfterHttpCannotConfirmAnEarlierClaim() {
        var work = queued(); when(sender.send(any())).thenAnswer(call -> {
            epoch.afterRestore(null); return new IncidentSendResult(IncidentSendResult.Disposition.SENT, "950", null);
        });
        assertThatThrownBy(() -> rulings.dispatch(work.publication())).isInstanceOf(BusinessRuleException.class);
        assertThat(state(work.publication())).isEqualTo(IncidentDeliveryState.CLAIMED);
        tx.executeWithoutResult(status -> assertThat(rulingRows.findByReportId(work.report()).orElseThrow().getPublishedMessageId()).isNull());
    }

    @ParameterizedTest @ValueSource(strings = {"draft", "dispatch", "review"})
    void writerAdmissionMustPrecedeAnyCallerSqlTransaction(String action) {
        var work = queued();
        when(sender.verify(any(), eq(""))).thenReturn(new IncidentSendResult(IncidentSendResult.Disposition.REJECTED, null, "PROVEN_MISSING"));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            if (action.equals("draft")) rulings.saveDraft(work.report(), 2, "Test outer transaction draft", "", manager, epoch.managementGeneration());
            else if (action.equals("dispatch")) rulings.dispatch(work.publication());
            else rulings.reconcile(work.report(), work.publication(), 2, 0, "RETRY_MISSING", "", manager, "Test reviewed decision", true, epoch.managementGeneration());
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("admission must precede");
        verifyNoInteractions(sender);
    }
    @Test void anUncheckedExternalReviewCannotGrantPermissionToRetryRestoredWork() {
        var work = queued();
        tx.executeWithoutResult(status -> ReflectionTestUtils.setField(windowRows.findById(windowId).orElseThrow(), "deliveryPaused", true));
        when(sender.verify(any(), eq(""))).thenReturn(new IncidentSendResult(IncidentSendResult.Disposition.REJECTED, null, "PROVEN_MISSING"));
        assertThatThrownBy(() -> rulings.reconcile(work.report(), work.publication(), 2, 0, "RETRY_MISSING", "", manager, "Test missing confirmation", false, epoch.managementGeneration()))
                .isInstanceOf(BusinessRuleException.class);
        tx.executeWithoutResult(status -> assertThat(publications.findById(work.publication()).orElseThrow().isRestoreReviewed()).isFalse());
        verify(sender, never()).verify(any(), any());
    }
    @ParameterizedTest @ValueSource(strings = {"CONFIRM_SENT", "RETRY_MISSING"})
    void unverifiedRemoteEvidenceCannotConfirmOrRequeueAnUnknownRuling(String decision) {
        var work = queued(); when(sender.send(any())).thenReturn(new IncidentSendResult(IncidentSendResult.Disposition.UNCERTAIN, null, "SEND_UNCERTAIN"));
        assertThat(rulings.dispatch(work.publication())).isTrue();
        when(sender.verify(any(), any())).thenReturn(new IncidentSendResult(IncidentSendResult.Disposition.REJECTED, "950", "VERIFICATION_FAILED"));
        assertThatThrownBy(() -> rulings.reconcile(work.report(), work.publication(), 2, 2, decision, decision.equals("CONFIRM_SENT") ? "950" : "", manager,
                "Test unproven remote outcome", true, epoch.managementGeneration())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("could not be verified");
        assertThat(state(work.publication())).isEqualTo(IncidentDeliveryState.UNCERTAIN);
        tx.executeWithoutResult(status -> { var root = rulingRows.findByReportId(work.report()).orElseThrow(); assertThat(root.getPublishedRevisionNumber()).isNull();
            assertThat(publications.findById(work.publication()).orElseThrow().isRestoreReviewed()).isFalse(); });
    }
    @Test void aNewExplicitApprovalCanReplacePendingWorkAndFirstConfirmationCanSkipDrafts() {
        var original = queued(); rulings.saveDraft(original.report(), 2, "Test second approved draft", "", manager, epoch.managementGeneration());
        UUID latest = rulings.publish(original.report(), 2, 3, manager, epoch.managementGeneration());
        assertThat(state(original.publication())).isEqualTo(IncidentDeliveryState.SUPERSEDED);
        assertThat(rulings.dispatch(original.publication())).isFalse();
        when(sender.send(any())).thenReturn(new IncidentSendResult(IncidentSendResult.Disposition.SENT, "950", null));
        assertThat(rulings.dispatch(latest)).isTrue();
        tx.executeWithoutResult(status -> assertThat(rulingRows.findByReportId(original.report()).orElseThrow().getPublishedRevisionNumber()).isEqualTo(2));
        verify(sender, times(1)).send(any());
    }

    @Test void discoveryReturnsOnlyBoundedPersistedDueWork() {
        var work = queued(); assertThat(rulings.dueRulings(10)).contains(work.publication());
        for (int limit : new int[]{0, 26}) assertThatThrownBy(() -> rulings.dueRulings(limit)).isInstanceOf(IllegalArgumentException.class);
    }
}
