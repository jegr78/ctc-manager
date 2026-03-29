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
@Table(name = "playoff_matchups")
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"round", "team1", "team2", "winner", "nextMatchup", "races"})
public class PlayoffMatchup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private PlayoffRound round;

    @Column(name = "bracket_position", nullable = false)
    private int bracketPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team1_id")
    private Team team1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team2_id")
    private Team team2;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private Team winner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "next_matchup_id")
    private PlayoffMatchup nextMatchup;

    private Integer homeScore;

    private Integer awayScore;

    @OneToMany(mappedBy = "playoffMatchup")
    @OrderBy("dateTime ASC NULLS LAST")
    private List<Race> races = new ArrayList<>();

    public PlayoffMatchup(PlayoffRound round, int bracketPosition) {
        this.round = round;
        this.bracketPosition = bracketPosition;
    }

    public boolean isComplete() {
        return winner != null;
    }

    public boolean isReady() {
        return team1 != null && team2 != null;
    }
}
