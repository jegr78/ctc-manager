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
	 * {@code startDate} / {@code endDate} / {@code eventDurationMinutes} also exist on the parent
	 * {@link SeasonPhase} and can diverge: the Playoff form writes here, the Phase form writes
	 * onto {@link SeasonPhase}. {@code RaceCalendarService.resolveEventDuration} reads from
	 * {@link Playoff} first, so these fields are authoritative for Google Calendar event durations.
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
	 */
	public Season getSeason() {
		return phase != null ? phase.getSeason() : null;
	}
}
