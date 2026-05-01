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
	 * The {@code matchdays.season_id} bridge column was dropped in V6 (MIGR-06);
	 * the phase association is now the single source of truth.
	 * Returns {@code null} only if {@code phase} is unset, which should not occur post-V4.
	 */
	public Season getSeason() {
		return phase != null ? phase.getSeason() : null;
	}
}
