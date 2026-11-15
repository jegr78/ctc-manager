package org.ctc.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.LineupSubmissionRevision;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LineupSubmissionRevisionRepository extends JpaRepository<LineupSubmissionRevision, UUID> {
    @EntityGraph(attributePaths = {"submission", "submission.race", "submission.team"})
    Optional<LineupSubmissionRevision> findByRequestId(UUID requestId);

    Optional<LineupSubmissionRevision> findBySubmissionIdAndRevisionNumber(UUID submissionId, int revisionNumber);

    List<LineupSubmissionRevision> findBySubmissionIdOrderByRevisionNumberDesc(UUID submissionId);

    @EntityGraph(attributePaths = {"submission"})
    @Query("select r from LineupSubmissionRevision r")
    List<LineupSubmissionRevision> findAllForBackup();
}
