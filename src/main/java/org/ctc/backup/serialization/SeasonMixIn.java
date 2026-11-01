package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import java.util.List;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Track;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler",
		"phases", "seasonDrivers", "seasonTeams", "carPoolEntries",
		"displayLabel", "teams", "matchdays", "activeTeams", "eligibleTeams"})
public abstract class SeasonMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract List<Car> getCars();

    @JsonIdentityReference(alwaysAsId = true)
    abstract List<Track> getTracks();
}
