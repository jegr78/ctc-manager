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

	/**
	 * Phase 61 MIGR-06 transitional bridge column — the {@code playoffs.season_id} column
	 * is still {@code NOT NULL} (and {@code UNIQUE}) in the V1-V5 schema. Plan 61-03 introduces
	 * V6 to drop it; until then we auto-fill it from {@code phase.getSeason()} in
	 * {@link #syncSeasonBridge()}. Read-only after persist — {@link #phase} is canonical.
	 */
	@Column(name = "season_id", nullable = false, updatable = false)
	private UUID seasonId;

	@NotBlank
	@Column(nullable = false)
	private String name;

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

	/**
	 * Phase 61 MIGR-06: keeps the {@code playoffs.season_id NOT NULL} V1 bridge column populated
	 * pre-V6. Auto-derives the value from the phase before INSERT so callers do not have to.
	 */
	@PrePersist
	void syncSeasonBridge() {
		if (seasonId == null && phase != null && phase.getSeason() != null) {
			seasonId = phase.getSeason().getId();
		}
	}
}
