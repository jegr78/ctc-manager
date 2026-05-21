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

	private static final String SNOWFLAKE_REGEX = "^$|^\\d{17,20}$";
	private static final String SNOWFLAKE_MESSAGE = "Must be a Discord snowflake (17–20 digits) or empty";
	private static final String WEBHOOK_REGEX = "^$|^https://discord\\.com/api/webhooks/\\d+/[A-Za-z0-9_-]+$";
	private static final String WEBHOOK_MESSAGE = "Must be an empty string or a discord.com webhook URL";

	@Pattern(regexp = SNOWFLAKE_REGEX, message = SNOWFLAKE_MESSAGE)
	private String guildId = "";

	@Size(max = 500)
	@Pattern(regexp = WEBHOOK_REGEX, message = WEBHOOK_MESSAGE)
	private String announcementWebhookUrl = "";

	@Pattern(regexp = SNOWFLAKE_REGEX, message = SNOWFLAKE_MESSAGE)
	private String raceResultsForumChannelId = "";

	@Pattern(regexp = SNOWFLAKE_REGEX, message = SNOWFLAKE_MESSAGE)
	private String standingsForumChannelId = "";

	@NotBlank
	@Size(max = 50)
	private String vsEmojiName = "CTC";

	@Pattern(regexp = SNOWFLAKE_REGEX, message = SNOWFLAKE_MESSAGE)
	private String botApplicationId;
}
