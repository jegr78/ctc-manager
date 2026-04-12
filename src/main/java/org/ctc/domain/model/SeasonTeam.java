package org.ctc.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "season_teams")
@Getter
@Setter
@NoArgsConstructor
public class SeasonTeam extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "season_id", nullable = false)
	private Season season;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	private Integer rating;

	private String primaryColor;

	private String secondaryColor;

	private String accentColor;

	private String logoUrl;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "successor_season_team_id")
	private SeasonTeam successor;

	private LocalDate replacedAt;

	public SeasonTeam(Season season, Team team) {
		this.season = season;
		this.team = team;
	}

	public String getEffectivePrimaryColor() {
		return primaryColor != null ? primaryColor : team.getPrimaryColor();
	}

	public String getEffectiveSecondaryColor() {
		return secondaryColor != null ? secondaryColor : team.getSecondaryColor();
	}

	public String getEffectiveAccentColor() {
		return accentColor != null ? accentColor : team.getAccentColor();
	}

	public String getEffectiveLogoUrl() {
		return logoUrl != null ? logoUrl : team.getLogoUrl();
	}

	public boolean isReplaced() {
		return successor != null;
	}

	public SeasonTeam getActiveSeasonTeam() {
		SeasonTeam current = this;
		int maxDepth = 10;
		while (current.getSuccessor() != null && maxDepth-- > 0) {
			current = current.getSuccessor();
		}
		return current;
	}
}
