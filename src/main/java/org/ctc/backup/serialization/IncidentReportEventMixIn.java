package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.*;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class IncidentReportEventMixIn {
    @JsonIdentityReference(alwaysAsId = true)
    abstract IncidentReportWindow getWindow();
    @JsonIdentityReference(alwaysAsId = true)
    abstract IncidentReport getReport();
}
