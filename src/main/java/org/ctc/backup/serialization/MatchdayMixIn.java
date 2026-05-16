package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;

/**
 * Externalised Jackson annotations for {@link Matchday}.
 *
 * <p>Suppresses the two child collections ({@code matches}, {@code races}) and the computed
 * {@code getSeason()} convenience getter (derived via {@code phase.getSeason()}).
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler",
        "matches", "races", "season"})
public abstract class MatchdayMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract SeasonPhase getPhase();

    @JsonIdentityReference(alwaysAsId = true)
    abstract SeasonPhaseGroup getGroup();
}
