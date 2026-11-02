package org.ctc.domain.repository;

import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.Season;

public interface SeasonLockingRepository {
    Optional<Season> findByIdForUpdate(UUID id);
}
