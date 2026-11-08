package org.ctc.domain.service;

import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PickWriteExecutor {
    private final ImportLockService imports;
    private final TransactionTemplate transactions;
    private final TransactionTemplate currentReads;

    public PickWriteExecutor(ImportLockService imports, PlatformTransactionManager transactions) {
        this.imports = imports;
        this.transactions = new TransactionTemplate(transactions);
        this.currentReads = new TransactionTemplate(transactions);
        this.currentReads.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public <T> T execute(Supplier<T> operation) { return executeIn(transactions,operation); }

    /** Structural editors must read commits made while they wait for the owning season lock. */
    public <T> T executeReadCommitted(Supplier<T> operation) { return executeIn(currentReads,operation); }

    private <T> T executeIn(TransactionTemplate template, Supplier<T> operation) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Pick writer admission must occur before starting a transaction");
        }
        if (!imports.tryEnterWriter()) throw new BusinessRuleException("A backup restore is in progress; retry later");
        var registered = new AtomicBoolean();
        try {
            return template.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int completionStatus) {
                        imports.exitWriter();
                    }
                });
                registered.set(true);
                return operation.get();
            });
        } catch (RuntimeException failure) {
            throw RaceSelectionConcurrency.translate(failure);
        } finally {
            if (!registered.get()) imports.exitWriter();
        }
    }
}
