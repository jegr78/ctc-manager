package org.ctc.backup.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/**
 * Surefire tests for {@link DataImportAuditService}.
 *
 * <p>Boots a {@code @SpringBootTest @ActiveProfiles("dev")} context so the embedded H2
 * datasource, Flyway migrations, and the {@code @Qualifier("backupObjectMapper")} bean are
 * all wired. The {@link PlatformTransactionManager} is observed via {@link MockitoSpyBean}
 * to assert REQUIRES_NEW propagation.
 *
 * <p>The service writes via {@code JdbcTemplate.update(...)} INSERT — both
 * {@code repository.save(...)} and {@code em.persist(...)} fail on a pre-allocated UUID with
 * {@code GenerationType.UUID}. Tests read back via {@link DataImportAuditRepository#findById(Object)}.
 * Because the service runs under {@code REQUIRES_NEW}, every {@code recordResult} call commits
 * in its own inner transaction — rows are tracked and deleted explicitly in
 * {@link #cleanupPersistedRows()} so tests stay isolated.
 */
@SpringBootTest
@ActiveProfiles("dev")
class DataImportAuditServiceTest {

    @Autowired
    private DataImportAuditService service;

    @Autowired
    @Qualifier("backupObjectMapper")
    private ObjectMapper backupObjectMapper;

    @Autowired
    private DataImportAuditRepository repository;

    @MockitoSpyBean
    private PlatformTransactionManager transactionManager;

    /** UUIDs persisted during a test; deleted in {@link #cleanupPersistedRows()}. */
    private final List<UUID> persistedAuditIds = new ArrayList<>();

    @AfterEach
    void cleanupPersistedRows() {
        for (UUID auditId : persistedAuditIds) {
            try {
                repository.deleteById(auditId);
            } catch (RuntimeException ignored) {
                // best-effort — the next test will still see a clean slate via fresh UUIDs
            }
        }
        persistedAuditIds.clear();
    }

    @Test
    void givenSuccessfulImport_whenRecordResultCalled_thenEntityPersistedWithBuiltFields() throws Exception {
        // given
        UUID auditId = UUID.randomUUID();
        persistedAuditIds.add(auditId);
        Map<String, Long> wiped = new LinkedHashMap<>();
        wiped.put("cars", 12L);
        wiped.put("drivers", 7L);
        Map<String, Long> restored = new LinkedHashMap<>();
        restored.put("cars", 12L);
        restored.put("drivers", 7L);

        // when
        DataImportAudit returned = service.recordResult(
                auditId, null, 1, wiped, restored, "backup-2026-05-14.zip", true);

        // then — row exists with the expected fields (read-back via repository)
        DataImportAudit reloaded = repository.findById(auditId).orElseThrow(
                () -> new AssertionError("Audit row not persisted for id=" + auditId));

        assertThat(reloaded.getId()).isEqualTo(auditId);
        assertThat(reloaded.isSuccess()).isTrue();
        assertThat(reloaded.getExecutedBy()).isEqualTo("dev"); // dev profile fork
        assertThat(reloaded.getExecutedAt()).isNotNull();
        assertThat(reloaded.getSchemaVersion()).isEqualTo(1);
        assertThat(reloaded.getSourceFilename()).isEqualTo("backup-2026-05-14.zip");

        // and — JSON-text round-trips back through the same backupObjectMapper
        Map<String, Long> roundTrippedWiped = backupObjectMapper.readValue(
                reloaded.getTableCountsWiped(), new TypeReference<>() { });
        Map<String, Long> roundTrippedRestored = backupObjectMapper.readValue(
                reloaded.getTableCountsRestored(), new TypeReference<>() { });
        assertThat(roundTrippedWiped).isEqualTo(wiped);
        assertThat(roundTrippedRestored).isEqualTo(restored);

        // and — the returned entity carries the same ID
        assertThat(returned.getId()).isEqualTo(auditId);
    }

    @Test
    void givenFailedImport_whenRecordResultCalled_thenSuccessFalseAndEmptyJsonMaps() {
        // given
        UUID auditId = UUID.randomUUID();
        persistedAuditIds.add(auditId);
        Map<String, Long> emptyWiped = Collections.emptyMap();
        Map<String, Long> emptyRestored = Collections.emptyMap();

        // when
        service.recordResult(auditId, null, 1, emptyWiped, emptyRestored,
                "broken-backup.zip", false);

        // then
        DataImportAudit reloaded = repository.findById(auditId).orElseThrow(
                () -> new AssertionError("Failure-path audit row not persisted for id=" + auditId));

        assertThat(reloaded.isSuccess()).isFalse();
        assertThat(reloaded.getTableCountsWiped()).isEqualTo("{}");
        assertThat(reloaded.getTableCountsRestored()).isEqualTo("{}");
        assertThat(reloaded.getSourceFilename()).isEqualTo("broken-backup.zip");
        assertThat(reloaded.getExecutedBy()).isEqualTo("dev");
    }

    @Test
    void givenRequiresNewPropagation_whenRecordResultInvoked_thenSpyTxManagerOpensNewTransaction() {
        // given
        UUID auditId = UUID.randomUUID();
        persistedAuditIds.add(auditId);

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
