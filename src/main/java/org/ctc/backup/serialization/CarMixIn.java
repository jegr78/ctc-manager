package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Car;

/**
 * Externalised Jackson annotations for {@link Car}. Phase 73 EXPORT-04.
 *
 * <p>Trivial leaf entity — no foreign keys, no bidirectional collections. The MixIn provides
 * {@code @JsonIdentityInfo} so that any other entity referencing this {@code Car} via
 * {@code @JsonIdentityReference(alwaysAsId=true)} (e.g. {@code Season.cars},
 * {@code Race.car}) can emit the UUID only and avoid duplicating the row inline.
 *
 * <p>{@code displayName} is a computed convenience getter (manufacturer + name) — suppressed
 * to keep the JSON shape aligned with persisted columns only.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "displayName"})
public abstract class CarMixIn {
}
