package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Entity
@Table(name = "match_scorings")
@Getter @Setter @NoArgsConstructor @ToString
public class MatchScoring extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "points_win", nullable = false)
    private int pointsWin;

    @Column(name = "points_draw", nullable = false)
    private int pointsDraw;

    @Column(name = "points_loss", nullable = false)
    private int pointsLoss;

    public MatchScoring(String name, int pointsWin, int pointsDraw, int pointsLoss) {
        this.name = name;
        this.pointsWin = pointsWin;
        this.pointsDraw = pointsDraw;
        this.pointsLoss = pointsLoss;
    }
}
