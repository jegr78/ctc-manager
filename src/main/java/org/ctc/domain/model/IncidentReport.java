package org.ctc.domain.model;

import jakarta.persistence.*;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "incident_reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentReport extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "window_id", nullable = false) private IncidentReportWindow window;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "race_id", nullable = false) private Race race;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "original_matchday_id", nullable = false) private Matchday originalMatchday;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "original_home_team_id", nullable = false) private Team originalHomeTeam;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "original_away_team_id", nullable = false) private Team originalAwayTeam;
    @Column(nullable = false, unique = true, length = 64) private String caseReference;
    @Column(nullable = false, length = 32) private String authorId;
    @Embedded private IncidentParty party;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private IncidentReportState state = IncidentReportState.ACTIVE;
    @Column(name = "aggregate_version", nullable = false) private long version;
    @Column(nullable = false) private int latestRevisionNumber;
    private Integer frozenRevisionNumber;

    public IncidentReport(IncidentReportWindow window, Race race, String authorId, IncidentParty party) {
        this.window = window; this.race = race; this.authorId = authorId; this.party = party;
        this.originalMatchday = race.getMatchday(); this.originalHomeTeam = race.getHomeTeam(); this.originalAwayTeam = race.getAwayTeam();
        this.caseReference = "INC-" + UUID.randomUUID().toString().toUpperCase(java.util.Locale.ROOT);
    }

    public void requireVersion(long expected) {
        if (version != expected) throw new org.ctc.domain.exception.BusinessRuleException("Incident report changed; reload its current version");
    }
    public void appendRevision() {
        requireActive(); latestRevisionNumber++; version++;
    }
    public void withdraw() { requireActive(); state = IncidentReportState.WITHDRAWN; version++; }
    public void freeze() { if (state == IncidentReportState.ACTIVE) frozenRevisionNumber = latestRevisionNumber; }
    private void requireActive() {
        if (state != IncidentReportState.ACTIVE) throw new org.ctc.domain.exception.BusinessRuleException("A withdrawn incident report cannot be changed");
    }
}
