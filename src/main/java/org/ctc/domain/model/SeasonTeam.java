package org.ctc.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "season_teams")
@Getter @Setter @NoArgsConstructor
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
}
