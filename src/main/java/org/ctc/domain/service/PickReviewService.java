package org.ctc.domain.service;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.MatchdayPick;
import org.ctc.domain.model.MatchdayPickDecision;
import org.ctc.domain.model.PickDecisionKind;
import org.ctc.domain.model.PickState;
import org.ctc.domain.model.PickSetupSnapshot;
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
        approve(raceId, expectedVersion, revisionNumber, actor, reason, false, null);
    }

    public void approve(UUID raceId, long expectedVersion, int revisionNumber, ManagementPickActor actor, String reason,
                        boolean replaceExisting, String expectedSettingsFingerprint) {
        decide(raceId, expectedVersion, actor, reason, PickDecisionKind.APPROVE, target -> {
            var pick = target.pick();
            if (pick.getLatestRevisionNumber() == null || pick.getLatestRevisionNumber() != revisionNumber) {
                throw new BusinessRuleException("Approval requires the exact latest pick revision");
            }
            var revision = revisions.findByPickIdAndRevisionNumber(pick.getId(), revisionNumber)
                    .orElseThrow(() -> new BusinessRuleException("The pick revision does not exist"));
            var existing = PickSetupSnapshot.from(target.race());
            if (expectedSettingsFingerprint != null && !existing.fingerprint().equals(expectedSettingsFingerprint)) {
                throw new BusinessRuleException("Internal settings changed; reload before approving");
            }
            if (existing.configured() && !existing.matches(revision.getPayload()) && !replaceExisting && !holdsApproved(pick, existing)) {
                throw new BusinessRuleException("Explicit replace consent is required for differing internal settings");
            }
            eligibility.validate(target.race(), revision.getPayload());
            settings.apply(target.race(), revision.getPayload());
            pick.approve(revisionNumber);
        });
    }

    /** The race still carries the previously approved revision, so replacing it needs no extra consent. */
    public boolean holdsApproved(MatchdayPick pick, PickSetupSnapshot existing) {
        return pick != null && pick.getApprovedRevisionNumber() != null
                && revisions.findByPickIdAndRevisionNumber(pick.getId(), pick.getApprovedRevisionNumber())
                        .map(approved -> existing.matches(approved.getPayload())).orElse(false);
    }

    private void decide(UUID raceId, long expectedVersion, ManagementPickActor actor, String reason,
                        PickDecisionKind kind, Consumer<PickTargetService.LockedTarget> action) {
        writes.execute(() -> {
            if (reason == null || reason.isBlank() || reason.length() > 1000) {
                throw new BusinessRuleException("A decision reason of at most 1000 characters is required");
            }
            var target = targets.lockForManagement(raceId, actor);
            if (!targets.isEligible(target.race())) {
                throw new BusinessRuleException("This race is not an eligible pick target");
            }
            var pick = target.pick() != null ? target.pick()
                    : new MatchdayPick(target.race(), target.season(), target.race().getHomeTeam());
            pick.requireVersion(expectedVersion);
            if (pick.getState() == PickState.APPROVED) {
                throw new BusinessRuleException("Approved picks are locked");
            }
            action.accept(new PickTargetService.LockedTarget(target.season(), target.race(), pick));
            picks.save(pick);
            decisions.save(new MatchdayPickDecision(pick, kind, pick.getVersion(), actor.userId(), reason, clock.instant(), null));
            return null;
        });
    }
}
