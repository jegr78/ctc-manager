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
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"matchdays", "seasonDrivers", "seasonTeams", "cars", "tracks", "raceScoring", "matchScoring"})
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeasonFormat format = SeasonFormat.LEAGUE;

    private Integer totalRounds;

    @Column(nullable = false)
    private int legs = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_scoring_id", nullable = false)
    private RaceScoring raceScoring;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_scoring_id", nullable = false)
    private MatchScoring matchScoring;

    @OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortIndex ASC")
    private List<Matchday> matchdays = new ArrayList<>();

    @OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SeasonDriver> seasonDrivers = new ArrayList<>();

    @OneToMany(mappedBy = "season", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SeasonTeam> seasonTeams = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "season_cars",
            joinColumns = @JoinColumn(name = "season_id"),
            inverseJoinColumns = @JoinColumn(name = "car_id"))
    @OrderBy("manufacturer ASC, name ASC")
    private List<Car> cars = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "season_tracks",
            joinColumns = @JoinColumn(name = "season_id"),
            inverseJoinColumns = @JoinColumn(name = "track_id"))
    @OrderBy("name ASC")
    private List<Track> tracks = new ArrayList<>();

    public Season(String name) {
        this.name = name;
    }

    /**
     * Convenience method: returns the list of Teams participating in this season,
     * ordered by short name. Derived from the seasonTeams association.
     */
    public List<Team> getTeams() {
        return seasonTeams.stream()
                .map(SeasonTeam::getTeam)
                .sorted(java.util.Comparator.comparing(Team::getShortName))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /**
     * Convenience method: adds a team to this season by creating a SeasonTeam entry.
     * Does nothing if the team is already registered for this season.
     */
    public void addTeam(Team team) {
        boolean alreadyPresent = seasonTeams.stream()
                .anyMatch(st -> st.getTeam().equals(team));
        if (!alreadyPresent) {
            seasonTeams.add(new SeasonTeam(this, team));
        }
    }

    /**
     * Convenience method: removes a team from this season by removing the SeasonTeam entry.
     */
    public void removeTeam(Team team) {
        seasonTeams.removeIf(st -> st.getTeam().getId().equals(team.getId()));
    }

    /**
     * Convenience method: removes a team by ID from this season.
     */
    public void removeTeamById(UUID teamId) {
        seasonTeams.removeIf(st -> st.getTeam().getId().equals(teamId));
    }
}
