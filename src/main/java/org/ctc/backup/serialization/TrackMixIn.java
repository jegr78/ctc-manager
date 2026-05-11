package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Track;

/**
 * Externalised Jackson annotations for {@link Track}. Phase 73 EXPORT-04.
 *
 * <p>Trivial leaf entity — no foreign keys, no bidirectional collections. The MixIn provides
 * {@code @JsonIdentityInfo} so that any other entity referencing this {@code Track} via
 * {@code @JsonIdentityReference(alwaysAsId=true)} (e.g. {@code Season.tracks},
 * {@code Race.track}) can emit the UUID only and avoid duplicating the row inline.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class TrackMixIn {
}
