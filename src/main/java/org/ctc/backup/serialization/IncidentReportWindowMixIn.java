package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.*;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class IncidentReportWindowMixIn {
    @JsonIgnore abstract boolean isDeliveryPaused();
    @JsonIgnore abstract String getDeliveryResumedBy();
    @JsonIgnore abstract String getDeliveryResumeReason();
    @JsonIgnore abstract java.time.Instant getDeliveryResumedAt();
    @JsonIdentityReference(alwaysAsId = true)
    abstract IncidentMatchContext getContext();
}
