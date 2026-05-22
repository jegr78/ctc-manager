package org.ctc.discord;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.discord.dev-seed")
public record DiscordDevSeedProperties(
		boolean enabled,
		String guildId,
		String botAppId,
		String currentMatchCategoryId,
		String raceResultsForumChannelId,
		String standingsForumChannelId,
		String vsEmojiName,
		String announcementWebhookUrl) {

	public boolean hasGuildId() {
		return guildId != null && !guildId.isBlank();
	}
}
