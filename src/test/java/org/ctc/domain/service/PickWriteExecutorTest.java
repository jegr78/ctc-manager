package org.ctc.domain.service;

import java.time.Duration;
import org.ctc.backup.exception.ImportWritersStillActiveException;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickWriteExecutorTest {
    @Test
    void givenRestoreLock_whenWriting_thenRejectBeforeStartingTransaction() {
        // given / when / then
        var imports = new ImportLockService(Duration.ofMillis(1));
        var manager = mock(PlatformTransactionManager.class);
        var executor = new PickWriteExecutor(imports, manager);
        assertThat(imports.tryLock()).isTrue();
        try {
            assertThatThrownBy(() -> executor.execute(() -> "private"))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("restore");
            verifyNoInteractions(manager);
        } finally {
            imports.unlock();
        }
    }

    @Test
    void givenAdmittedWriter_whenCommitting_thenHoldLeaseThroughCommitAndReleaseAfterward() throws Exception {
        // given / when / then
        var imports = new ImportLockService(Duration.ofMillis(1));
        var manager = new Manager(imports);
        var executor = new PickWriteExecutor(imports, manager);
        assertThat(executor.execute(() -> "receipt")).isEqualTo("receipt");
        assertThat(manager.committed).isTrue();
        assertThat(imports.tryLock()).isTrue();
        try {
            imports.awaitWritersDrained();
        } finally {
            imports.unlock();
        }
    }

    @Test
    void givenAlreadyActiveTransaction_whenWriting_thenRejectBeforeAdmissionOrAnotherTransaction() {
        // given
        var imports = new ImportLockService(Duration.ofMillis(1));
        var manager = mock(PlatformTransactionManager.class);
        var executor = new PickWriteExecutor(imports, manager);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            // when / then
            assertThatThrownBy(() -> executor.execute(() -> "receipt"))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("before", "transaction");
            verifyNoInteractions(manager);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void givenFailedWriter_whenRollingBack_thenKeepLeaseThroughRollbackAndReleaseAfterward() throws Exception {
        // given / when / then
        var imports = new ImportLockService(Duration.ofMillis(1));
        var manager = new Manager(imports);
        var executor = new PickWriteExecutor(imports, manager);
        assertThatThrownBy(() -> executor.execute(() -> { throw new BusinessRuleException("Invalid selection"); }))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Invalid selection");
        assertThat(manager.rolledBack).isTrue();
        assertThat(imports.tryLock()).isTrue();
        try {
            imports.awaitWritersDrained();
        } finally {
            imports.unlock();
        }
    }

    @Test
    void givenDatabaseLockFailureAtTransactionStart_whenWriting_thenReleaseAdmissionAndReturnRetryReason() throws Exception {
        // given
        var imports = new ImportLockService(Duration.ofMillis(1));
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenThrow(new org.springframework.dao.PessimisticLockingFailureException("Start conflict"));
        var executor = new PickWriteExecutor(imports, manager);
        // when / then
        assertThatThrownBy(() -> executor.execute(() -> "receipt"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("changed");
        assertThat(imports.tryLock()).isTrue();
        try {
            imports.awaitWritersDrained();
        } finally {
            imports.unlock();
        }
    }

    private static void assertWriterHeld(ImportLockService imports) {
        assertThat(imports.tryLock()).isTrue();
        try {
            assertThatThrownBy(imports::awaitWritersDrained).isInstanceOf(ImportWritersStillActiveException.class);
        } finally {
            imports.unlock();
        }
    }

    private static class Manager extends AbstractPlatformTransactionManager {
        private final ImportLockService imports;
        private boolean active;
        boolean committed;
        boolean rolledBack;

        Manager(ImportLockService imports) { this.imports = imports; }
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected boolean isExistingTransaction(Object transaction) { return active; }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { active = true; }
        @Override protected void doCommit(DefaultTransactionStatus status) { assertWriterHeld(imports); committed = true; }
        @Override protected void doRollback(DefaultTransactionStatus status) { assertWriterHeld(imports); rolledBack = true; }
        @Override protected void doCleanupAfterCompletion(Object transaction) { active = false; }
    }
}
