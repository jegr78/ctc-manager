package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.MatchScoring;
import org.ctc.domain.model.RaceScoring;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;

/**
 * Externalised Jackson annotations for {@link SeasonPhase}. Phase 73 EXPORT-04.
 *
 * <p>All three {@code @ManyToOne} foreign keys ({@code season}, {@code raceScoring},
 * {@code matchScoring}) are rendered as UUID references. The two child collections
 * ({@code groups}, {@code matchdays}) are ignored — children own the FK back.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "groups", "matchdays"})
public abstract class SeasonPhaseMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract Season getSeason();

    @JsonIdentityReference(alwaysAsId = true)
    abstract RaceScoring getRaceScoring();

    @JsonIdentityReference(alwaysAsId = true)
    abstract MatchScoring getMatchScoring();
}
