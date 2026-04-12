package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "races")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"matchday", "match", "track", "car", "settings", "results", "playoffMatchup", "attachments", "homeTeamOverride", "awayTeamOverride"})
public class Race extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "matchday_id", nullable = false)
	private Matchday matchday;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "match_id")
	private Match match;

	private LocalDateTime dateTime;

	@Column(name = "calendar_event_id")
	private String calendarEventId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "track_id")
	private Track track;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "car_id")
	private Car car;

	@OneToOne(mappedBy = "race", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	private RaceSettings settings;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "playoff_matchup_id")
	private PlayoffMatchup playoffMatchup;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "home_team_id")
	@Getter(AccessLevel.NONE)
	private Team homeTeamOverride;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "away_team_id")
	@Getter(AccessLevel.NONE)
	private Team awayTeamOverride;

	@OneToMany(mappedBy = "race", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	private List<RaceResult> results = new ArrayList<>();

	@OneToMany(mappedBy = "race", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("createdAt DESC")
	private List<RaceAttachment> attachments = new ArrayList<>();

	// Convenience delegation methods — resolve teams from override, Match, or PlayoffMatchup
	public Team getHomeTeam() {
		if (homeTeamOverride != null) return homeTeamOverride;
		if (match != null) return match.getHomeTeam();
		if (playoffMatchup != null) return playoffMatchup.getTeam1();
		return null;
	}

	public Team getAwayTeam() {
		if (awayTeamOverride != null) return awayTeamOverride;
		if (match != null) return match.getAwayTeam();
		if (playoffMatchup != null) return playoffMatchup.getTeam2();
		return null;
	}

	public boolean isBye() {
		return match != null && match.isBye();
	}

	public Integer getHomeScore() {
		if (match != null) return match.getHomeScore();
		if (playoffMatchup != null) return playoffMatchup.getHomeScore();
		return null;
	}

	public Integer getAwayScore() {
		if (match != null) return match.getAwayScore();
		if (playoffMatchup != null) return playoffMatchup.getAwayScore();
		return null;
	}

	public boolean hasAllSettings() {
		return settings != null && settings.isComplete();
	}

	public boolean hasCalendarEvent() {
		return calendarEventId != null && !calendarEventId.isBlank();
	}
}
