package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.Track;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "kindName", "slot"})
public abstract class TrackImageMixIn {
    @JsonIdentityReference(alwaysAsId = true)
    abstract Track getTrack();
}
