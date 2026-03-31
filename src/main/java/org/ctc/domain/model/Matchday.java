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
@Table(name = "matchdays")
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"season", "matches", "races"})
public class Matchday extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @NotBlank
    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private int sortIndex;

    @OneToMany(mappedBy = "matchday", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Match> matches = new ArrayList<>();

    @OneToMany(mappedBy = "matchday", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Race> races = new ArrayList<>();

    public Matchday(Season season, String label, int sortIndex) {
        this.season = season;
        this.label = label;
        this.sortIndex = sortIndex;
    }
}
