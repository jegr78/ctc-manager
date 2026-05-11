package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceSettings;

/**
 * Externalised Jackson annotations for {@link RaceSettings}. Phase 73 EXPORT-04.
 *
 * <p>Suppresses the {@code isComplete()} convenience getter (property {@code complete}).
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "complete"})
public abstract class RaceSettingsMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract Race getRace();
}
