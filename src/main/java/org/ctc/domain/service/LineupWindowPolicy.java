package org.ctc.domain.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.LineupWindowKind;
import org.springframework.stereotype.Service;

/**
 * Lineup intake for one leg opens with its first confirmed settings publication or a management decision, and teams
 * may submit until one hour before the leg's Europe/Berlin start.
 */
@Service
@RequiredArgsConstructor
public class LineupWindowPolicy {
    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    public static final Duration LEAD = Duration.ofHours(1);
    private final Clock clock;

    public Decision assess(Context context) { return assessAt(context, clock.instant()); }

    public Decision assessAt(Context context, Instant at) {
        if (!context.targetValid()) return new Decision(false, "The leg is not open for lineups", null);
        Instant deadline;
        try {
            deadline = deadline(context.start());
        } catch (IllegalArgumentException ambiguous) {
            return new Decision(false, "The start time requires management review", null);
        }
        if (context.override() == LineupWindowKind.CLOSE) return new Decision(false, "Management closed lineups for this leg", deadline);
        if (context.override() == null && !context.settingsPublished()) {
            return new Decision(false, "Opens when the settings for this leg are published", deadline);
        }
        if (deadline != null && !at.isBefore(deadline)) return new Decision(false, "The lineup deadline has passed", deadline);
        return new Decision(true, deadline == null ? "Open, no start time scheduled" : "Open until the deadline", deadline);
    }

    /** One hour before the start, or null without a start time; ambiguous or nonexistent local times are rejected. */
    public static Instant deadline(LocalDateTime start) {
        if (start == null) return null;
        var offsets = ZONE.getRules().getValidOffsets(start);
        if (offsets.size() != 1) throw new IllegalArgumentException("The start time has no unambiguous Europe/Berlin offset");
        return start.toInstant(offsets.getFirst()).minus(LEAD);
    }

    public record Context(boolean targetValid, boolean settingsPublished, LineupWindowKind override, LocalDateTime start) { }
    public record Decision(boolean editable, String reason, Instant deadline) { }
}
