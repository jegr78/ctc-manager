package org.ctc.discord.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
@Setter
public class DiscordConfigForm {

	private static final String WEBHOOK_REGEX =
			"^$|^https://(?:discord|discordapp)\\.com(?:/api)?(?:/v\\d+)?/webhooks/\\d+/[A-Za-z0-9_-]+(?:\\?.*)?$";
	private static final String WEBHOOK_MESSAGE = "Must be an empty string or a discord.com webhook URL";

	@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)
	private String guildId = "";

	@Size(max = 500)
	@Pattern(regexp = WEBHOOK_REGEX, message = WEBHOOK_MESSAGE)
	private String announcementWebhookUrl = "";

	@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)
	private String raceResultsForumChannelId = "";

	@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)
	private String standingsForumChannelId = "";

	@Size(max = 500)
	@Pattern(regexp = WEBHOOK_REGEX, message = WEBHOOK_MESSAGE)
	private String raceResultsForumWebhookUrl = "";

	@Size(max = 500)
	@Pattern(regexp = WEBHOOK_REGEX, message = WEBHOOK_MESSAGE)
	private String standingsForumWebhookUrl = "";

	@NotBlank
	@Size(max = 50)
	private String vsEmojiName = "CTC";

	@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)
	private String botApplicationId;

	@Pattern(regexp = DiscordSnowflake.PATTERN, message = DiscordSnowflake.MESSAGE)
	private String currentMatchCategoryId = "";

	private String matchdayPairingsTemplate = "";
}
