package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Team;

/**
 * Externalised Jackson annotations for {@link Team}.
 *
 * <p>Required because:
 * <ul>
 *   <li>{@code Team.parentTeam} is a self-FK — needs ID-only reference to keep the JSON
 *       acyclic. Intra-file forward reference resolved by Jackson via the same
 *       {@code @JsonIdentityInfo} declaration on this MixIn.</li>
 *   <li>{@code Team.subTeams} is the inverse of {@code parentTeam} — ignored so that parent
 *       teams do not embed their entire subtree.</li>
 *   <li>{@code Team.seasonDrivers} is a back-reference owned by {@code SeasonDriver.team} —
 *       ignored.</li>
 *   <li>Convenience methods ({@code isSubTeam} → property {@code subTeam},
 *       {@code hasSubTeams} → already covered by ignoring {@code subTeams},
 *       {@code getParentOrSelf} → property {@code parentOrSelf}) are computed, not
 *       persisted — ignored to avoid duplicate JSON keys and lazy-init triggers.</li>
 * </ul>
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler",
        "subTeams", "seasonDrivers",
        "subTeam", "parentOrSelf"})
public abstract class TeamMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getParentTeam();
}
