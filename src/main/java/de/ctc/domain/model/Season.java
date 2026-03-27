package de.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "seasons")
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"matchdays", "seasonDrivers", "teams"})
public class Season {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String name;

    private LocalDate startDate;

    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active = false;

    @OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortIndex ASC")
    private List<Matchday> matchdays = new ArrayList<>();

    @OneToMany(mappedBy = "season")
    private List<SeasonDriver> seasonDrivers = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "season_teams",
            joinColumns = @JoinColumn(name = "season_id"),
            inverseJoinColumns = @JoinColumn(name = "team_id"))
    @OrderBy("shortName ASC")
    private List<Team> teams = new ArrayList<>();

    public Season(String name) {
        this.name = name;
    }
}
