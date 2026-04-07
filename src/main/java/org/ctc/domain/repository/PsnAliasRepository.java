package org.ctc.domain.repository;

import org.ctc.domain.model.PsnAlias;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PsnAliasRepository extends JpaRepository<PsnAlias, UUID> {

    Optional<PsnAlias> findByAliasIgnoreCase(String alias);

    boolean existsByAliasIgnoreCase(String alias);

    List<PsnAlias> findByDriverId(UUID driverId);
}
