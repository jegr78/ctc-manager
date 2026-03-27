package de.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Entity
@Table(name = "race_results",
       uniqueConstraints = @UniqueConstraint(columnNames = {"race_id", "driver_id"}))
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"race", "driver"})
public class RaceResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Min(1) @Max(12)
    @Column(nullable = false)
    private int position;

    @Min(1) @Max(12)
    @Column(nullable = false)
    private int qualiPosition;

    @Column(nullable = false)
    private boolean fastestLap = false;

    @Column(nullable = false)
    private int pointsRace;

    @Column(nullable = false)
    private int pointsQuali;

    @Column(nullable = false)
    private int pointsFl;

    @Column(nullable = false)
    private int pointsTotal;

    public RaceResult(Race race, Driver driver, int position, int qualiPosition, boolean fastestLap) {
        this.race = race;
        this.driver = driver;
        this.position = position;
        this.qualiPosition = qualiPosition;
        this.fastestLap = fastestLap;
    }
}
