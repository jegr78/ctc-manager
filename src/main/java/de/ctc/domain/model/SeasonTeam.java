package de.ctc.domain.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "season_teams")
@Getter @Setter @NoArgsConstructor
public class SeasonTeam {

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
        if (primaryColor != null) return primaryColor;
        if (team.getPrimaryColor() != null) return team.getPrimaryColor();
        return team.isSubTeam() ? team.getParentTeam().getPrimaryColor() : null;
    }

    public String getEffectiveSecondaryColor() {
        if (secondaryColor != null) return secondaryColor;
        if (team.getSecondaryColor() != null) return team.getSecondaryColor();
        return team.isSubTeam() ? team.getParentTeam().getSecondaryColor() : null;
    }

    public String getEffectiveAccentColor() {
        if (accentColor != null) return accentColor;
        if (team.getAccentColor() != null) return team.getAccentColor();
        return team.isSubTeam() ? team.getParentTeam().getAccentColor() : null;
    }

    public String getEffectiveLogoUrl() {
        if (logoUrl != null) return logoUrl;
        if (team.getLogoUrl() != null) return team.getLogoUrl();
        return team.isSubTeam() ? team.getParentTeam().getLogoUrl() : null;
    }
}
