package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.IncidentRuling;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class IncidentRulingRevisionMixIn {
    @JsonIdentityReference(alwaysAsId = true) abstract IncidentRuling getRuling();
}
