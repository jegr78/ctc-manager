package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.RaceScoring;

/**
 * Externalised Jackson annotations for {@link RaceScoring}.
 *
 * <p>Trivial leaf entity — {@code racePoints} and {@code qualiPoints} are CSV strings stored
 * directly. Suppresses the four computed getters
 * ({@code racePointsArray}, {@code qualiPointsArray}, {@code canParse}, {@code valid}) so the
 * JSON contains only persisted column data.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler",
        "racePointsArray", "qualiPointsArray", "canParse", "valid"})
public abstract class RaceScoringMixIn {
}
