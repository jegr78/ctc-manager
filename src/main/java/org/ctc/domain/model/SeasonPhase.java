package org.ctc.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "season_phases")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"season", "groups", "raceScoring", "matchScoring"})
public class SeasonPhase extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "season_id", nullable = false)
	private Season season;

	@Column(nullable = false)
	private int sortIndex;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PhaseType phaseType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PhaseLayout layout;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SeasonFormat format = SeasonFormat.LEAGUE;

	private String label;

	private LocalDate startDate;

	private LocalDate endDate;

	private Integer totalRounds;

	@Column(nullable = false)
	private int legs = 1;

	@Column(name = "event_duration_minutes")
	private Integer eventDurationMinutes;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "race_scoring_id", nullable = false)
	private RaceScoring raceScoring;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "match_scoring_id", nullable = false)
	private MatchScoring matchScoring;

	@OneToMany(mappedBy = "phase", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("sortIndex ASC")
	private List<SeasonPhaseGroup> groups = new ArrayList<>();

	public SeasonPhase(Season season, PhaseType phaseType, PhaseLayout layout, int sortIndex) {
		this.season = season;
		this.phaseType = phaseType;
		this.layout = layout;
		this.sortIndex = sortIndex;
	}
}
