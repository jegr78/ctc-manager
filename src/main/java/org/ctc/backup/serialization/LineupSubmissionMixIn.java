package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class LineupSubmissionMixIn {
    @JsonIdentityReference(alwaysAsId = true)
    abstract Race getRace();
    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getTeam();
}
