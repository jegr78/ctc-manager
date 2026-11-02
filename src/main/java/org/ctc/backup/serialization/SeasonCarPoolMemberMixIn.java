package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.*;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class SeasonCarPoolMemberMixIn {
    @JsonIdentityReference(alwaysAsId = true)
    abstract SeasonCarPoolEntry getEntry();

    @JsonIdentityReference(alwaysAsId = true)
    abstract Car getCar();
}
