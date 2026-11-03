package org.ctc.domain.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentManagementActor;
import org.ctc.domain.model.IncidentActorKind;
import org.ctc.domain.model.IncidentEventKind;
import org.ctc.domain.model.IncidentReportEvent;
import org.ctc.domain.model.IncidentReportWindow;
import org.ctc.domain.model.IncidentWindowState;
import org.ctc.domain.model.Race;
import org.ctc.domain.repository.IncidentReportEventRepository;
import org.ctc.domain.repository.IncidentReportRepository;
import org.ctc.domain.repository.IncidentReportWindowRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IncidentWindowService {
    public static final ZoneId DEADLINE_ZONE = ZoneId.of("Europe/Berlin");
    private final IncidentReportWindowRepository windows;
    private final IncidentReportEventRepository events;
    private final IncidentReportRepository reports;
    private final IncidentContextService contexts;
    private final PickWriteExecutor writes;
    private final EntityManager entityManager;
    private final Clock clock;
    private final org.springframework.context.ApplicationEventPublisher publisher;

    @Transactional(readOnly = true)
    public DeadlinePreview previewDeadline(UUID contextId) {
        var context = contexts.get(contextId);
        var window = windows.findByContextId(contextId).orElseThrow(() -> new BusinessRuleException("Incident window is unavailable"));
        if (window.getState() != IncidentWindowState.DRAFT) return preview(window.getPlannedStartLocal(), window.getPlannedStart());
        return previewUnbound(IncidentContextService.reference(context));
    }

    @Transactional(readOnly = true)
    public DeadlinePreview previewUnbound(org.ctc.domain.incidents.IncidentMatchRef reference) {
        var local = contexts.resolve(reference).races().stream().map(Race::getDateTime)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        return preview(local, resolvePlanned(local));
    }

    private DeadlinePreview preview(LocalDateTime local, Instant planned) {
        var deadline = planned == null ? null : planned.plus(Duration.ofHours(24));
        String problem = local == null ? "No planned race start is available; enter an explicit future deadline"
                : planned == null ? "The planned Berlin start is ambiguous or nonexistent; review an explicit future deadline" : "";
        return new DeadlinePreview(local, planned, deadline, deadline != null && !clock.instant().isBefore(deadline), problem);
    }

    private static Instant resolvePlanned(LocalDateTime local) {
        if (local == null) return null;
        var offsets = DEADLINE_ZONE.getRules().getValidOffsets(local);
        return offsets.size() == 1 ? local.toInstant(offsets.getFirst()) : null;
    }

    public Instant parseDeadline(LocalDateTime local, String offset) {
        if (local == null || offset == null || offset.isBlank()) throw new BusinessRuleException("Choose a deadline and its Berlin UTC offset");
        try {
            var chosen = ZoneOffset.of(offset);
            if (!DEADLINE_ZONE.getRules().getValidOffsets(local).contains(chosen)) {
                throw new BusinessRuleException("The chosen UTC offset is not valid for that Berlin deadline");
            }
            return local.toInstant(chosen).truncatedTo(ChronoUnit.MICROS);
        } catch (java.time.DateTimeException invalid) {
            throw new BusinessRuleException("Choose a valid Berlin UTC offset for the deadline");
        }
    }

    public UUID open(UUID contextId, Instant deadline, long expectedVersion, IncidentManagementActor actor) {
        return writes.execute(() -> {
            IncidentContextService.requireManager(actor);
            var window = lockForContext(contextId); window.requireVersion(expectedVersion);
            if (window.getState() != IncidentWindowState.DRAFT) throw new BusinessRuleException("A reporting window cannot be reopened");
            var planned = previewDeadline(contextId);
            var checkedAt = clock.instant(); var chosen = future(deadline, checkedAt);
            var now = checkedAt.truncatedTo(ChronoUnit.MICROS);
            window.open(chosen, planned.plannedStartLocal(), planned.plannedStart(), actor.id(), now);
            events.save(IncidentReportEvent.windowDecision(window, IncidentEventKind.OPENED, IncidentActorKind.MANAGEMENT,
                    actor.id(), "Reporting window explicitly opened", now, null));
            return window.getId();
        });
    }

    public void changeDeadline(UUID contextId, Instant deadline, long expectedVersion, String reason, IncidentManagementActor actor) {
        writes.execute(() -> {
            IncidentContextService.requireManager(actor);
            var window = lockForContext(contextId); window.requireVersion(expectedVersion);
            if (reason == null || reason.isBlank() || reason.length() > 1000) throw new BusinessRuleException("A deadline change reason is required, up to 1000 characters");
            var checkedAt = clock.instant(); window.requireOpenAt(checkedAt);
            var chosen = future(deadline, checkedAt); var previous = window.getDeadline();
            window.changeDeadline(chosen);
            events.save(IncidentReportEvent.windowDecision(window, IncidentEventKind.DEADLINE_CHANGED, IncidentActorKind.MANAGEMENT,
                    actor.id(), reason, checkedAt.truncatedTo(ChronoUnit.MICROS), previous));
            return null;
        });
    }

    public boolean closeDue(UUID windowId, Instant observedAt) {
        return writes.execute(() -> {
            var window = lock(windowId);
            // A scheduler observation cannot override the deadline or the server time after locking.
            var now = clock.instant();
            if (window.getState() != IncidentWindowState.OPEN || now.isBefore(window.getDeadline())) return false;
            var accepted = now.truncatedTo(ChronoUnit.MICROS);
            var finalReports = reports.findByWindowIdForUpdate(windowId);
            for (var report : finalReports) {
                entityManager.refresh(report, LockModeType.PESSIMISTIC_WRITE); report.freeze();
            }
            window.close(accepted);
            events.save(IncidentReportEvent.windowDecision(window, IncidentEventKind.CLOSED, IncidentActorKind.SYSTEM,
                    "incident-deadline", "Reporting deadline reached", accepted, window.getDeadline()));
            publisher.publishEvent(new org.ctc.domain.incidents.IncidentWindowClosed(window, finalReports));
            return true;
        });
    }

    IncidentReportWindow lock(UUID windowId) {
        var snapshot = windows.findById(windowId).orElseThrow(() -> new BusinessRuleException("Incident window is unavailable"));
        contexts.lock(snapshot.getContext().getId());
        var window = windows.findByIdForUpdate(windowId).orElseThrow(() -> new BusinessRuleException("Incident window is unavailable"));
        entityManager.refresh(window, LockModeType.PESSIMISTIC_WRITE);
        return window;
    }

    private IncidentReportWindow lockForContext(UUID contextId) {
        var window = windows.findByContextId(contextId).orElseThrow(() -> new BusinessRuleException("Incident window is unavailable"));
        return lock(window.getId());
    }

    @Transactional(readOnly = true)
    public IncidentReportWindow getForContext(UUID contextId) {
        return windows.findByContextId(contextId).orElseThrow(() -> new BusinessRuleException("Incident window is unavailable"));
    }

    private static Instant future(Instant deadline, Instant now) {
        var normalized = deadline == null ? null : deadline.truncatedTo(ChronoUnit.MICROS);
        if (normalized == null || !now.isBefore(normalized)) throw new BusinessRuleException("Choose an explicit future incident deadline");
        return normalized;
    }

    public record DeadlinePreview(LocalDateTime plannedStartLocal, Instant plannedStart, Instant defaultDeadline, boolean expired, String problem) { }
}
