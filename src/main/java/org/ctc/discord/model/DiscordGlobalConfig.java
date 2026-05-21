package org.ctc.discord.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.ctc.domain.model.BaseEntity;

@Entity
@Getter
@NoArgsConstructor
@Setter
@Table(name = "discord_global_config")
@ToString(exclude = {"announcementWebhookUrl"})
public class DiscordGlobalConfig extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "guild_id", length = 32, nullable = false)
	private String guildId = "";

	@Column(name = "announcement_webhook_url", length = 500, nullable = false)
	private String announcementWebhookUrl = "";

	@Column(name = "race_results_forum_channel_id", length = 32, nullable = false)
	private String raceResultsForumChannelId = "";

	@Column(name = "standings_forum_channel_id", length = 32, nullable = false)
	private String standingsForumChannelId = "";

	@Column(name = "vs_emoji_name", length = 50, nullable = false)
	private String vsEmojiName = "CTC";

	@Column(name = "bot_application_id", length = 32)
	private String botApplicationId;
}
