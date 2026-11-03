package org.ctc.domain.service;

import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.PickPublicationOrigin;
import org.ctc.domain.model.PickDecisionKind;
import org.ctc.domain.model.PickPayload;
import org.ctc.domain.model.PickSetupSnapshot;
import org.ctc.domain.model.PickState;
import org.ctc.domain.model.Race;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.repository.MatchdayPickRepository;
import org.ctc.domain.repository.MatchdayPickDecisionRepository;
import org.ctc.domain.repository.MatchdayPickRevisionRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PickPublicationAuthorization {
    private final MatchdayPickRepository picks;
    private final MatchdayPickDecisionRepository decisions;
    private final MatchdayPickRevisionRepository revisions;

    public Source authorize(Race race) {
        if (!race.isPicksManaged()) return new Source(PickPublicationOrigin.LEGACY, 0);
        var pick = picks.findByRaceId(race.getId()).orElseThrow(PickPublicationAuthorization::unavailable);
        if (pick.getState() != PickState.APPROVED || !pick.matchesTarget(race)) throw unavailable();
        var decision = decisions.findByPickIdOrderByAggregateVersionDesc(pick.getId()).stream()
                .filter(row -> row.getAggregateVersion() == pick.getVersion()).findFirst()
                .orElseThrow(PickPublicationAuthorization::unavailable);
        PickPayload payload;
        PickPublicationOrigin origin;
        if (decision.getKind() == PickDecisionKind.CORRECTION) {
            origin = PickPublicationOrigin.CORRECTION;
            payload = decision.getCorrectionPayload();
        } else if (decision.getKind() == PickDecisionKind.APPROVE && pick.getApprovedRevisionNumber() != null) {
            origin = PickPublicationOrigin.APPROVAL;
            payload = revisions.findByPickIdAndRevisionNumber(pick.getId(), pick.getApprovedRevisionNumber())
                    .orElseThrow(PickPublicationAuthorization::unavailable).getPayload();
        } else throw unavailable();
        if (payload == null || !PickSetupSnapshot.from(race).matches(payload)) throw unavailable();
        return new Source(origin, pick.getVersion());
    }

    private static BusinessRuleException unavailable() {
        return new BusinessRuleException("Publication requires the exact approved settings or a recorded management correction");
    }
    public record Source(PickPublicationOrigin origin, long version) { }
}
