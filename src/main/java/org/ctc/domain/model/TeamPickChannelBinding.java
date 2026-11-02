package org.ctc.domain.model;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "team_pick_channel_bindings", uniqueConstraints = {
        @UniqueConstraint(name = "uq_pick_binding_team", columnNames = {"guild_id", "season_id", "team_id"}),
        @UniqueConstraint(name = "uq_pick_binding_channel", columnNames = {"guild_id", "season_id", "channel_id"})})
@Getter
@Setter
@NoArgsConstructor
public class TeamPickChannelBinding extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;
    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;
    @Column(name = "channel_id", nullable = false, length = 32)
    private String channelId;

    public TeamPickChannelBinding(Season season, Team team, String guildId, String channelId) {
        this.season = season; this.team = team; this.guildId = guildId; this.channelId = channelId;
    }
}
