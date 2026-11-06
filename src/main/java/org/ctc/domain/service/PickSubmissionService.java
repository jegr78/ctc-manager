package org.ctc.domain.service;

import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.PickPayload;
import org.ctc.domain.model.MatchdayPick;
import org.ctc.domain.model.MatchdayPickRevision;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.picks.AuthorizedPickActor;
import org.ctc.domain.picks.SubmissionReceipt;
import org.ctc.domain.repository.MatchdayPickRepository;
import org.ctc.domain.repository.MatchdayPickRevisionRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PickSubmissionService {
    private final MatchdayPickRepository picks;
    private final MatchdayPickRevisionRepository revisions;
    private final PickTargetService targets;
    private final PickEligibilityService eligibility;
    private final PickDeadlinePolicy deadlines;
    private final PickCollectionStatus collection;
    private final PickWriteExecutor writes;
    private final Clock clock;

    public SubmissionReceipt submit(UUID raceId, long expectedVersion, UUID requestId, AuthorizedPickActor actor, PickPayload payload) {
        return writes.execute(() -> {
            var target = targets.lockForTeam(raceId, actor);
            if (requestId == null) throw new BusinessRuleException("A request identity is required");
            var existing = revisions.findByRequestId(requestId).orElse(null);
            if (existing != null) {
                if (!raceId.equals(existing.getPick().getRace().getId())
                        || !actor.userId().equals(existing.getSubmitterId())) {
                    throw new BusinessRuleException("This request belongs to another submission");
                }
                return existing.receipt();
            }
            if (payload == null) throw new BusinessRuleException("A complete pick is required");
            var pick = target.pick() != null ? target.pick()
                    : new MatchdayPick(target.race(), target.season(), target.race().getHomeTeam());
            pick.requireVersion(expectedVersion);
            eligibility.validate(target.race(), payload);
            var acceptedAt = clock.instant();
            var decision = deadlines.assessAt(new PickDeadlinePolicy.Context(pick.getState(),
                    target.season().isPicksCollectionEnabled(), collection.isAvailable(),
                    target.race().isPicksManaged() && targets.isEligible(target.race()), target.race().getMatchday().isPicksOpen(),
                    pick.isManualClosed(), org.ctc.domain.model.MatchdayPickWindowRules.deadline(target.race())), acceptedAt);
            if (!decision.editable()) throw new BusinessRuleException(decision.reason());
            int number = pick.submit();
            picks.save(pick);
            var revision = new MatchdayPickRevision(pick, number, payload, acceptedAt, actor.userId(), requestId, pick.getVersion());
            revisions.save(revision);
            return revision.receipt();
        });
    }
}
