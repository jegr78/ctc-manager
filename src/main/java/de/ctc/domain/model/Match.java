package de.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "matches")
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"matchday", "homeTeam", "awayTeam", "races"})
public class Match {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matchday_id", nullable = false)
    private Matchday matchday;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id")
    private Team awayTeam;

    private Integer homeScore;

    private Integer awayScore;

    @Column(nullable = false)
    private boolean bye = false;

    @OneToMany(mappedBy = "match", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dateTime ASC NULLS LAST")
    private List<Race> races = new ArrayList<>();

    public Match(Matchday matchday, Team homeTeam, Team awayTeam) {
        this.matchday = matchday;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
    }
}
