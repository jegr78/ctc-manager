package org.ctc.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import java.util.UUID;
import lombok.Getter;
import org.ctc.domain.picks.SubmissionReceipt;

@Entity
@Table(name = "matchday_pick_revisions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_pick_revision", columnNames = {"pick_id", "revision_number"}),
        @UniqueConstraint(name = "uq_pick_request", columnNames = "request_id")})
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchdayPickRevision extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pick_id", nullable = false)
    private MatchdayPick pick;
    @Column(nullable = false)
    private int revisionNumber;
    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "trackId", column = @Column(name = "track_id", nullable = false)),
        @AttributeOverride(name = "carId", column = @Column(name = "car_id", nullable = false)),
        @AttributeOverride(name = "laps", column = @Column(name = "laps", nullable = false)),
        @AttributeOverride(name = "tyreWear", column = @Column(name = "tyre_wear", nullable = false)),
        @AttributeOverride(name = "fuelConsumption", column = @Column(name = "fuel_consumption", nullable = false)),
        @AttributeOverride(name = "refuelingSpeed", column = @Column(name = "refueling_speed", nullable = false)),
        @AttributeOverride(name = "initialFuel", column = @Column(name = "initial_fuel", nullable = false, length = 16)),
        @AttributeOverride(name = "requiredPitStops", column = @Column(name = "required_pit_stops", nullable = false)),
        @AttributeOverride(name = "timeProgressionMode", column = @Column(name = "time_progression_mode", nullable = false, length = 24)),
        @AttributeOverride(name = "weather", column = @Column(name = "weather", nullable = false, length = 255)),
        @AttributeOverride(name = "timeOfDay", column = @Column(name = "time_of_day", nullable = false, length = 255)),
        @AttributeOverride(name = "availableTyres", column = @Column(name = "available_tyres", nullable = false, length = 8)),
        @AttributeOverride(name = "mandatoryTyres", column = @Column(name = "mandatory_tyres", nullable = false, length = 8))})
    private PickPayload payload;
    @Convert(converter = UtcInstantConverter.class)
    @Column(nullable = false)
    private Instant confirmedAt;
    @Column(nullable = false, length = 32)
    private String submitterId;
    @Column(nullable = false)
    private UUID requestId;
    @Column(nullable = false)
    private long receiptVersion;

    public MatchdayPickRevision(MatchdayPick pick, int revisionNumber, PickPayload payload, Instant confirmedAt,
                               String submitterId, UUID requestId, long receiptVersion) {
        this.pick = pick;
        this.revisionNumber = revisionNumber;
        this.payload = payload;
        this.confirmedAt = confirmedAt.truncatedTo(ChronoUnit.MICROS);
        this.submitterId = submitterId;
        this.requestId = requestId;
        this.receiptVersion = receiptVersion;
    }

    public SubmissionReceipt receipt() {
        return new SubmissionReceipt(pick.getRace().getId(), requestId, revisionNumber, receiptVersion, confirmedAt, submitterId);
    }
}
