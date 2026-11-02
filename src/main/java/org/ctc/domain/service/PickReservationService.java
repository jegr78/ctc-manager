package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.repository.MatchdayPickRepository;
import org.ctc.domain.repository.MatchdayPickRevisionRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import org.ctc.domain.model.PickState;
import org.ctc.domain.exception.BusinessRuleException;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PickReservationService {
    private final MatchdayPickRepository picks;
    private final MatchdayPickRevisionRepository revisions;
    private final EntityManager entityManager;
    @Transactional
    public List<Reservation> pending(UUID seasonId, boolean currentRead) {
        var rows = currentRead ? picks.findBySeasonIdForUpdate(seasonId) : picks.findBySeasonId(seasonId);
        var result = new ArrayList<Reservation>();
        for (var pick : rows) {
            if (currentRead) entityManager.refresh(pick, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (pick.getState() == PickState.APPROVED || pick.getLatestRevisionNumber() == null) continue;
            var latest = currentRead
                    ? revisions.findByPickIdAndRevisionNumber(pick.getId(), pick.getLatestRevisionNumber())
                    : revisions.findSnapshotByPickIdAndRevisionNumber(pick.getId(), pick.getLatestRevisionNumber());
            var payload = latest.orElseThrow(() -> new BusinessRuleException("Pending pick history is incomplete")).getPayload();
            result.add(new Reservation(pick.getRace().getId(), pick.getTeam().getId(), payload.carId(), payload.carPoolEntryId(), payload.trackId()));
        }
        return List.copyOf(result);
    }
    public record Reservation(UUID raceId, UUID teamId, UUID carId, UUID entryId, UUID trackId) { }
}
