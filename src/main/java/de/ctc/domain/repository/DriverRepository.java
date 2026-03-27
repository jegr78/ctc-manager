package de.ctc.domain.repository;

import de.ctc.domain.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    Optional<Driver> findByPsnId(String psnId);

    Optional<Driver> findByPsnIdIgnoreCase(String psnId);

    List<Driver> findByActiveTrue();
}
