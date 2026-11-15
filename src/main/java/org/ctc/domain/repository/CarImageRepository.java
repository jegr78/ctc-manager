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

    /** Slot and stored path of every GT7 image of the car, as plain values outside the persistence context. */
    @Query("SELECT i.sourceKey AS slot, i.imagePath AS imagePath FROM CarImage i WHERE i.car.id = :carId AND i.source = org.ctc.domain.model.ImageSource.GT7")
    List<Gt7Slot> findGt7Slots(UUID carId);

    interface Gt7Slot {
        String getSlot();
        String getImagePath();
    }

    @EntityGraph(attributePaths = "car")
    @Query("SELECT e FROM CarImage e")
    List<CarImage> findAllForBackup();
}
