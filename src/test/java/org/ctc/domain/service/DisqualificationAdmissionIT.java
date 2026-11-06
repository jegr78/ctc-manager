package org.ctc.domain.service;

import java.util.List;
import java.util.Map;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.ctc.testsupport.RacePenaltyFixture;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@CtcDevSpringBootContext
@Tag("integration")
class DisqualificationAdmissionIT extends RacePenaltyFixture {
    @Autowired RaceDisqualificationService disqualifications;
    @Autowired RaceRepository raceRows;
    @Autowired RaceLineupService lineupService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlayoffService playoffs;
    @Autowired PlayoffCarSelectionService playoffSelections;
    @Autowired org.ctc.domain.repository.PlayoffMatchupRepository matchups;

    @Test
    void givenDuplicateFinishingPositions_whenSavingNativeResults_thenRejectAmbiguousClassification() {
        // given
        var duplicate = List.of(new RaceService.RaceResultData(homeDriver, null, null, 1, 1, false),
                new RaceService.RaceResultData(awayDriver, null, null, 1, 2, false));
        // when / then
        assertThatThrownBy(() -> races.saveResults(raceId, duplicate))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("unique finishing positions");
        assertScores(80, 70);
    }

    @Test
    void givenHistoricalDuplicateFinishingPositions_whenRecordingDsq_thenRequireClassificationCorrectionFirst() {
        // given
        jdbc.update("update race_results set position=1 where race_id=?", raceId);
        var id = tx.execute(status -> raceRows.findById(raceId).orElseThrow().getResults().getFirst().getId());
        var view = disqualifications.view(raceId, id);
        // when / then
        assertThatThrownBy(() -> disqualifications.save(raceId, id, command(view, true), "test-manager"))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("unique finishing positions");
        assertThat(disqualifications.view(raceId, id).history()).isEmpty(); assertScores(80, 70);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void givenCurrentOrClearedGuestDsq_whenRemovingGuest_thenReturnDomainRejectionBeforeAnyLineupMutation(boolean clear) {
        // given
        var id = tx.execute(status -> raceRows.findById(raceId).orElseThrow().getResults().stream()
                .filter(r -> r.getDriver().getId().equals(awayDriver)).findFirst().orElseThrow().getId());
        disqualifications.save(raceId, id, command(disqualifications.view(raceId, id), true), "test-manager");
        if (clear) disqualifications.save(raceId, id, command(disqualifications.view(raceId, id), false), "test-manager");
        // when / then
        assertThatThrownBy(() -> lineupService.saveLineup(raceId, Map.of(homeDriver, homeId), Map.of()))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("permanent DSQ history");
        tx.executeWithoutResult(status -> {
            assertThat(lineups.findByRaceId(raceId)).hasSize(2);
            assertThat(raceRows.findById(raceId).orElseThrow().getResults()).hasSize(2);
        });
        assertThat(disqualifications.view(raceId, id).history()).hasSize(clear ? 2 : 1);
        assertScores(80, clear ? 70 : 0);
    }

    @Test
    void givenHistoricalDsqFlagWithoutHistory_whenDeletingOrDroppingResults_thenPreserveAmbiguousEvidence() {
        // given
        jdbc.update("update race_results set disqualified=true,points_race=0,points_total=0 where race_id=? and driver_id=?", raceId, homeDriver);
        // when / then
        assertThatThrownBy(() -> races.saveResults(raceId, List.of())).isInstanceOf(BusinessRuleException.class).hasMessageContaining("DSQ");
        assertThatThrownBy(() -> races.deleteRace(raceId)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("DSQ");
        assertThat(jdbc.queryForObject("select count(*) from race_results where race_id=? and disqualified=true", Integer.class, raceId)).isEqualTo(1);
    }

    @Test
    void givenOverrideLegWithDsqStateButNoHistory_whenChangingPlayoffParentParticipants_thenRejectBeforeMutation() {
        // given
        var matchupId = tx.execute(status -> {
            var playoff = playoffs.createPlayoff(seasonId, "Test DSQ parent identity", 2);
            var matchup = matchups.findByRoundPlayoffId(playoff.getId()).getFirst();
            matchup.setTeam1(teams.findById(homeId).orElseThrow()); matchup.setTeam2(teams.findById(awayId).orElseThrow());
            var race = raceRows.findById(raceId).orElseThrow(); race.setPlayoffMatchup(matchup);
            race.setHomeTeamOverride(matchup.getTeam1()); race.setAwayTeamOverride(matchup.getTeam2()); return matchup.getId();
        });
        jdbc.update("update race_results set disqualified=true,points_race=0,points_total=0 where race_id=? and driver_id=?", raceId, homeDriver);
        // when / then
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            var matchup = matchups.findById(matchupId).orElseThrow();
            playoffSelections.requireValid(matchup.getRound().getPlayoff(), List.of(new PlayoffCarSelectionService.Participants(
                    matchup, teams.findById(awayId).orElseThrow(), teams.findById(homeId).orElseThrow())));
            matchup.setTeam1(teams.findById(awayId).orElseThrow()); matchup.setTeam2(teams.findById(homeId).orElseThrow());
        })).isInstanceOf(BusinessRuleException.class).hasMessageContaining("DSQ");
        tx.executeWithoutResult(status -> {
            var matchup = matchups.findById(matchupId).orElseThrow(); assertThat(matchup.getTeam1().getId()).isEqualTo(homeId);
            assertThat(matchup.getTeam2().getId()).isEqualTo(awayId);
        });
        assertThat(jdbc.queryForObject("select count(*) from race_results where race_id=? and disqualified=true", Integer.class, raceId)).isEqualTo(1);
    }

    private RaceDisqualificationService.Correction command(RaceDisqualificationService.View view, boolean disqualified) {
        return new RaceDisqualificationService.Correction(disqualified, view.revision(), view.fingerprint(), view.generation(), "Test guest decision");
    }
}
