package org.ctc.domain.repository;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.SeasonCarPoolMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SeasonCarPoolMemberRepository extends JpaRepository<SeasonCarPoolMember, UUID> {
    @EntityGraph(attributePaths = {"entry", "car"})
    @Query("select e from SeasonCarPoolMember e")
    List<SeasonCarPoolMember> findAllForBackup();

    boolean existsByCarId(UUID carId);
}
