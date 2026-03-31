package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

@Entity
@Table(name = "season_drivers",
       uniqueConstraints = @UniqueConstraint(columnNames = {"season_id", "driver_id"}))
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"season", "driver", "team"})
public class SeasonDriver extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    public SeasonDriver(Season season, Driver driver, Team team) {
        this.season = season;
        this.driver = driver;
        this.team = team;
    }
}
