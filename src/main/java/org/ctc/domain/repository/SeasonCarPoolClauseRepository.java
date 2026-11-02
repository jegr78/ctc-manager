package org.ctc.domain.repository;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.SeasonCarPoolClause;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeasonCarPoolClauseRepository extends JpaRepository<SeasonCarPoolClause, UUID> {
    @EntityGraph(attributePaths = {"entry"})
    @Query("select e from SeasonCarPoolClause e")
    List<SeasonCarPoolClause> findAllForBackup();
}
