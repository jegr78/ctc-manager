package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class TeamPickChannelBindingMixIn {
    @JsonIdentityReference(alwaysAsId = true)
    abstract Season getSeason();
    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getTeam();
}
