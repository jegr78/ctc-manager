package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Playoff;
import org.ctc.domain.model.PlayoffSeed;
import org.ctc.domain.model.Team;

/**
 * Externalised Jackson annotations for {@link PlayoffSeed}. Phase 73 EXPORT-04.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class PlayoffSeedMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract Playoff getPlayoff();

    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getTeam();
}
