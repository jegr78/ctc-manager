package org.ctc.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.CarImage;
import org.ctc.domain.model.CarImageKind;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CarImageRepository extends JpaRepository<CarImage, UUID> {
    List<CarImage> findByCarIdOrderBySortIndexAscIdAsc(UUID carId);

    Optional<CarImage> findFirstByCarIdAndKindOrderBySortIndexAscIdAsc(UUID carId, CarImageKind kind);

    @EntityGraph(attributePaths = "car")
    @Query("SELECT e FROM CarImage e")
    List<CarImage> findAllForBackup();
}
