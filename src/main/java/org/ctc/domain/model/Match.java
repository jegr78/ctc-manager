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
@org.hibernate.annotations.DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"matchday", "homeTeam", "awayTeam", "walkoverTeam", "races", "discordChannelWebhookUrl"})
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

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "walkover_team_id")
	private Team walkoverTeam;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tie_break_winner_id")
	private Team tieBreakWinner;

	private Integer homeScore;

	private Integer awayScore;

    @Column(name = "pick_deadline_override")
    private LocalDateTime pickDeadlineOverride;

    @Column(name = "pick_window_version", nullable = false)
    private long pickWindowVersion;

	@Column(nullable = false)
	private boolean bye;

	// Selection writes refresh each race separately before refreshing pairing ownership.
	@OneToMany(mappedBy = "match", cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE, CascadeType.DETACH}, orphanRemoval = true)
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

	public boolean isWalkoverFor(Team team) {
		return walkoverTeam != null && team != null && walkoverTeam.getId().equals(team.getId());
	}

    /** Drops a tie-break winner that a walkover, a changed score or a replaced team has made invalid. */
    @PrePersist
    @PreUpdate
    void dropInvalidTieBreakWinner() {
        if (tieBreakWinner == null) return;
        if (!TieBreakRules.valid(true, tieBreakWinner.getId(), homeTeam == null ? null : homeTeam.getId(),
                awayTeam == null ? null : awayTeam.getId(), homeScore, awayScore, bye,
                walkoverTeam == null ? null : walkoverTeam.getId())) {
            tieBreakWinner = null;
        }
    }

    /** The stored result of this pairing, or null while it is unplayed. */
    public MatchOutcome outcome() {
        if (bye) return new MatchOutcome(homeTeam, null, false);
        if (walkoverTeam != null) {
            boolean homeForfeits = homeTeam != null && walkoverTeam.getId().equals(homeTeam.getId());
            return new MatchOutcome(homeForfeits ? awayTeam : homeTeam, walkoverTeam, false);
        }
        if (homeScore == null || awayScore == null) return null;
        if (homeScore > awayScore) return new MatchOutcome(homeTeam, awayTeam, false);
        if (awayScore > homeScore) return new MatchOutcome(awayTeam, homeTeam, false);
        if (tieBreakWinner == null) return MatchOutcome.draw();
        boolean homeWins = homeTeam != null && tieBreakWinner.getId().equals(homeTeam.getId());
        return new MatchOutcome(tieBreakWinner, homeWins ? awayTeam : homeTeam, true);
    }

    public boolean isHomeWinner() {
        var result = outcome();
        return result != null && result.isWinner(homeTeam);
    }

    public boolean isAwayWinner() {
        var result = outcome();
        return result != null && result.isWinner(awayTeam);
    }

    public boolean isWonOnTieBreak() {
        var result = outcome();
        return result != null && result.tieBreak();
    }

    public boolean isScoredDraw() {
        var result = outcome();
        return result != null && result.isDraw();
    }

    public Integer getOfficialHomeScore() {
        if (walkoverTeam == null) return homeScore;
        return WalkoverScoring.score(homeTeam, awayTeam, walkoverTeam, matchday.getPhase().getRaceScoring()).home();
    }

    public Integer getOfficialAwayScore() {
        if (walkoverTeam == null) return awayScore;
        return WalkoverScoring.score(homeTeam, awayTeam, walkoverTeam, matchday.getPhase().getRaceScoring()).away();
    }
}
