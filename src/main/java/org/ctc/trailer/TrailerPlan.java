package org.ctc.trailer;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.ctc.admin.dto.MatchdayGraphicData;

public record TrailerPlan(TrailerScope scope, String title, String seasonLabel, MatchdayGraphicData overview,
                          List<TrailerRaceScene> races, List<TrailerChecklistItem> blockers, Set<UUID> carsNeedingCutout) {

    public boolean ready() {
        return blockers.isEmpty();
    }
}
