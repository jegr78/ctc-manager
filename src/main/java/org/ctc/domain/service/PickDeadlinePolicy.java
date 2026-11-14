package org.ctc.domain.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PickDeadlinePolicy {
    private static final ZoneId DEADLINE_ZONE = ZoneId.of("Europe/Berlin");
    private final Clock clock;

    public Decision assess(Context context) {
        return assessAt(context, clock.instant());
    }

    Decision assessAt(Context context, Instant acceptedAt) {
        if (!context.targetValid()) {
            return new Decision(false, "The target is unavailable");
        }
        if (!context.matchdayOpen()) {
            return new Decision(false, "Matchday picks are closed");
        }
        if (context.manualClosed()) {
            return new Decision(false, "Collection was manually closed");
        }
        if (context.deadline() == null) {
            return new Decision(true, "Matchday was opened without a pick deadline");
        }
        Instant cutoff;
        try {
            cutoff = resolveDeadline(context.deadline());
        } catch (BusinessRuleException exception) {
            return new Decision(false, "The deadline requires management review");
        }
        return acceptedAt.isBefore(cutoff)
                ? new Decision(true, "Before the deadline")
                : new Decision(false, "The deadline has passed");
    }

    public Instant resolveDeadline(LocalDateTime deadline) {
        var offsets = DEADLINE_ZONE.getRules().getValidOffsets(deadline);
        if (offsets.size() != 1) {
            throw new BusinessRuleException("Deadline must have one unambiguous offset in Europe/Berlin");
        }
        return deadline.toInstant(offsets.getFirst());
    }

    public record Context(boolean targetValid, boolean matchdayOpen, boolean manualClosed, LocalDateTime deadline) {}
    public record Decision(boolean editable, String reason) {}
}
