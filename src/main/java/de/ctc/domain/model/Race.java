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
@Getter @Setter @NoArgsConstructor @ToString(exclude = {"matchday", "match", "track", "car", "results", "playoffMatchup", "attachments"})
public class Race {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matchday_id", nullable = false)
    private Matchday matchday;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;

    private LocalDateTime dateTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    private Track track;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "car_id")
    private Car car;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playoff_matchup_id")
    private PlayoffMatchup playoffMatchup;

    @OneToMany(mappedBy = "race", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<RaceResult> results = new ArrayList<>();

    @OneToMany(mappedBy = "race", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    private List<RaceAttachment> attachments = new ArrayList<>();

    // Convenience delegation methods — resolve teams from Match or PlayoffMatchup
    public Team getHomeTeam() {
        if (match != null) return match.getHomeTeam();
        if (playoffMatchup != null) return playoffMatchup.getTeam1();
        return null;
    }

    public Team getAwayTeam() {
        if (match != null) return match.getAwayTeam();
        if (playoffMatchup != null) return playoffMatchup.getTeam2();
        return null;
    }

    public boolean isBye() {
        return match != null && match.isBye();
    }

    public Integer getHomeScore() {
        if (match != null) return match.getHomeScore();
        if (playoffMatchup != null) return playoffMatchup.getHomeScore();
        return null;
    }

    public Integer getAwayScore() {
        if (match != null) return match.getAwayScore();
        if (playoffMatchup != null) return playoffMatchup.getAwayScore();
        return null;
    }
}
