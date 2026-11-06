package org.ctc.domain.model;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "race_disqualification_revisions", uniqueConstraints = @UniqueConstraint(columnNames = {"result_id", "revision_number"}))
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RaceDisqualificationRevision extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "result_id", nullable = false) private RaceResult result;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "race_id", nullable = false) private Race race;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "driver_id", nullable = false) private Driver driver;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "season_id", nullable = false) private Season season;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "phase_id", nullable = false) private SeasonPhase phase;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "matchday_id", nullable = false) private Matchday matchday;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "match_id") private Match match;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "playoff_matchup_id") private PlayoffMatchup playoffMatchup;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "home_team_id", nullable = false) private Team homeTeam;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "away_team_id", nullable = false) private Team awayTeam;
    @Column(nullable = false) private int revisionNumber;
    @Column(nullable = false) private boolean previousDisqualified;
    @Column(nullable = false) private boolean disqualified;
    @Column(nullable = false) private int recordedPosition;
    @Column(nullable = false, length = 2000) private String reason;
    @Column(nullable = false, length = 200) private String actorId;
    @Column(nullable = false, length = 64) private String resultFingerprint;

    public RaceDisqualificationRevision(RaceResult result, int revision, boolean previous, boolean disqualified,
                                        String reason, String actor, String fingerprint) {
        this.result = result; this.race = result.getRace(); this.driver = result.getDriver();
        this.matchday = race.getMatchday(); this.phase = matchday.getPhase(); this.season = phase.getSeason();
        this.match = race.getMatch(); this.playoffMatchup = race.getPlayoffMatchup();
        this.homeTeam = race.getHomeTeam(); this.awayTeam = race.getAwayTeam(); this.revisionNumber = revision;
        this.previousDisqualified = previous; this.disqualified = disqualified; this.recordedPosition = result.getPosition();
        this.reason = reason; this.actorId = actor; this.resultFingerprint = fingerprint;
    }
}
