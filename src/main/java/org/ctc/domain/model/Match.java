package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "matches")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"matchday", "homeTeam", "awayTeam", "races", "discordChannelWebhookUrl"})
public class Match extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "matchday_id", nullable = false)
	private Matchday matchday;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "home_team_id", nullable = false)
	private Team homeTeam;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "away_team_id")
	private Team awayTeam;

	private Integer homeScore;

	private Integer awayScore;

	@Column(nullable = false)
	private boolean bye;

	@OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("dateTime ASC NULLS LAST")
	private List<Race> races = new ArrayList<>();

	@Column(name = "discord_channel_id", length = 32)
	private String discordChannelId;

	@Column(name = "discord_channel_webhook_url", length = 500)
	private String discordChannelWebhookUrl;

	@Column(name = "discord_teaser", length = 2000)
	private String discordTeaser;

	@Column(name = "stream_link", length = 500)
	private String streamLink;

	@Column(name = "lobby_host", length = 100)
	private String lobbyHost;

	@Column(name = "race_director", length = 100)
	private String raceDirector;

	@Column(name = "streamer", length = 100)
	private String streamer;

	@Column(name = "discord_channel_archived_at")
	private LocalDateTime discordChannelArchivedAt;

	public Match(Matchday matchday, Team homeTeam, Team awayTeam) {
		this.matchday = matchday;
		this.homeTeam = homeTeam;
		this.awayTeam = awayTeam;
	}
}
