package org.ctc.domain.model;

import jakarta.persistence.*;
import java.util.UUID;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;

@Entity
@Table(name = "matchday_picks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchdayPick extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false, unique = true)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "season_id", nullable = false)
    private Season season;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_matchday_id", nullable = false)
    private Matchday originalMatchday;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_away_team_id", nullable = false)
    private Team originalAwayTeam;

    @Column(name = "aggregate_version", nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PickState state = PickState.MISSING;

    private Integer latestRevisionNumber;
    private Integer approvedRevisionNumber;

    @Column(nullable = false)
    private boolean manualOpen;

    @Column(nullable = false)
    private boolean manualClosed;

    public MatchdayPick(Race race, Season season, Team team) {
        this.race = race;
        this.season = season;
        this.team = team;
        this.originalMatchday = race.getMatchday();
        this.originalAwayTeam = race.getAwayTeam();
    }

    public boolean matchesTarget(Race target) {
        return target != null && target.getMatchday() != null && target.getHomeTeam() != null && target.getAwayTeam() != null
                && originalMatchday != null && originalAwayTeam != null
                && Objects.equals(race.getId(), target.getId()) && Objects.equals(season.getId(), target.getMatchday().getSeason().getId())
                && Objects.equals(team.getId(), target.getHomeTeam().getId())
                && Objects.equals(originalMatchday.getId(), target.getMatchday().getId())
                && Objects.equals(originalAwayTeam.getId(), target.getAwayTeam().getId());
    }

    public int submit() {
        requireUnapproved();
        if (manualClosed) throw new BusinessRuleException("Pick collection is manually closed");
        latestRevisionNumber = latestRevisionNumber == null ? 1 : latestRevisionNumber + 1;
        state = PickState.SUBMITTED;
        version++;
        return latestRevisionNumber;
    }

    public void requireVersion(long expected) {
        if (version != expected) throw new BusinessRuleException("Pick changed; reload its current version");
    }

    public void openMissing() {
        requireUnapproved();
        if (latestRevisionNumber != null) throw new BusinessRuleException("Only a missing pick can be opened without a return");
        manualOpen = true;
        manualClosed = false;
        version++;
    }

    public void returnForCorrection() {
        requireUnapproved();
        if (latestRevisionNumber == null) throw new BusinessRuleException("A missing pick cannot be returned");
        state = PickState.RETURNED;
        manualOpen = true;
        manualClosed = false;
        version++;
    }

    public void close() {
        requireUnapproved();
        manualOpen = false;
        manualClosed = true;
        version++;
    }

    public void approve(int revision) {
        requireUnapproved();
        if (latestRevisionNumber == null || latestRevisionNumber != revision) {
            throw new BusinessRuleException("Approval requires the exact latest pick revision");
        }
        approvedRevisionNumber = revision;
        state = PickState.APPROVED;
        manualOpen = false;
        manualClosed = false;
        version++;
    }

    public void correct() {
        if (state != PickState.APPROVED) throw new BusinessRuleException("Only approved settings can be corrected");
        version++;
    }

    private void requireUnapproved() {
        if (state == PickState.APPROVED) throw new BusinessRuleException("Approved picks are locked");
    }
}
