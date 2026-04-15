package org.ctc.domain.repository;

import org.ctc.domain.model.Car;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarRepository extends JpaRepository<Car, UUID> {
	List<Car> findAllByOrderByManufacturerAscNameAsc();

	boolean existsByManufacturerAndName(String manufacturer, String name);

	Optional<Car> findByGt7Id(String gt7Id);

	boolean existsByGt7Id(String gt7Id);
}
