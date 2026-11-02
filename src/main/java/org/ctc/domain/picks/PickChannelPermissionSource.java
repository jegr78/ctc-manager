package org.ctc.domain.picks;

public interface PickChannelPermissionSource {
    boolean hasCurrentPrivateAccess(String guildId, String channelId, String userId);
}
