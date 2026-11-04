package org.ctc.domain.service;

import java.util.UUID;
import java.util.concurrent.*;
import org.ctc.CtcManagerApplication;
import org.ctc.domain.incidents.IncidentThreadClaim;
import org.ctc.domain.model.IncidentThreadPublication;
import org.ctc.domain.model.IncidentThreadStage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes=CtcManagerApplication.class)
@ActiveProfiles({"local","dev"})
@Testcontainers
@EnabledIfSystemProperty(named="docker.available",matches="true")
@Tag("integration")
class IncidentThreadMappingRaceMariaDbIT extends IncidentThreadDispatchFixture {
    @Container static MariaDBContainer<?> database=new MariaDBContainer<>("mariadb:11").withDatabaseName("ctc_thread_mapping_race").withUsername("ctc").withPassword("test");
    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",database::getJdbcUrl);properties.add("spring.datasource.username",database::getUsername);properties.add("spring.datasource.password",database::getPassword);properties.add("spring.datasource.driver-class-name",() -> "org.mariadb.jdbc.Driver");
    }
    @MockitoSpyBean IncidentWindowService lockingWindows;
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void givenARepeatableReadSnapshotBeforeAWindowWait_whenAnotherWorkerCommits_thenUseItsCurrentMappingOrDeferBeforeAnyHttp(boolean deliveryChanged) throws Exception {
        UUID id=disclosure();var reachedWindow=new CountDownLatch(1);var storedMapping=new java.util.concurrent.atomic.AtomicReference<UUID>();
        doAnswer(call -> {if(Thread.currentThread().getName().startsWith("test-stale-publication-"))reachedWindow.countDown();return call.callRealMethod();}).when(lockingWindows).lock(windowId);
        try(var workers=Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("test-stale-publication-",0).factory())) {
            var pending=new java.util.concurrent.atomic.AtomicReference<Future<Boolean>>();
            tx.executeWithoutResult(status -> {
                windows.lock(windowId);pending.set(workers.submit(() -> publication.dispatch(id)));
                assertDoesNotThrow(() -> assertThat(reachedWindow.await(5,TimeUnit.SECONDS)).isTrue());
                var row=deliveryRows.findByIdForUpdate(id).orElseThrow();
                var mapping=threadRows.save(IncidentThreadPublication.capture(row,"600000000000000006","500000000000000005","",IncidentThreadProjection.capture(row).messages().size()));
                storedMapping.set(mapping.getId());
                if(deliveryChanged) {
                    var token=UUID.randomUUID();row.claim(token,epoch.managementGeneration(),clock.instant(),clock.instant().plusSeconds(600));long version=row.getVersion();
                    mapping.accept(IncidentThreadStage.PRIVATE_STARTER,0,"701",null);row.continueDisclosure(token,version,epoch.managementGeneration(),clock.instant());
                }
                threadRows.flush();
            });
            assertThatThrownBy(() -> pending.get().get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).cause().isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class);
            verifyNoInteractions(threadSender);assertThat(publication.dispatch(id)).isTrue();
        }
        var claim=org.mockito.ArgumentCaptor.forClass(IncidentThreadClaim.class);verify(threadSender,times(1)).send(claim.capture());assertThat(claim.getValue().work().stage()).isEqualTo(deliveryChanged?IncidentThreadStage.PRIVATE_THREAD:IncidentThreadStage.PRIVATE_STARTER);assertThat(stage(id)).isEqualTo(deliveryChanged?IncidentThreadStage.PUBLIC_STARTER:IncidentThreadStage.PRIVATE_THREAD);
        tx.executeWithoutResult(status -> assertThat(threadRows.findByDeliveryId(id).orElseThrow().getId()).isEqualTo(storedMapping.get()));
    }
}
