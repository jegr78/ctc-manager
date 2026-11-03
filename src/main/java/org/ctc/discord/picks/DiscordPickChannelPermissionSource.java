package org.ctc.discord.picks;

import lombok.RequiredArgsConstructor;
import org.ctc.discord.DiscordChannelMemberAccess;
import org.ctc.discord.DiscordRestClient;
import org.ctc.domain.picks.PickChannelPermissionSource;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DiscordPickChannelPermissionSource implements PickChannelPermissionSource {
    private final DiscordRestClient discord;
    @Override
    public boolean hasCurrentPrivateAccess(String guildId, String channelId, String userId) {
        var current = DiscordChannelMemberAccess.current(discord, guildId, channelId, userId);
        return current != null && current.privateChannel();
    }
}
