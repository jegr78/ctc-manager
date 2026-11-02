package org.ctc.domain.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.PickPayload;
import org.ctc.domain.repository.MatchdayPickRevisionRepository;
import org.ctc.domain.repository.MatchdayPickDecisionRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PickHistoryReferences {
    private final MatchdayPickRevisionRepository revisions;
    private final MatchdayPickDecisionRepository decisions;

    public boolean hasSelection(UUID seasonId) { return !payloads(seasonId).isEmpty(); }

    public void requireCarsRetained(UUID seasonId, Collection<UUID> removed) {
        if (payloads(seasonId).stream().anyMatch(payload -> removed.contains(payload.carId()))) {
            throw new BusinessRuleException("Cannot remove a car referenced by confidential pick history");
        }
    }

    public void requireTracksRetained(UUID seasonId, Collection<UUID> removed) {
        if (payloads(seasonId).stream().anyMatch(payload -> removed.contains(payload.trackId()))) {
            throw new BusinessRuleException("Cannot remove a track referenced by confidential pick history");
        }
    }

    private List<PickPayload> payloads(UUID seasonId) {
        var result = new ArrayList<PickPayload>();
        revisions.findHistoryBySeasonForUpdate(seasonId).forEach(revision -> result.add(revision.getPayload()));
        decisions.findHistoryBySeasonForUpdate(seasonId).stream().filter(decision -> decision.getCorrectionPayload() != null)
                .forEach(decision -> result.add(decision.getCorrectionPayload()));
        return result;
    }
}
