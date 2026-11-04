package org.ctc.domain.service;

import java.util.*;
import java.util.concurrent.*;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.integration.OutboundPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class IncidentDueWorkWorkerTest {
    final IncidentDeliveryService delivery=mock(IncidentDeliveryService.class);
    final IncidentDisclosureService disclosure=mock(IncidentDisclosureService.class);
    final IncidentThreadPublicationService publication=mock(IncidentThreadPublicationService.class);
    final IncidentRulingService rulings=mock(IncidentRulingService.class);
    final ImportLockService imports=new ImportLockService(java.time.Duration.ofSeconds(1));
    final OutboundPolicy enabled=new OutboundPolicy(true,new MockEnvironment());
    final IncidentDueWorkWorker worker=new IncidentDueWorkWorker(delivery,disclosure,publication,rulings,imports,enabled);

    @Test
    void givenPersistedDueWindowsAndReceipts_whenTicking_thenRequestBoundedBatchesAndContinueAfterOneDomainFailure() {
        UUID one=UUID.randomUUID(),two=UUID.randomUUID(),receipt=UUID.randomUUID();
        when(delivery.dueWindows(10)).thenReturn(List.of(one,two));when(delivery.dueReceipts(10)).thenReturn(List.of(receipt));
        doThrow(new org.ctc.domain.exception.BusinessRuleException("Test PRIVATE closed work")).when(disclosure).freezeDueWindow(one);
        worker.tick();verify(disclosure).freezeDueWindow(one);verify(disclosure).freezeDueWindow(two);verify(delivery).dispatch(receipt);
        worker.tick();verify(delivery,times(2)).dueWindows(10);
    }
    @Test
    void givenRecoveryPolicyOrRestoreAdmission_whenTicking_thenDoNotDiscoverOrMutateWork() {
        new IncidentDueWorkWorker(delivery,disclosure,publication,rulings,imports,new OutboundPolicy(true,new MockEnvironment().withProperty("spring.profiles.active","recovery-check"))).tick();
        assertThat(imports.tryLock()).isTrue();try {worker.tick();}finally{imports.unlock();}
        verifyNoInteractions(delivery,disclosure,publication,rulings);
    }
    @Test
    void givenAnActiveTick_whenAnotherTickArrives_thenDoNotDispatchTheSameBatchConcurrently() throws Exception {
        var entered=new CountDownLatch(1);var release=new CountDownLatch(1);UUID receipt=UUID.randomUUID();
        when(delivery.dueWindows(10)).thenReturn(List.of());when(delivery.dueReceipts(10)).thenReturn(List.of(receipt));
        when(delivery.dispatch(receipt)).thenAnswer(call -> {entered.countDown();assertThat(release.await(5,TimeUnit.SECONDS)).isTrue();return true;});
        try(var workers=Executors.newVirtualThreadPerTaskExecutor()) {var running=workers.submit(worker::tick);try {assertThat(entered.await(5,TimeUnit.SECONDS)).isTrue();worker.tick();verify(delivery,times(1)).dueReceipts(10);}finally{release.countDown();}running.get(10,TimeUnit.SECONDS);}
    }
    @Test
    void givenDefaultAndExplicitEnabledConfiguration_whenBooting_thenKeepTheSchedulerOffUntilOperatorActivation() {
        var context=new ApplicationContextRunner().withUserConfiguration(IncidentDeliverySchedulingConfiguration.class)
            .withBean(IncidentDeliveryService.class,()->delivery).withBean(IncidentDisclosureService.class,()->disclosure).withBean(IncidentThreadPublicationService.class,()->publication).withBean(IncidentRulingService.class,()->rulings).withBean(ImportLockService.class,()->imports).withBean(OutboundPolicy.class,()->enabled);
        context.run(app -> assertThat(app).doesNotHaveBean(IncidentDueWorkWorker.class));
        context.withPropertyValues("app.discord.incidents.delivery-enabled=true","app.discord.incidents.worker-initial-delay-ms=60000").run(app -> assertThat(app).hasSingleBean(IncidentDueWorkWorker.class));
    }
    @Test void givenPersistedDisclosureSteps_whenTicking_thenDispatchTheBoundedQueueAndContinueAfterOneFailure() {
        var one=UUID.randomUUID();var two=UUID.randomUUID();
        when(publication.dueDisclosures(10)).thenReturn(List.of(one,two));doThrow(new org.ctc.domain.exception.BusinessRuleException("Test unavailable step")).when(publication).dispatch(one);
        worker.tick();verify(publication).dueDisclosures(10);verify(publication).dispatch(one);verify(publication).dispatch(two);
    }
    @Test void rulingWorkUsesTheSameBoundedWorkerAndContinuesAfterOneRejectedCase() {
        var one=UUID.randomUUID();var two=UUID.randomUUID();
        when(rulings.dueRulings(10)).thenReturn(List.of(one,two));
        doThrow(new org.ctc.domain.exception.BusinessRuleException("Test rejected ruling")).when(rulings).dispatch(one);
        worker.tick();verify(rulings).dueRulings(10);verify(rulings).dispatch(one);verify(rulings).dispatch(two);
    }

}
