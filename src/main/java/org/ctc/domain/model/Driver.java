package org.ctc.domain.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "drivers")
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"seasonDrivers", "raceResults"})
public class Driver extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String psnId;

    @NotBlank
    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "driver")
    private List<SeasonDriver> seasonDrivers = new ArrayList<>();

    @OneToMany(mappedBy = "driver")
    private List<RaceResult> raceResults = new ArrayList<>();

    public Driver(String psnId, String nickname) {
        this.psnId = psnId;
        this.nickname = nickname;
    }
}
