package org.ctc.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.SiteSlug;
import org.ctc.domain.model.SiteSlugKind;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SiteSlugRepository extends JpaRepository<SiteSlug, UUID> {

	List<SiteSlug> findByKind(SiteSlugKind kind);

	Optional<SiteSlug> findFirstByKindAndEntityId(SiteSlugKind kind, UUID entityId);

	@EntityGraph(attributePaths = {})
	@Query("SELECT e FROM SiteSlug e")
	List<SiteSlug> findAllForBackup();
}
