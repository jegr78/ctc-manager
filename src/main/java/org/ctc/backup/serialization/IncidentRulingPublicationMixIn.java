package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.IncidentRulingRevision;
import org.ctc.domain.model.IncidentThreadPublication;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class IncidentRulingPublicationMixIn {
    @JsonIgnore abstract boolean isRestoreReviewed();
    @JsonIdentityReference(alwaysAsId = true) abstract IncidentRulingRevision getRevision();
    @JsonIdentityReference(alwaysAsId = true) abstract IncidentThreadPublication getThreadPublication();
}
