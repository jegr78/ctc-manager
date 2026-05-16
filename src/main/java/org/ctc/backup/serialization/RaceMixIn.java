package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.ctc.domain.model.Track;

/**
 * Externalised Jackson annotations for {@link Race}.
 *
 * <p>Renders all seven {@code @ManyToOne} foreign keys as UUID references. Suppresses:
 * <ul>
 *   <li>{@code settings} — inverse {@code @OneToOne(mappedBy="race")} side; the
 *       {@code RaceSettings} row owns the FK and is serialized in
 *       {@code data/race-settings.json}.</li>
 *   <li>{@code results}, {@code attachments} — child back-references.</li>
 *   <li>Seven convenience getters: {@code getHomeTeam}, {@code getAwayTeam},
 *       {@code getHomeScore}, {@code getAwayScore}, {@code isBye} (property {@code bye}),
 *       {@code hasAllSettings} (property {@code allSettings}),
 *       {@code hasCalendarEvent} (property {@code calendarEvent}).</li>
 * </ul>
 *
 * <p>Note: {@code Race.homeTeamOverride} and {@code Race.awayTeamOverride} carry
 * {@code @Getter(AccessLevel.NONE)} on the entity (no public getter; the convenience
 * methods {@code getHomeTeam()} / {@code getAwayTeam()} are the in-code access path).
 * To make these private fields visible to Jackson while keeping the original entity
 * byte-identical, the MixIn declares matching field stubs annotated with
 * {@code @JsonProperty} + {@code @JsonIdentityReference} — Jackson uses the MixIn field
 * declarations to discover and read the underlying private fields by reflection.
 */
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler",
        "settings", "results", "attachments",
        "homeTeam", "awayTeam", "homeScore", "awayScore",
        "bye", "allSettings", "calendarEvent"})
public abstract class RaceMixIn {

    @JsonIdentityReference(alwaysAsId = true)
    abstract Matchday getMatchday();

    @JsonIdentityReference(alwaysAsId = true)
    abstract Match getMatch();

    @JsonIdentityReference(alwaysAsId = true)
    abstract Track getTrack();

    @JsonIdentityReference(alwaysAsId = true)
    abstract Car getCar();

    @JsonIdentityReference(alwaysAsId = true)
    abstract PlayoffMatchup getPlayoffMatchup();

    @JsonProperty("homeTeamOverride")
    @JsonIdentityReference(alwaysAsId = true)
    Team homeTeamOverride;

    @JsonProperty("awayTeamOverride")
    @JsonIdentityReference(alwaysAsId = true)
    Team awayTeamOverride;
}
