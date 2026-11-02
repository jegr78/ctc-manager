package org.ctc.domain.repository;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.SeasonCarPoolEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeasonCarPoolEntryRepository extends JpaRepository<SeasonCarPoolEntry, UUID> {
    @EntityGraph(attributePaths = {"season"})
    @Query("select e from SeasonCarPoolEntry e")
    List<SeasonCarPoolEntry> findAllForBackup();
}
