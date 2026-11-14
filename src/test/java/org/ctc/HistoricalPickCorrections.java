package org.ctc;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.MatchdayPickDecision;
import org.ctc.domain.model.PickDecisionKind;
import org.ctc.domain.model.PickPayload;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.MatchdayPickDecisionRepository;
import org.ctc.domain.repository.MatchdayPickRepository;
import org.ctc.domain.service.PickTargetService;
import org.ctc.domain.service.PickWriteExecutor;
import org.ctc.domain.service.RaceSettingsApplicationService;
import org.springframework.stereotype.Component;

/** Writes a management correction the way earlier releases did, so tests can build historical archives. */
@Component
@RequiredArgsConstructor
public class HistoricalPickCorrections {
    private final PickWriteExecutor writes;
    private final PickTargetService targets;
    private final MatchdayPickRepository picks;
    private final MatchdayPickDecisionRepository decisions;
    private final RaceSettingsApplicationService settings;
    private final Clock clock;

    public void record(UUID raceId, long expectedVersion, ManagementPickActor actor, String reason, PickPayload payload) {
        writes.execute(() -> {
            var target = targets.lockForManagement(raceId, actor);
            var pick = target.pick();
            pick.requireVersion(expectedVersion);
            settings.apply(target.race(), payload);
            pick.correct();
            picks.save(pick);
            decisions.save(new MatchdayPickDecision(pick, PickDecisionKind.CORRECTION, pick.getVersion(), actor.userId(), reason, clock.instant(), payload));
            return null;
        });
    }
}
