package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.LineupSubmission;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class LineupSubmissionRevisionMixIn {
    @JsonIdentityReference(alwaysAsId = true)
    abstract LineupSubmission getSubmission();
}
