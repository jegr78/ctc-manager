package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "playoff_rounds")
@Getter
@Setter
@NoArgsConstructor
@ToString(exclude = {"playoff", "matchups"})
public class PlayoffRound extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "playoff_id", nullable = false)
	private Playoff playoff;

	@NotBlank
	@Column(nullable = false)
	private String label;

	@Column(name = "round_index", nullable = false)
	private int roundIndex;

	@Column(name = "best_of_legs", nullable = false)
	private int bestOfLegs = 1;

	@OneToMany(mappedBy = "round", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("bracketPosition ASC")
	private List<PlayoffMatchup> matchups = new ArrayList<>();

	public PlayoffRound(Playoff playoff, String label, int roundIndex) {
		this.playoff = playoff;
		this.label = label;
		this.roundIndex = roundIndex;
	}
}
