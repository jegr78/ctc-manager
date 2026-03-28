package de.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "races")
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"matchday", "homeTeam", "awayTeam", "results", "playoffMatchup"})
public class Race {

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

    private LocalDateTime dateTime;

    private String track;

    private String car;

    @Column(nullable = false)
    private boolean bye = false;

    private Integer homeScore;

    private Integer awayScore;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playoff_matchup_id")
    private PlayoffMatchup playoffMatchup;

    @OneToMany(mappedBy = "race", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<RaceResult> results = new ArrayList<>();

    public Race(Matchday matchday, Team homeTeam, Team awayTeam) {
        this.matchday = matchday;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
    }
}
