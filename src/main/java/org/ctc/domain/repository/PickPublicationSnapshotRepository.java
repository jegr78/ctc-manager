package org.ctc.domain.repository;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.PickPublicationSnapshot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PickPublicationSnapshotRepository extends JpaRepository<PickPublicationSnapshot, UUID> {
    @EntityGraph(attributePaths = {"attempt"})
    List<PickPublicationSnapshot> findByAttemptRaceIdOrderByAttemptContentVersionDescConfirmedAtDesc(UUID raceId);

    @EntityGraph(attributePaths = {"attempt", "attempt.race"})
    @Query("select s from PickPublicationSnapshot s")
    List<PickPublicationSnapshot> findAllForBackup();
}
