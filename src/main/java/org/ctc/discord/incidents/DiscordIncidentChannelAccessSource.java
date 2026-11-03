package org.ctc.discord.incidents;

import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.ctc.discord.DiscordChannelMemberAccess;
import org.ctc.discord.DiscordRestClient;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.incidents.IncidentChannelAccessSource;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiscordIncidentChannelAccessSource implements IncidentChannelAccessSource {
    private final DiscordRestClient discord;
    @Override
    public Set<String> currentRoles(String guild, String channel, String user) {
        var current = DiscordChannelMemberAccess.current(discord, guild, channel, user);
        if (current == null) throw new BusinessRuleException("Incident channel access is unavailable");
        return current.roles();
    }
}
