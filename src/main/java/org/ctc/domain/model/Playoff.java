package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "playoffs")
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"season", "seasons", "rounds", "seeds"})
public class Playoff extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false, unique = true)
    private Season season;

    @NotBlank
    @Column(nullable = false)
    private String name;

    private LocalDate startDate;

    private LocalDate endDate;

    @Column(name = "event_duration_minutes")
    private Integer eventDurationMinutes;

    @ManyToMany
    @JoinTable(name = "playoff_seasons",
            joinColumns = @JoinColumn(name = "playoff_id"),
            inverseJoinColumns = @JoinColumn(name = "season_id"))
    @OrderBy("name ASC")
    private List<Season> seasons = new ArrayList<>();

    @OneToMany(mappedBy = "playoff", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("roundIndex ASC")
    private List<PlayoffRound> rounds = new ArrayList<>();

    @OneToMany(mappedBy = "playoff", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("seed ASC")
    private List<PlayoffSeed> seeds = new ArrayList<>();

    public Playoff(Season season, String name) {
        this.season = season;
        this.name = name;
    }
}
