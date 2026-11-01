package org.ctc.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.Car;

public interface CarLockingRepository {
    List<Car> findAllByGt7IdForUpdate(String gt7Id);
    Optional<Car> findByIdForUpdate(UUID id);
}
