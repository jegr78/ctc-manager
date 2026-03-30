package de.ctc.domain.model;

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
@Table(name = "teams")
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"seasonDrivers", "parentTeam", "subTeams"})
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Column(nullable = false)
    private String shortName;

    private String logoUrl;

    private String primaryColor;

    private String secondaryColor;

    private String accentColor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_team_id")
    private Team parentTeam;

    @OneToMany(mappedBy = "parentTeam")
    @OrderBy("shortName ASC")
    private List<Team> subTeams = new ArrayList<>();

    @OneToMany(mappedBy = "team")
    private List<SeasonDriver> seasonDrivers = new ArrayList<>();

    public Team(String name, String shortName) {
        this.name = name;
        this.shortName = shortName;
    }

    public Team(String name, String shortName, Team parentTeam) {
        this.name = name;
        this.shortName = shortName;
        this.parentTeam = parentTeam;
    }

    public boolean isSubTeam() {
        return parentTeam != null;
    }

    public boolean hasSubTeams() {
        return subTeams != null && !subTeams.isEmpty();
    }

    public Team getParentOrSelf() {
        return parentTeam != null ? parentTeam : this;
    }

    public String getEffectivePrimaryColor() {
        if (primaryColor != null) return primaryColor;
        return parentTeam != null ? parentTeam.getPrimaryColor() : null;
    }

    public String getEffectiveSecondaryColor() {
        if (secondaryColor != null) return secondaryColor;
        return parentTeam != null ? parentTeam.getSecondaryColor() : null;
    }

    public String getEffectiveAccentColor() {
        if (accentColor != null) return accentColor;
        return parentTeam != null ? parentTeam.getAccentColor() : null;
    }

    public String getEffectiveLogoUrl() {
        if (logoUrl != null) return logoUrl;
        return parentTeam != null ? parentTeam.getLogoUrl() : null;
    }
}
