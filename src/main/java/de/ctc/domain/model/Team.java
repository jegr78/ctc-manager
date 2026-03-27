package de.ctc.domain.model;

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
@Table(name = "teams")
@Getter @Setter @NoArgsConstructor @ToString(exclude = "seasonDrivers")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotBlank
    @Column(nullable = false)
    private String name;

    @NotBlank
    @Column(nullable = false, unique = true)
    private String shortName;

    private String logoUrl;

    @OneToMany(mappedBy = "team")
    private List<SeasonDriver> seasonDrivers = new ArrayList<>();

    public Team(String name, String shortName) {
        this.name = name;
        this.shortName = shortName;
    }
}
