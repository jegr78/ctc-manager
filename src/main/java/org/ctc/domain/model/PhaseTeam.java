package org.ctc.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Entity
@Table(name = "phase_teams")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"phase", "team", "group"})
public class PhaseTeam extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "phase_id", nullable = false)
	private SeasonPhase phase;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "group_id")
	private SeasonPhaseGroup group;

	public PhaseTeam(SeasonPhase phase, Team team) {
		this.phase = phase;
		this.team = team;
	}
}
