package org.ctc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.ctc.domain.exception.BusinessRuleException;

/** The lineup one team submitted for one leg; revisions are immutable and management applies one of them. */
@Entity
@Table(name = "lineup_submissions", uniqueConstraints = @UniqueConstraint(name = "uq_lineup_submission_target", columnNames = {"race_id", "team_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineupSubmission extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "race_id", nullable = false, updatable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false, updatable = false)
    private Team team;

    @Column(name = "latest_revision_number", nullable = false)
    private int latestRevisionNumber;

    @Column(name = "applied_revision_number")
    private Integer appliedRevisionNumber;

    @Convert(converter = UtcInstantConverter.class)
    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "applied_by", length = 128)
    private String appliedBy;

    @Column(nullable = false)
    private long version;

    public LineupSubmission(Race race, Team team) {
        this.race = race;
        this.team = team;
    }

    public void requireVersion(long expected) {
        if (version != expected) throw new BusinessRuleException("The lineup submission changed; reload its current version");
    }

    /** Starts the next revision and returns its number. */
    public int submit() {
        latestRevisionNumber++;
        version++;
        return latestRevisionNumber;
    }

    public void apply(int revisionNumber, Instant at, String actor) {
        if (revisionNumber < 1 || revisionNumber > latestRevisionNumber) throw new BusinessRuleException("Unknown lineup revision");
        appliedRevisionNumber = revisionNumber;
        appliedAt = at.truncatedTo(ChronoUnit.MICROS);
        appliedBy = actor;
        version++;
    }

    public LineupStatus status() {
        if (appliedRevisionNumber == null) return LineupStatus.SUBMITTED;
        return appliedRevisionNumber == latestRevisionNumber ? LineupStatus.APPLIED : LineupStatus.SUBMITTED_AFTER_APPLY;
    }
}
