package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.*;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class IncidentMatchContextMixIn {
    @JsonIdentityReference(alwaysAsId = true)
    abstract Match getMatch();
    @JsonIdentityReference(alwaysAsId = true)
    abstract PlayoffMatchup getPlayoffMatchup();
    @JsonIdentityReference(alwaysAsId = true)
    abstract Season getSeason();
    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getHomeTeam();
    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getAwayTeam();
}
