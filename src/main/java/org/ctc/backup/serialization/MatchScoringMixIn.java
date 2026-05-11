package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.MatchScoring;

/**
 * Externalised Jackson annotations for {@link MatchScoring}. Phase 73 EXPORT-04.
 *
 * <p>Trivial leaf entity — no foreign keys, no bidirectional collections.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class MatchScoringMixIn {
}
