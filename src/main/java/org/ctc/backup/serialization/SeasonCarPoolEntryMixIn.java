package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.*;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "clauses", "members"})
public abstract class SeasonCarPoolEntryMixIn {
    @JsonIdentityReference(alwaysAsId = true)
    abstract Season getSeason();
}
