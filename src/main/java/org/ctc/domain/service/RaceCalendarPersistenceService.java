package org.ctc.domain.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.repository.RaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists the external event ID against current race state after the calendar call returns. */
@Service
@RequiredArgsConstructor
public class RaceCalendarPersistenceService {
    private final RaceRepository races;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveCreatedEvent(UUID raceId, String eventId) {
        var race = races.findByIdForSelectionUpdate(raceId)
                .orElseThrow(() -> new EntityNotFoundException("Race", raceId));
        race.setCalendarEventId(eventId);
        races.save(race);
    }
}
