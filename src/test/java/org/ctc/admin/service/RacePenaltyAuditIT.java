package org.ctc.admin.service;

import org.ctc.domain.service.RacePenaltyService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.RacePenaltyFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Tag("integration")
class RacePenaltyAuditIT extends RacePenaltyFixture {
    @Autowired DataAuditService audit;
    @Autowired JdbcTemplate jdbc;

    @Test
    void givenValidNettedScore_whenAuditing_thenDoNotReportFalseStaleAggregate() {
        // given
        correct();
        // when
        var report = audit.audit();
        // then
        assertThat(report.sections().stream().flatMap(section -> section.findings().stream())
                .filter(finding -> finding.subject().contains(matchId.toString()) || finding.subject().contains(raceId.toString())))
                .noneMatch(finding -> finding.category().name().equals("TEAM_PENALTIES") || finding.category().name().equals("STALE_AGGREGATE"));
        assertScores(70, 70);
    }

    @Test
    void givenInvalidHistoricalFingerprint_whenAuditing_thenReportAndSkipUnsafeAggregationWithoutRepair() {
        // given
        correct();
        jdbc.update("update race_penalty_revisions set result_fingerprint = ? where race_id = ?", "z".repeat(64), raceId);
        // when
        var report = audit.audit();
        // then
        assertThat(report.sections().stream().flatMap(section -> section.findings().stream())
                .filter(finding -> finding.subject().contains(raceId.toString())))
                .anyMatch(finding -> finding.category().name().equals("TEAM_PENALTIES") && finding.evidence().contains("fingerprint"));
        assertThat(jdbc.queryForObject("select result_fingerprint from race_penalty_revisions where race_id = ?", String.class, raceId)).isEqualTo("z".repeat(64));
        assertScores(70, 70);
    }

    @Test
    void givenRaceAndCapturedPenaltyReassignedToForeignTeam_whenAuditing_thenReportWithoutCrashingOrRepairing() {
        // given
        correct();
        var stranger = teams.findAll().stream().filter(team -> !team.getId().equals(homeId) && !team.getId().equals(awayId)).findFirst().orElseThrow().getId();
        jdbc.update("update races set home_team_id = ? where id = ?", stranger, raceId);
        jdbc.update("update race_penalty_revisions set home_team_id = ? where race_id = ?", stranger, raceId);
        // when
        var report = audit.audit();
        // then
        assertThat(report.sections().stream().flatMap(section -> section.findings().stream()).filter(finding -> finding.subject().contains(raceId.toString())))
                .anyMatch(finding -> finding.category().name().equals("TEAM_PENALTIES") && finding.evidence().contains("participants"));
        assertThat(jdbc.queryForObject("select home_team_id from races where id = ?", java.util.UUID.class, raceId)).isEqualTo(stranger);
        assertScores(70, 70);
    }

    @Test
    void givenNonparticipantMatchWalkover_whenAuditing_thenReportWithoutChangingMarker() {
        // given
        var other = teams.findAll().stream().map(org.ctc.domain.model.Team::getId)
                .filter(id -> !id.equals(homeId) && !id.equals(awayId)).findFirst().orElseThrow();
        jdbc.update("update matches set walkover_team_id = ? where id = ?", other, matchId);
        // when
        var report = audit.audit();
        // then
        assertThat(report.sections().stream().flatMap(section -> section.findings().stream())
                .filter(finding -> finding.subject().contains(matchId.toString())))
                .anyMatch(finding -> finding.category().name().equals("PAIRING") && finding.evidence().contains("walkover"));
        assertThat(jdbc.queryForObject("select walkover_team_id from matches where id = ?", java.util.UUID.class, matchId)).isEqualTo(other);
    }

    private void correct() {
        var view = penalties.view(raceId);
        penalties.save(raceId, new RacePenaltyService.Correction(homeId, awayId, 20, 10, view.revision(),
                view.fingerprint(), view.generation(), "Private steward evidence"), "test-manager");
    }
}
