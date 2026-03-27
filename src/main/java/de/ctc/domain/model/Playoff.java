package de.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "playoffs")
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"season", "rounds"})
public class Playoff {

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

    @Column(name = "best_of_legs", nullable = false)
    private int bestOfLegs = 2;

    @OneToMany(mappedBy = "playoff", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("roundIndex ASC")
    private List<PlayoffRound> rounds = new ArrayList<>();

    public Playoff(Season season, String name, int bestOfLegs) {
        this.season = season;
        this.name = name;
        this.bestOfLegs = bestOfLegs;
    }
}
