package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.*;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class MatchdayPickMixIn {
    @JsonIdentityReference(alwaysAsId = true)
    abstract Race getRace();
    @JsonIdentityReference(alwaysAsId = true)
    abstract Season getSeason();
    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getTeam();
    @JsonIdentityReference(alwaysAsId = true)
    abstract Matchday getOriginalMatchday();
    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getOriginalAwayTeam();
}
