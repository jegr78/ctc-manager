package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "matchdays")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"phase", "group", "matches", "races"})
public class Matchday extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "phase_id", nullable = false)
	private SeasonPhase phase;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "group_id")
	private SeasonPhaseGroup group;

	/**
	 * Phase 61 MIGR-06 transitional bridge column — the {@code matchdays.season_id} column
	 * is still {@code NOT NULL} in the V1-V5 schema. Plan 61-03 introduces V6 to drop it;
	 * until then we auto-fill it from {@code phase.getSeason()} in {@link #syncSeasonBridge()}.
	 * Read-only after persist — the canonical association is {@link #phase}.
	 */
	@Column(name = "season_id", nullable = false, updatable = false)
	private UUID seasonId;

	@NotBlank
	@Column(nullable = false)
	private String label;

	@Column(nullable = false)
	private int sortIndex;

	@OneToMany(mappedBy = "matchday", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Match> matches = new ArrayList<>();

	@OneToMany(mappedBy = "matchday", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Race> races = new ArrayList<>();

	public Matchday(SeasonPhase phase, String label, int sortIndex) {
		this.phase = phase;
		this.label = label;
		this.sortIndex = sortIndex;
	}

	/**
	 * Convenience getter — derives season via {@code getPhase().getSeason()}.
	 * The {@code matchdays.season_id} bridge column survives until V6 (Plan 61-03)
	 * but the phase association is the single source of truth in Java.
	 * Returns {@code null} only if {@code phase} is unset, which should not occur post-V4.
	 */
	public Season getSeason() {
		return phase != null ? phase.getSeason() : null;
	}

	/**
	 * Phase 61 MIGR-06: keeps the {@code matchdays.season_id NOT NULL} V1 bridge column populated
	 * pre-V6. Auto-derives the value from the phase before INSERT so callers do not have to.
	 */
	@PrePersist
	void syncSeasonBridge() {
		if (seasonId == null && phase != null && phase.getSeason() != null) {
			seasonId = phase.getSeason().getId();
		}
	}
}
