package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "season_phase_groups")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"phase"})
public class SeasonPhaseGroup extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "phase_id", nullable = false)
	private SeasonPhase phase;

	@NotBlank
	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private int sortIndex;

	public SeasonPhaseGroup(SeasonPhase phase, String name, int sortIndex) {
		this.phase = phase;
		this.name = name;
		this.sortIndex = sortIndex;
	}
}
