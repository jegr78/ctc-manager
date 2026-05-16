package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Driver;

/**
 * Externalised Jackson annotations for {@link Driver}.
 *
 * <p>Suppresses three bidirectional back-reference collections:
 * <ul>
 *   <li>{@code seasonDrivers} — owned by {@code SeasonDriver.driver} (serialized in
 *       {@code data/season-drivers.json}).</li>
 *   <li>{@code raceResults} — owned by {@code RaceResult.driver} (serialized in
 *       {@code data/race-results.json}).</li>
 *   <li>{@code aliases} — owned by {@code PsnAlias.driver} (serialized in
 *       {@code data/psn-aliases.json}; OQ-4 single-source emission).</li>
 * </ul>
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler",
        "seasonDrivers", "raceResults", "aliases"})
public abstract class DriverMixIn {
}
