package org.ctc.domain.service;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.MatchdayPick;
import org.ctc.domain.model.MatchdayPickDecision;
import org.ctc.domain.model.PickDecisionKind;
import org.ctc.domain.model.PickPayload;
import org.ctc.domain.model.PickState;
import org.ctc.domain.picks.ManagementPickActor;
import org.ctc.domain.repository.MatchdayPickDecisionRepository;
import org.ctc.domain.repository.MatchdayPickRepository;
import org.ctc.domain.repository.MatchdayPickRevisionRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PickReviewService {
    private final MatchdayPickRepository picks;
    private final MatchdayPickRevisionRepository revisions;
    private final MatchdayPickDecisionRepository decisions;
    private final PickTargetService targets;
    private final PickEligibilityService eligibility;
    private final RaceSettingsApplicationService settings;
    private final PickWriteExecutor writes;
    private final Clock clock;

    public void openMissing(UUID raceId, long expectedVersion, ManagementPickActor actor, String reason) {
        decide(raceId, expectedVersion, actor, reason, PickDecisionKind.OPEN, target -> target.pick().openMissing());
    }

    public void returnForCorrection(UUID raceId, long expectedVersion, ManagementPickActor actor, String reason) {
        decide(raceId, expectedVersion, actor, reason, PickDecisionKind.RETURN, target -> target.pick().returnForCorrection());
    }

    public void close(UUID raceId, long expectedVersion, ManagementPickActor actor, String reason) {
        decide(raceId, expectedVersion, actor, reason, PickDecisionKind.CLOSE, target -> target.pick().close());
    }

    public void approve(UUID raceId, long expectedVersion, int revisionNumber, ManagementPickActor actor, String reason) {
        decide(raceId, expectedVersion, actor, reason, PickDecisionKind.APPROVE, target -> {
            var pick = target.pick();
            if (pick.getLatestRevisionNumber() == null || pick.getLatestRevisionNumber() != revisionNumber) {
                throw new BusinessRuleException("Approval requires the exact latest pick revision");
            }
            var revision = revisions.findByPickIdAndRevisionNumber(pick.getId(), revisionNumber)
                    .orElseThrow(() -> new BusinessRuleException("The pick revision does not exist"));
            eligibility.validate(target.race(), revision.getPayload());
            settings.apply(target.race(), revision.getPayload());
            pick.approve(revisionNumber);
        });
    }

    public void correct(UUID raceId, long expectedVersion, ManagementPickActor actor, String reason,
                        PickPayload payload) {
        if (payload == null) throw new BusinessRuleException("A complete correction payload is required");
        decide(raceId, expectedVersion, actor, reason, PickDecisionKind.CORRECTION, payload, target -> {
            if (target.pick().getState() != PickState.APPROVED) {
                throw new BusinessRuleException("Only approved settings can be corrected");
            }
            eligibility.validate(target.race(), payload);
            settings.apply(target.race(), payload);
            target.pick().correct();
        });
    }

    private void decide(UUID raceId, long expectedVersion, ManagementPickActor actor, String reason,
                        PickDecisionKind kind, Consumer<PickTargetService.LockedTarget> action) {
        decide(raceId, expectedVersion, actor, reason, kind, null, action);
    }

    private void decide(UUID raceId, long expectedVersion, ManagementPickActor actor, String reason,
                        PickDecisionKind kind, PickPayload correctionPayload, Consumer<PickTargetService.LockedTarget> action) {
        writes.execute(() -> {
            if (reason == null || reason.isBlank() || reason.length() > 1000) {
                throw new BusinessRuleException("A decision reason of at most 1000 characters is required");
            }
            var target = targets.lockForManagement(raceId, actor);
            if (kind != PickDecisionKind.CORRECTION && !targets.isEligible(target.race())) {
                throw new BusinessRuleException("This race is not an eligible pick target");
            }
            var pick = target.pick() != null ? target.pick()
                    : new MatchdayPick(target.race(), target.season(), target.race().getHomeTeam());
            pick.requireVersion(expectedVersion);
            if (kind != PickDecisionKind.CORRECTION && pick.getState() == PickState.APPROVED) {
                throw new BusinessRuleException("Approved picks are locked");
            }
            action.accept(new PickTargetService.LockedTarget(target.season(), target.race(), pick));
            picks.save(pick);
            decisions.save(new MatchdayPickDecision(pick, kind, pick.getVersion(), actor.userId(), reason, clock.instant(), correctionPayload));
            return null;
        });
    }
}
