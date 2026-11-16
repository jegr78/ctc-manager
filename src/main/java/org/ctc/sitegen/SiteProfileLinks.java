package org.ctc.sitegen;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.SiteSlug;
import org.ctc.domain.model.SiteSlugKind;
import org.ctc.domain.repository.SiteSlugRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Absolute links to generated season profile pages, built from the stored slugs without allocating new ones.
 * Every method returns null when no public base URL is configured or the entity has no stored slug.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteProfileLinks {
    private final SiteProperties properties;
    private final SiteSlugRepository slugs;
    private final SiteSlugger slugger;

    public String team(UUID teamId, String seasonLabel) { return link(SiteSlugKind.TEAM, "team", teamId, seasonLabel); }

    public String driver(UUID driverId, String seasonLabel) { return link(SiteSlugKind.DRIVER, "driver", driverId, seasonLabel); }

    /** The alltime career page of a team, {@code team/<slug>.html}. */
    public String teamCareer(UUID teamId) { return link(SiteSlugKind.TEAM, "team", teamId, ""); }

    /** The alltime career page of a driver, {@code driver/<slug>.html}. */
    public String driverCareer(UUID driverId) { return link(SiteSlugKind.DRIVER, "driver", driverId, ""); }

    private String link(SiteSlugKind kind, String folder, UUID entityId, String seasonLabel) {
        String base = properties.getPublicBaseUrl();
        if (base == null || base.isBlank() || entityId == null || seasonLabel == null) return null;
        String season = seasonLabel.isEmpty() ? "" : "season/" + slugger.slugify(seasonLabel) + "/";
        return slugs.findFirstByKindAndEntityId(kind, entityId).map(SiteSlug::getSlug)
                .map(slug -> (base.endsWith("/") ? base : base + "/") + season + folder + "/" + slug + ".html")
                .orElse(null);
    }
}
