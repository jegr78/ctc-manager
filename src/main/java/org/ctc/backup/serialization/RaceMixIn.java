package org.ctc.backup.serialization;

import com.fasterxml.jackson.annotation.*;
import org.ctc.domain.model.*;

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
    abstract SeasonCarPoolEntry getCarPoolEntry();

    @JsonIdentityReference(alwaysAsId = true)
    abstract PlayoffMatchup getPlayoffMatchup();

    @JsonProperty("homeTeamOverride")
    @JsonIdentityReference(alwaysAsId = true)
    Team homeTeamOverride;

    @JsonProperty("awayTeamOverride")
    @JsonIdentityReference(alwaysAsId = true)
    Team awayTeamOverride;
}
