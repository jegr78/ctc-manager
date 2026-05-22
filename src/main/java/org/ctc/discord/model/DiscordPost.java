package org.ctc.discord.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.ctc.domain.model.BaseEntity;

@Entity
@Getter
@NoArgsConstructor
@Setter
@Table(name = "discord_post")
@ToString(exclude = {"webhookToken"})
public class DiscordPost extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "channel_id", length = 32, nullable = false)
	private String channelId;

	@Column(name = "message_id", length = 32, nullable = false)
	private String messageId;

	@Column(name = "webhook_id", length = 32, nullable = false)
	private String webhookId;

	@Column(name = "webhook_token", length = 128, nullable = false)
	private String webhookToken;

	@Enumerated(EnumType.STRING)
	@Column(name = "post_type", length = 32, nullable = false)
	private DiscordPostType postType;

	@Column(name = "match_id")
	private UUID matchId;

	@Column(name = "matchday_id")
	private UUID matchdayId;

	@Column(name = "race_id")
	private UUID raceId;

	@Column(name = "season_id")
	private UUID seasonId;

	@Column(name = "posted_at", nullable = false)
	private LocalDateTime postedAt;

	@Column(name = "attachments_replaced_at")
	private LocalDateTime attachmentsReplacedAt;
}
