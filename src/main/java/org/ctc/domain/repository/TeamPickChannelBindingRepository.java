package org.ctc.domain.repository;

import java.util.List;
import org.ctc.domain.model.TeamPickChannelBinding;

public interface TeamPickChannelBindingRepository {
    List<TeamPickChannelBinding> findByGuildIdAndChannelId(String guildId, String channelId);
}
