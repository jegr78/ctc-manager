package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonDriver;
import org.ctc.domain.model.Team;

/**
 * Externalised Jackson annotations for {@link SeasonDriver}.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class SeasonDriverMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract Season getSeason();

    @JsonIdentityReference(alwaysAsId = true)
    abstract Driver getDriver();

    @JsonIdentityReference(alwaysAsId = true)
    abstract Team getTeam();
}
