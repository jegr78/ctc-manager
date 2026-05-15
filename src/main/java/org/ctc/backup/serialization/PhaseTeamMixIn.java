package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.model.Team;

/**
 * Externalised Jackson annotations for {@link PhaseTeam}.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class PhaseTeamMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract SeasonPhase getPhase();

    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getTeam();

    @JsonIdentityReference(alwaysAsId = true)
    abstract SeasonPhaseGroup getGroup();
}
