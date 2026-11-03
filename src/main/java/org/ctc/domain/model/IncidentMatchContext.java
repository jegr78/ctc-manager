package org.ctc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "incident_match_contexts", uniqueConstraints = @UniqueConstraint(name = "uq_incident_context_channel", columnNames = {"guild_id", "channel_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentMatchContext extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "match_id", unique = true) private Match match;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "playoff_matchup_id", unique = true) private PlayoffMatchup playoffMatchup;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "season_id", nullable = false) private Season season;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "home_team_id", nullable = false) private Team homeTeam;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "away_team_id", nullable = false) private Team awayTeam;
    @Column(nullable = false, length = 32) private String guildId;
    @Column(nullable = false, length = 32) private String channelId;
    @Column(name = "aggregate_version", nullable = false) private long version;
    @Column(nullable = false, length = 128) private String boundBy;
    @Convert(converter = UtcInstantConverter.class) @Column(nullable = false) private Instant boundAt;

    public IncidentMatchContext(Match match, PlayoffMatchup playoffMatchup, Season season, Team homeTeam, Team awayTeam,
                                String guildId, String channelId, String boundBy, Instant boundAt) {
        this.match = match; this.playoffMatchup = playoffMatchup; this.season = season;
        this.homeTeam = homeTeam; this.awayTeam = awayTeam; this.guildId = guildId; this.channelId = channelId;
        this.boundBy = boundBy; this.boundAt = boundAt; this.version = 1;
    }
}
