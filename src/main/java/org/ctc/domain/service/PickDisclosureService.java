package org.ctc.domain.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.picks.PublicPickProjection;
import org.ctc.domain.repository.RaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PickDisclosureService {
    private final RaceRepository races;
    private final PickPublicationService publications;

    @Transactional(readOnly = true)
    public PublicPickProjection forPublic(UUID raceId) {
        var race = races.findById(raceId).orElseThrow(() -> new EntityNotFoundException("Race", raceId));
        return publications.publicProjection(race);
    }
}
