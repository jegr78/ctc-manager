package org.ctc.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.Race;

public interface RaceSelectionLockingRepository {
    List<Race> findBySeasonForSelectionUpdate(UUID seasonId);
    Optional<Race> findByIdForSelectionUpdate(UUID raceId);
}
