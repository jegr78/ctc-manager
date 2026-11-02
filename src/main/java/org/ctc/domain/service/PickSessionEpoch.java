package org.ctc.domain.service;

import java.util.function.LongSupplier;
import java.util.concurrent.atomic.AtomicLong;
import org.ctc.backup.event.BackupImportSucceededEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PickSessionEpoch implements LongSupplier {
    private final AtomicLong generation = new AtomicLong();
    @Override public long getAsLong() { return generation.get(); }
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterRestore(BackupImportSucceededEvent event) { generation.incrementAndGet(); }
}
