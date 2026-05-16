package org.ctc.domain.repository;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.RaceAttachment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RaceAttachmentRepository extends JpaRepository<RaceAttachment, UUID> {

	/**
	 * Full-table finder used by {@code BackupExportService}.
	 *
	 * <p>Eager-fetches the {@code race} {@code @ManyToOne} association.
	 */
	@EntityGraph(attributePaths = {"race"})
	@Query("SELECT e FROM RaceAttachment e")
	List<RaceAttachment> findAllForBackup();
}
