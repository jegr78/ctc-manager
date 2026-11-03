package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.PickPublicationAttempt;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class PickPublicationSnapshotMixIn {
    @JsonIdentityReference(alwaysAsId = true)
    abstract PickPublicationAttempt getAttempt();
}
