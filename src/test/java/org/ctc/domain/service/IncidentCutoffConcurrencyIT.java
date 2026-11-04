package org.ctc.domain.service;

import java.util.UUID;
import java.util.concurrent.*;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.IncidentDeliveryOperation;
import org.ctc.domain.model.IncidentPayload;
import org.ctc.domain.repository.IncidentDeliveryRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("integration")
class IncidentCutoffConcurrencyIT extends IncidentIntegrationFixture {
    @Autowired IncidentDeliveryRepository deliveryRows;
    @Autowired IncidentDisclosureService disclosure;
    @Test
    void givenEditAdmittedBeforeCutoffButWaitingOnTheSeason_whenClosureWinsItsCurrentTimeCheck_thenFreezeOnlyTheAcceptedRevision() throws Exception {
        windows.open(contextId,NOW.plusSeconds(60),0,manager);var saved=reports.submit(windowId,fixture.race().getId(),1,UUID.randomUUID(),actor,payload);
        var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var preauthorized=new CountDownLatch(1);
        doAnswer(call -> {preauthorized.countDown();return null;}).when(access).requireCurrent(any(),any(),any());
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {
            var blocker=workers.submit(() -> tx.execute(status -> {seasons.findByIdForUpdate(fixture.season().getId()).orElseThrow();locked.countDown();try {assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}return true;}));
            try {
                assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
                var edit=workers.submit(() -> {try {reports.edit(saved.reportId(),1,1,UUID.randomUUID(),actor,new IncidentPayload("Test late guest","00:00","Test PRIVATE late revision",""));return false;}catch(BusinessRuleException expected){return true;}});
                assertThat(preauthorized.await(5,TimeUnit.SECONDS)).isTrue();clock.set(NOW.plusSeconds(60));var close=workers.submit(() -> disclosure.freezeDueWindow(windowId));release.countDown();
                assertThat(blocker.get(10,TimeUnit.SECONDS)).isTrue();assertThat(edit.get(10,TimeUnit.SECONDS)).isTrue();assertThat(close.get(10,TimeUnit.SECONDS)).isTrue();
            } finally {release.countDown();}
        }
        tx.executeWithoutResult(status -> {var queued=deliveryRows.findByReportWindowId(windowId).stream().filter(row -> row.getOperation()==IncidentDeliveryOperation.DISCLOSURE).toList();assertThat(queued).hasSize(1);assertThat(queued.getFirst().getRevision().getRevisionNumber()).isEqualTo(1);assertThat(queued.getFirst().getRevision().getPayload()).isEqualTo(payload);});
    }
}
