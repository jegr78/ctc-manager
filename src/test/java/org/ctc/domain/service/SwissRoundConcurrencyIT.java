package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.concurrent.*;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.SeasonLockingRepositoryImpl;
import org.ctc.testsupport.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@CtcDevSpringBootContext @Tag("integration")
class SwissRoundConcurrencyIT extends SwissRoundFixture {
    @Autowired SwissRoundService rounds;
    @Autowired EntityManager em;
    @Autowired ImportLockService imports;
    @Autowired MatchdayService matchdays;
    @Autowired RaceAttachmentService attachments;
    @MockitoSpyBean SeasonLockingRepositoryImpl lockedSeasons;
    @MockitoSpyBean RaceCarSelectionService selections;

    UUID firstRound() {
        var form=rounds.editor(phaseId,null,null,null).form();
        for(int i=0;i<2;i++) { form.getRows().get(i).setHomeTeamId(teamIds.get(i*2)); form.getRows().get(i).setAwayTeamId(teamIds.get(i*2+1)); }
        return rounds.save(form);
    }
    @Autowired SwissPairingService automatic;
    @Test void givenAutomaticGenerationWaitingOnSeason_whenPhaseLegsChange_thenUseFreshConfiguration() throws Exception {
        waitingWriter(() -> { em.find(SeasonPhase.class,phaseId).setLegs(3); return null; },
                () -> automatic.generateNextRound(phaseId,null),false);
        var day=days.findByPhaseIdOrderBySortIndexAsc(phaseId).getFirst();
        tx.executeWithoutResult(status -> assertThat(matches.findByMatchdayId(day.getId())).hasSize(2)
                .allSatisfy(match -> assertThat(races.findByMatchId(match.getId())).hasSize(3)));
    }
    @Test void givenHistoricalRoundWithCustomSortIndex_whenGeneratingAutomatically_thenAppendAfterStoredRound() {
        UUID day=firstRound();
        tx.executeWithoutResult(status -> { em.find(Matchday.class,day).setSortIndex(9); for(var match:matches.findByMatchdayId(day)) {match.setHomeScore(20); match.setAwayScore(10);} });
        var next=automatic.generateNextRound(phaseId,null);
        assertThat(next.getSortIndex()).isEqualTo(10); assertThat(next.getLabel()).isEqualTo("Round 2");
    }
    @Test void givenRestoreAdmissionHeld_whenSavingRound_thenRejectBeforeAnyWrite() {
        var form=rounds.editor(phaseId,null,null,null).form();
        assertThat(imports.tryLock()).isTrue();
        try { assertThatThrownBy(() -> rounds.save(form)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("restore"); }
        finally { imports.unlock(); }
        assertThat(days.findByPhaseIdOrderBySortIndexAsc(phaseId)).isEmpty();
    }
    @Test void givenRoundEditWaitingOnSeason_whenMetadataCommits_thenRejectStaleContextWithoutOverwritingIt() throws Exception {
        UUID day=firstRound(); var form=rounds.editor(phaseId,null,day,null).form(); form.setLabel("Test stale overwrite");
        waitingWriter(() -> matchdays.saveMatchday("Test current label",9,seasonId,day),() -> rounds.save(form),true);
        assertThat(days.findById(day).orElseThrow().getLabel()).isEqualTo("Test current label");
        assertThat(matches.findByMatchdayId(day)).hasSize(2);
    }
    @Test void givenRoundEditWaitingOnSeason_whenEvidenceCommits_thenRejectStaleIdentityAndRetainEvidence() throws Exception {
        UUID day=firstRound(); var form=rounds.editor(phaseId,null,day,null).form();
        var row=form.getRows().getFirst(); UUID home=row.getHomeTeamId(); row.setHomeTeamId(row.getAwayTeamId()); row.setAwayTeamId(home);
        UUID race=tx.execute(status -> races.findByMatchId(row.getMatchId()).getFirst().getId());
        waitingWriter(() -> attachments.addLink(race,"Test historical evidence","https://example.invalid/test"),() -> rounds.save(form),true);
        tx.executeWithoutResult(status -> {
            assertThat(em.find(Race.class,race).getAttachments()).singleElement().satisfies(a -> assertThat(a.getName()).isEqualTo("Test historical evidence"));
            assertThat(em.find(Match.class,row.getMatchId()).getHomeTeam().getId()).isEqualTo(home);
        });
    }
    @Test void givenTwoRoundEditsWithSameSnapshot_whenFirstCommits_thenSecondCannotOverwriteIt() throws Exception {
        UUID day=firstRound(); var first=rounds.editor(phaseId,null,day,null).form(); var second=rounds.editor(phaseId,null,day,null).form();
        first.setLabel("Test winning edit"); second.setLabel("Test losing edit");
        var held=new CountDownLatch(1); var release=new CountDownLatch(1);
        var target=AopTestUtils.<RaceCarSelectionService>getUltimateTargetObject(selections);
        doAnswer(call -> { held.countDown(); assertThat(release.await(15,TimeUnit.SECONDS)).isTrue(); return call.callRealMethod(); })
                .when(target).validateAssignments(eq(seasonId),anyList());
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            var winning=workers.submit(() -> rounds.save(first));
            try {
                assertThat(held.await(15,TimeUnit.SECONDS)).isTrue(); var losing=workers.submit(() -> rounds.save(second));
                assertThatThrownBy(() -> losing.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown(); assertThat(winning.get(15,TimeUnit.SECONDS)).isEqualTo(day);
                assertThatThrownBy(() -> losing.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessRuleException.class);
            } finally { release.countDown(); winning.get(15,TimeUnit.SECONDS); reset(target); }
        }
        assertThat(days.findById(day).orElseThrow().getLabel()).isEqualTo(first.getLabel());
    }
    @Test void givenAllocationFailure_whenSavingChangedRound_thenKeepOriginalPairingAndRaceIds() {
        UUID day=firstRound(); var form=rounds.editor(phaseId,null,day,null).form(); var row=form.getRows().getFirst(); UUID home=row.getHomeTeamId();
        row.setHomeTeamId(row.getAwayTeamId()); row.setAwayTeamId(home);
        var target=AopTestUtils.<RaceCarSelectionService>getUltimateTargetObject(selections);
        doReturn(new RaceCarSelectionService.Validation("Test allocation conflict",null,null)).when(target).validateAssignments(eq(seasonId),anyList());
        try { assertThatThrownBy(() -> rounds.save(form)).isInstanceOf(BusinessRuleException.class).hasMessage("Test allocation conflict"); }
        finally { reset(target); }
        tx.executeWithoutResult(status -> assertThat(em.find(Match.class,row.getMatchId()).getHomeTeam().getId()).isEqualTo(home));
        assertThat(races.findByMatchId(row.getMatchId())).hasSize(2);
    }
    @Test void givenCallerMutatesSubmittedRowsAfterValidation_whenSaving_thenApplyOnlyTheCapturedRound() {
        var form=rounds.editor(phaseId,null,null,null).form(); var row=form.getRows().getFirst(); row.setHomeTeamId(teamIds.get(0)); row.setAwayTeamId(teamIds.get(1));
        var target=AopTestUtils.<RaceCarSelectionService>getUltimateTargetObject(selections);
        doAnswer(call -> { var result=call.callRealMethod(); row.setHomeTeamId(teamIds.get(1)); form.setLabel("Test caller mutation"); return result; })
                .when(target).validateAssignments(eq(seasonId),anyList());
        UUID day;
        try { day=rounds.save(form); } finally { reset(target); }
        tx.executeWithoutResult(status -> assertThat(matches.findByMatchdayId(day)).singleElement().satisfies(match -> {
            assertThat(match.getHomeTeam().getId()).isEqualTo(teamIds.get(0)); assertThat(match.getAwayTeam().getId()).isEqualTo(teamIds.get(1));
            assertThat(match.getMatchday().getLabel()).isEqualTo("Round 1");
        }));
    }
    @Test void givenRoundTransactionHoldingSeason_whenAttachmentWriterStarts_thenEvidenceWaitsForPairingCommit() throws Exception {
        UUID day=firstRound(); UUID race=tx.execute(status -> races.findByMatchdayId(day).getFirst().getId());
        waitingWriter(() -> { em.find(Matchday.class,day).setLabel("Test round commit"); return null; },
                () -> attachments.addLink(race,"Test after commit","https://example.invalid/current"),false);
        tx.executeWithoutResult(status -> assertThat(em.find(Race.class,race).getAttachments()).hasSize(1));
    }
    @Test void givenRoundTransactionHoldingSeason_whenMatchdayWriterStarts_thenMetadataWaitsForRoundCommit() throws Exception {
        UUID day=firstRound();
        waitingWriter(() -> null,
                () -> matchdays.saveMatchday("Test subsequent label",5,seasonId,day),false);
        assertThat(days.findById(day).orElseThrow().getLabel()).isEqualTo("Test subsequent label");
    }
    private void waitingWriter(Callable<?> change, Callable<?> waiting, boolean rejected) throws Exception {
        var held=new CountDownLatch(1); var release=new CountDownLatch(1); var attempted=new CountDownLatch(1);
        var root=AopTestUtils.<SeasonLockingRepositoryImpl>getUltimateTargetObject(lockedSeasons);
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            var owner=workers.submit(() -> tx.execute(status -> {
                seasons.findByIdForUpdate(seasonId).orElseThrow();
                try { change.call(); em.flush(); held.countDown(); assertThat(release.await(15,TimeUnit.SECONDS)).isTrue(); }
                catch(Exception failure) { throw new IllegalStateException(failure); }
                return null;
            }));
            try {
                assertThat(held.await(15,TimeUnit.SECONDS)).isTrue();
                // Attachment writers acquire the same owner through ScoreWriteLockService's query.
                doAnswer(call -> { attempted.countDown(); return call.callRealMethod(); }).when(root).findByIdForUpdate(seasonId);
                var writer=workers.submit(() -> { attempted.countDown(); return waiting.call(); });
                assertThat(attempted.await(15,TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> writer.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                release.countDown(); owner.get(15,TimeUnit.SECONDS);
                if(rejected) assertThatThrownBy(() -> writer.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(BusinessRuleException.class)
                        .satisfies(error -> assertThat(error.getCause().getMessage()).containsAnyOf("changed","Reload"));
                else writer.get(15,TimeUnit.SECONDS);
            } finally { release.countDown(); owner.get(15,TimeUnit.SECONDS); reset(root); }
        }
    }
}
