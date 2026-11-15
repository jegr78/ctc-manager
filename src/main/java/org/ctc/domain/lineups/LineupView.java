package org.ctc.domain.lineups;

import java.util.List;
import org.ctc.domain.model.LineupPayload;

/** What a team member may see for one target: the committed lineup, the organisation roster and the window. */
public record LineupView(LineupTarget target, int lineupSize, LineupPayload current, boolean applied,
                         List<LineupChoice> choices, List<String> warnings) {
    public LineupView { choices = List.copyOf(choices); warnings = List.copyOf(warnings); }
}
