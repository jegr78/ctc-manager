package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.MatchdayPick;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class MatchdayPickRevisionMixIn {
    @JsonIdentityReference(alwaysAsId = true)
    abstract MatchdayPick getPick();
}
