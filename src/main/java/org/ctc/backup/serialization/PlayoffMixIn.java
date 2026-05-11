package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Playoff;
import org.ctc.domain.model.SeasonPhase;

/**
 * Externalised Jackson annotations for {@link Playoff}. Phase 73 EXPORT-04.
 *
 * <p>Suppresses both child collections ({@code rounds}, {@code seeds}) and the computed
 * {@code getSeason()} convenience getter ({@code Playoff.getSeason()} derives via
 * {@code phase.getSeason()}).
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler",
        "rounds", "seeds", "season"})
public abstract class PlayoffMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract SeasonPhase getPhase();
}
