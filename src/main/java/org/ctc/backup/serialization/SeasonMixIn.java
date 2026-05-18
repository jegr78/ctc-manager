package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import java.util.List;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Track;

/**
 * Externalised Jackson annotations for {@link Season}.
 *
 * <p>Ignores the three child collections ({@code phases}, {@code seasonDrivers},
 * {@code seasonTeams}) — children own the FK back to {@code Season} and are emitted in
 * their own top-level files.
 *
 * <p>The {@code @ManyToMany} collections {@code cars} and {@code tracks} are rendered as
 * arrays of UUID strings via {@code @JsonIdentityReference(alwaysAsId=true)}. {@code Car}
 * and {@code Track} are top-level entities in {@code data/cars.json} / {@code data/tracks.json}.
 *
 * <p>Computed convenience getters ({@code displayLabel}, {@code teams}, {@code matchdays},
 * {@code activeTeams}, {@code eligibleTeams}) are suppressed.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler",
		"phases", "seasonDrivers", "seasonTeams",
		"displayLabel", "teams", "matchdays", "activeTeams", "eligibleTeams"})
public abstract class SeasonMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract List<Car> getCars();

    @JsonIdentityReference(alwaysAsId = true)
    abstract List<Track> getTracks();
}
