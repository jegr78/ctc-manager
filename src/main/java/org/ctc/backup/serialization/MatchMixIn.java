package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Team;

/**
 * Externalised Jackson annotations for {@link Match}. Phase 73 EXPORT-04.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "races"})
public abstract class MatchMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract Matchday getMatchday();

    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getHomeTeam();

    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getAwayTeam();
}
