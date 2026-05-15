package org.ctc.domain.repository;

import org.ctc.domain.model.Car;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarRepository extends JpaRepository<Car, UUID> {
	List<Car> findAllByOrderByManufacturerAscNameAsc();

	boolean existsByManufacturerAndName(String manufacturer, String name);

	Optional<Car> findByGt7Id(String gt7Id);

	boolean existsByGt7Id(String gt7Id);

	/**
	 * Full-table finder used by {@code BackupExportService}.
	 *
	 * <p>{@code Car} has no {@code @ManyToOne} associations the export aggregate touches;
	 * the empty {@code @EntityGraph} keeps the contract uniform across all 24 backup
	 * finders (verified reflectively by {@code BackupRepositoryEntityGraphIT}).
	 *
	 * <p>The explicit JPQL prevents the Spring Data method-name parser from
	 * mis-interpreting {@code ForBackup} as a property filter.
	 */
	@EntityGraph(attributePaths = {})
	@Query("SELECT e FROM Car e")
	List<Car> findAllForBackup();
}
