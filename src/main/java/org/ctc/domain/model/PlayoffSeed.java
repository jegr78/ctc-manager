package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "playoff_seeds")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"playoff", "team"})
public class PlayoffSeed extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "playoff_id", nullable = false)
	private Playoff playoff;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@Column(nullable = false)
	private int seed;

	public PlayoffSeed(Playoff playoff, Team team, int seed) {
		this.playoff = playoff;
		this.team = team;
		this.seed = seed;
	}
}
