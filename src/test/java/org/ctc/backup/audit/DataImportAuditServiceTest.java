package org.ctc.backup.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 75 / Plan 02 — Surefire unit tests for {@link DataImportAuditService}.
 *
 * <p>Boots a {@code @SpringBootTest @ActiveProfiles("dev")} context so the embedded H2
 * datasource, the Phase 72 V7 migration, and the {@code @Qualifier("backupObjectMapper")}
 * bean are all wired. The {@link DataImportAuditRepository} is replaced via
 * {@link MockitoBean} so the test stays behaviorally focused (no JPA assertions — those
 * live in Plan 06 IT). The {@link PlatformTransactionManager} is observed via
 * {@link MockitoSpyBean} so the REQUIRES_NEW propagation can be asserted mechanically
 * (per RESEARCH §Pitfall 3).
 */
@SpringBootTest
@ActiveProfiles("dev")
class DataImportAuditServiceTest {

    @Autowired
    private DataImportAuditService service;

    @Autowired
    @Qualifier("backupObjectMapper")
    private ObjectMapper backupObjectMapper;

    @MockitoBean
    private DataImportAuditRepository repository;

    @MockitoSpyBean
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        when(repository.save(any(DataImportAudit.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void givenSuccessfulImport_whenRecordResultCalled_thenRepositorySaveInvokedWithBuiltEntity() throws Exception {
        // given
        UUID auditId = UUID.randomUUID();
        Map<String, Long> wiped = new LinkedHashMap<>();
        wiped.put("cars", 12L);
        wiped.put("drivers", 7L);
        Map<String, Long> restored = new LinkedHashMap<>();
        restored.put("cars", 12L);
        restored.put("drivers", 7L);

        // when
        DataImportAudit returned = service.recordResult(
                auditId, null, 1, wiped, restored, "backup-2026-05-14.zip", true);

        // then — repository.save was invoked once with the captured entity
        ArgumentCaptor<DataImportAudit> captor = ArgumentCaptor.forClass(DataImportAudit.class);
        verify(repository).save(captor.capture());
        DataImportAudit captured = captor.getValue();

        assertThat(captured.getId()).isEqualTo(auditId);
        assertThat(captured.isSuccess()).isTrue();
        assertThat(captured.getExecutedBy()).isEqualTo("dev"); // dev profile fork
        assertThat(captured.getExecutedAt()).isNotNull();
        assertThat(captured.getSchemaVersion()).isEqualTo(1);
        assertThat(captured.getSourceFilename()).isEqualTo("backup-2026-05-14.zip");

        // and — JSON-text round-trips back through the same backupObjectMapper
        Map<String, Long> roundTrippedWiped = backupObjectMapper.readValue(
                captured.getTableCountsWiped(), new TypeReference<>() { });
        Map<String, Long> roundTrippedRestored = backupObjectMapper.readValue(
                captured.getTableCountsRestored(), new TypeReference<>() { });
        assertThat(roundTrippedWiped).isEqualTo(wiped);
        assertThat(roundTrippedRestored).isEqualTo(restored);

        // and — the returned entity is the same instance (mock returns input via thenAnswer)
        assertThat(returned).isSameAs(captured);
    }

    @Test
    void givenFailedImport_whenRecordResultCalled_thenSuccessFalseAndEmptyJsonMaps() {
        // given
        UUID auditId = UUID.randomUUID();
        Map<String, Long> emptyWiped = Collections.emptyMap();
        Map<String, Long> emptyRestored = Collections.emptyMap();

        // when
        service.recordResult(auditId, null, 1, emptyWiped, emptyRestored,
                "broken-backup.zip", false);

        // then
        ArgumentCaptor<DataImportAudit> captor = ArgumentCaptor.forClass(DataImportAudit.class);
        verify(repository).save(captor.capture());
        DataImportAudit captured = captor.getValue();

        assertThat(captured.isSuccess()).isFalse();
        assertThat(captured.getTableCountsWiped()).isEqualTo("{}");
        assertThat(captured.getTableCountsRestored()).isEqualTo("{}");
        assertThat(captured.getSourceFilename()).isEqualTo("broken-backup.zip");
        assertThat(captured.getExecutedBy()).isEqualTo("dev");
    }

    @Test
    void givenRequiresNewPropagation_whenRecordResultInvoked_thenSpyTxManagerOpensNewTransaction() {
        // given
        UUID auditId = UUID.randomUUID();

        // when
        service.recordResult(auditId, null, 1, Map.of(), Map.of(),
                "propagation-check.zip", true);

        // then — the transaction manager was asked to open at least one TX with REQUIRES_NEW
        ArgumentCaptor<TransactionDefinition> defCaptor =
                ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, atLeastOnce()).getTransaction(defCaptor.capture());

        boolean sawRequiresNew = defCaptor.getAllValues().stream()
                .anyMatch(def -> def != null
                        && def.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        assertThat(sawRequiresNew)
                .as("DataImportAuditService.recordResult must open a REQUIRES_NEW transaction")
                .isTrue();
    }
}
