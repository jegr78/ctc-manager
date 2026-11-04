package org.ctc.domain.service;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.integration.OutboundPolicy;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
@RequiredArgsConstructor
public final class IncidentDueWorkWorker {
    private final IncidentDeliveryService delivery;
    private final IncidentDisclosureService disclosure;
    private final IncidentThreadPublicationService publication;
    private final ImportLockService imports;
    private final OutboundPolicy outbound;
    private final AtomicBoolean working = new AtomicBoolean();

    @Scheduled(fixedDelayString="${app.discord.incidents.worker-delay-ms:5000}",initialDelayString="${app.discord.incidents.worker-initial-delay-ms:1000}")
    public void tick() {
        if(!outbound.isEnabled() || imports.isLocked() || !working.compareAndSet(false,true)) return;
        try {
            for(var id:delivery.dueWindows(10)) attempt(() -> disclosure.freezeDueWindow(id));
            for(var id:delivery.dueReceipts(10)) attempt(() -> delivery.dispatch(id));
            for(var id:publication.dueDisclosures(10)) attempt(() -> publication.dispatch(id));
        } catch(RuntimeException unavailable) { log.warn("Incident due-work discovery is unavailable; inspect persistent delivery state"); }
        finally {working.set(false);}
    }
    private static void attempt(Runnable work) {
        try {work.run();}catch(RuntimeException unavailable){log.warn("Incident due work was deferred; inspect persistent delivery state");}
    }
}
