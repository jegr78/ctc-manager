package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "playoffs")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"phase", "rounds", "seeds"})
public class Playoff extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "phase_id", nullable = false, unique = true)
	private SeasonPhase phase;

	@NotBlank
	@Column(nullable = false)
	private String name;

	/**
	 * Phase 61 WR-04 follow-up: {@code startDate} / {@code endDate} / {@code eventDurationMinutes}
	 * are also present on {@link SeasonPhase} (the parent PLAYOFF phase) and were backfilled by
	 * V4. The Playoff form ({@code PlayoffService.createPlayoff(6-arg)}) writes here; the Phase
	 * form ({@code SeasonPhaseController}) writes onto {@link SeasonPhase}. The two values can
	 * silently diverge when only one form is edited.
	 *
	 * <p>{@code RaceCalendarService.resolveEventDuration} reads
	 * {@code playoff.eventDurationMinutes} first, so the Playoff fields below are currently
	 * authoritative for Google Calendar event durations. A follow-up V7 migration is planned
	 * to drop these duplicate columns and route all Playoff date/duration access through the
	 * parent SeasonPhase as the single source of truth.
	 */
	private LocalDate startDate;

	private LocalDate endDate;

	@Column(name = "event_duration_minutes")
	private Integer eventDurationMinutes;

	@OneToMany(mappedBy = "playoff", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("roundIndex ASC")
	private List<PlayoffRound> rounds = new ArrayList<>();

	@OneToMany(mappedBy = "playoff", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("seed ASC")
	private List<PlayoffSeed> seeds = new ArrayList<>();

	public Playoff(SeasonPhase phase, String name) {
		this.phase = phase;
		this.name = name;
	}

	/**
	 * Convenience getter — derives season via {@code getPhase().getSeason()}.
	 * The {@code playoffs.season_id} bridge column and the {@code playoff_seasons} M:N join table
	 * were both dropped in V6 (MIGR-06); the phase association is now the single source of truth.
	 */
	public Season getSeason() {
		return phase != null ? phase.getSeason() : null;
	}
}
