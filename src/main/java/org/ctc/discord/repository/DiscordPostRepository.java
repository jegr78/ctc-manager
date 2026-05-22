package org.ctc.discord.repository;

import java.util.Optional;
import java.util.UUID;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DiscordPostRepository
		extends JpaRepository<DiscordPost, Long>, JpaSpecificationExecutor<DiscordPost> {

	Optional<DiscordPost> findByChannelIdAndPostTypeAndMatchId(
			String channelId, DiscordPostType postType, UUID matchId);
}
