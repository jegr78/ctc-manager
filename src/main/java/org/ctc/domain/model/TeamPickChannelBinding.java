package org.ctc.domain.model;

import lombok.Getter;

@Getter
public class TeamPickChannelBinding {
    private final Season season;
    private final Team team;
    private final String guildId;
    private final String channelId;

    public TeamPickChannelBinding(Season season, Team team, String guildId, String channelId) {
        this.season = season; this.team = team; this.guildId = guildId; this.channelId = channelId;
    }
}
