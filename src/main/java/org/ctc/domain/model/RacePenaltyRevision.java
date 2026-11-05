package org.ctc.domain.model;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "race_penalty_revisions", uniqueConstraints = @UniqueConstraint(columnNames = {"race_id", "revision_number"}))
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RacePenaltyRevision extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "race_id", nullable = false)
    private Race race;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "season_id", nullable = false)
    private Season season;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "phase_id", nullable = false)
    private SeasonPhase phase;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "matchday_id", nullable = false)
    private Matchday matchday;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "match_id")
    private Match match;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "playoff_matchup_id")
    private PlayoffMatchup playoffMatchup;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;
    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;
    @Column(name = "home_penalty", nullable = false)
    private int homePenalty;
    @Column(name = "away_penalty", nullable = false)
    private int awayPenalty;
    @Column(name = "home_gross", nullable = false)
    private int homeGross;
    @Column(name = "away_gross", nullable = false)
    private int awayGross;
    @Column(nullable = false, length = 2000)
    private String reason;
    @Column(name = "actor_id", nullable = false, length = 200)
    private String actorId;
    @Column(name = "result_fingerprint", nullable = false, length = 64)
    private String resultFingerprint;

    public RacePenaltyRevision(Race race, int revisionNumber, int homePenalty, int awayPenalty,
                               int homeGross, int awayGross, String reason, String actorId, String fingerprint) {
        this.race = race;
        this.matchday = race.getMatchday();
        this.phase = matchday.getPhase();
        this.season = phase.getSeason();
        this.match = race.getMatch();
        this.playoffMatchup = race.getPlayoffMatchup();
        this.homeTeam = race.getHomeTeam();
        this.awayTeam = race.getAwayTeam();
        this.revisionNumber = revisionNumber;
        this.homePenalty = homePenalty;
        this.awayPenalty = awayPenalty;
        this.homeGross = homeGross;
        this.awayGross = awayGross;
        this.reason = reason;
        this.actorId = actorId;
        this.resultFingerprint = fingerprint;
    }
}
