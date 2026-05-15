package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.PsnAlias;

/**
 * Externalised Jackson annotations for {@link PsnAlias}.
 *
 * <p>The {@code driver} foreign key is rendered as a UUID string only — the full
 * {@code Driver} row lives in {@code data/drivers.json}.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public abstract class PsnAliasMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract Driver getDriver();
}
