package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.*;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class SeasonCarPoolClauseMixIn {
    @JsonIdentityReference(alwaysAsId = true)
    abstract SeasonCarPoolEntry getEntry();
}
