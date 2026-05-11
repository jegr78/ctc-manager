package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;

/**
 * Externalised Jackson annotations for {@link SeasonTeam}. Phase 73 EXPORT-04.
 *
 * <p>The self-typed {@code successor} foreign key is rendered as a UUID reference. Intra-file
 * forward reference resolved by Jackson via the same {@code @JsonIdentityInfo} declaration.
 *
 * <p>Six convenience getters are suppressed:
 * {@code effectivePrimaryColor}, {@code effectiveSecondaryColor}, {@code effectiveAccentColor},
 * {@code effectiveLogoUrl}, {@code replaced} (from {@code isReplaced()}),
 * {@code activeSeasonTeam}.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler",
        "effectivePrimaryColor", "effectiveSecondaryColor",
        "effectiveAccentColor", "effectiveLogoUrl",
        "replaced", "activeSeasonTeam"})
public abstract class SeasonTeamMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract Season getSeason();

    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getTeam();

    @JsonIdentityReference(alwaysAsId = true)
    abstract SeasonTeam getSuccessor();
}
