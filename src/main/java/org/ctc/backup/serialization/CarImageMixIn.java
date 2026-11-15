package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.Car;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "kindName", "slot"})
public abstract class CarImageMixIn {
    @JsonIdentityReference(alwaysAsId = true)
    abstract Car getCar();
}
