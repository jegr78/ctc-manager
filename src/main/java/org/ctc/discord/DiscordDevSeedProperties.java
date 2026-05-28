package org.ctc.discord;

import static org.springframework.util.StringUtils.hasText;

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
		String announcementWebhookUrl,
		String raceResultsForumWebhookUrl,
		String standingsForumWebhookUrl) {

	public boolean hasGuildId() {
		return hasText(guildId);
	}
}
