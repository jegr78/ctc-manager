package org.ctc.admin.service;

import org.ctc.domain.model.*;
import org.ctc.domain.service.ScoringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResultsGraphicServiceTest {

    @TempDir
    Path tempDir;

    private final ScoringService scoringService = mock(ScoringService.class);

    private ResultsGraphicService createService() {
        return new ResultsGraphicService(null, scoringService, tempDir.toString());
    }

    private RaceResult createResult(Race race, String psnId, Team team, int position, int pointsTotal) {
        var driver = new Driver();
        driver.setId(UUID.randomUUID());
        driver.setPsnId(psnId);
        var result = new RaceResult(race, driver, position, position, false);
        result.setPointsTotal(pointsTotal);
        return result;
    }

    @Test
    void givenRaceWithEvenTeams_whenBuildResultRows_thenRowsSortedByPointsDescending() {
        // given
        var service = createService();
        var homeTeam = new Team("Home", "HOM");
        homeTeam.setId(UUID.randomUUID());
        var awayTeam = new Team("Away", "AWY");
        awayTeam.setId(UUID.randomUUID());

        var race = new Race();
        race.setId(UUID.randomUUID());
        var match = new Match(new Matchday(new Season("S"), "MD 1", 1), homeTeam, awayTeam);
        race.setMatch(match);

        var r1 = createResult(race, "HomeTop", homeTeam, 1, 20);
        var r2 = createResult(race, "HomeMid", homeTeam, 3, 14);
        var r3 = createResult(race, "AwayTop", awayTeam, 2, 17);
        var r4 = createResult(race, "AwayMid", awayTeam, 4, 12);
        race.getResults().addAll(List.of(r1, r2, r3, r4));

        when(scoringService.isDriverInTeam(eq(r1), any(), eq(homeTeam.getId()))).thenReturn(true);
        when(scoringService.isDriverInTeam(eq(r2), any(), eq(homeTeam.getId()))).thenReturn(true);
        when(scoringService.isDriverInTeam(eq(r3), any(), eq(homeTeam.getId()))).thenReturn(false);
        when(scoringService.isDriverInTeam(eq(r4), any(), eq(homeTeam.getId()))).thenReturn(false);

        // when
        var rows = service.buildResultRows(race);

        // then
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).homeDriver()).isEqualTo("HomeTop");
        assertThat(rows.get(0).homePoints()).isEqualTo(20);
        assertThat(rows.get(0).awayDriver()).isEqualTo("AwayTop");
        assertThat(rows.get(0).awayPoints()).isEqualTo(17);
        assertThat(rows.get(1).homeDriver()).isEqualTo("HomeMid");
        assertThat(rows.get(1).homePoints()).isEqualTo(14);
        assertThat(rows.get(1).awayDriver()).isEqualTo("AwayMid");
        assertThat(rows.get(1).awayPoints()).isEqualTo(12);
    }

    @Test
    void givenDriversWithEqualPoints_whenBuildResultRows_thenTiebreakerByPosition() {
        // given
        var service = createService();
        var homeTeam = new Team("Home", "HOM");
        homeTeam.setId(UUID.randomUUID());
        var awayTeam = new Team("Away", "AWY");
        awayTeam.setId(UUID.randomUUID());

        var race = new Race();
        race.setId(UUID.randomUUID());
        var match = new Match(new Matchday(new Season("S"), "MD 1", 1), homeTeam, awayTeam);
        race.setMatch(match);

        // Same points, different positions — lower position should come first
        var r1 = createResult(race, "HomeP3", homeTeam, 3, 15);
        var r2 = createResult(race, "HomeP1", homeTeam, 1, 15);
        race.getResults().addAll(List.of(r1, r2));

        when(scoringService.isDriverInTeam(eq(r1), any(), eq(homeTeam.getId()))).thenReturn(true);
        when(scoringService.isDriverInTeam(eq(r2), any(), eq(homeTeam.getId()))).thenReturn(true);

        // when
        var rows = service.buildResultRows(race);

        // then
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).homeDriver()).isEqualTo("HomeP1");
        assertThat(rows.get(1).homeDriver()).isEqualTo("HomeP3");
    }

    @Test
    void givenRaceWithUnevenTeams_whenBuildResultRows_thenLastRowHasEmptyAwaySlot() {
        // given
        var service = createService();
        var homeTeam = new Team("Home", "HOM");
        homeTeam.setId(UUID.randomUUID());
        var awayTeam = new Team("Away", "AWY");
        awayTeam.setId(UUID.randomUUID());

        var race = new Race();
        race.setId(UUID.randomUUID());
        var match = new Match(new Matchday(new Season("S"), "MD 1", 1), homeTeam, awayTeam);
        race.setMatch(match);

        var r1 = createResult(race, "H1", homeTeam, 1, 20);
        var r2 = createResult(race, "H2", homeTeam, 2, 17);
        var r3 = createResult(race, "H3", homeTeam, 3, 14);
        var r4 = createResult(race, "A1", awayTeam, 4, 12);
        var r5 = createResult(race, "A2", awayTeam, 5, 10);
        race.getResults().addAll(List.of(r1, r2, r3, r4, r5));

        when(scoringService.isDriverInTeam(eq(r1), any(), eq(homeTeam.getId()))).thenReturn(true);
        when(scoringService.isDriverInTeam(eq(r2), any(), eq(homeTeam.getId()))).thenReturn(true);
        when(scoringService.isDriverInTeam(eq(r3), any(), eq(homeTeam.getId()))).thenReturn(true);
        when(scoringService.isDriverInTeam(eq(r4), any(), eq(homeTeam.getId()))).thenReturn(false);
        when(scoringService.isDriverInTeam(eq(r5), any(), eq(homeTeam.getId()))).thenReturn(false);

        // when
        var rows = service.buildResultRows(race);

        // then
        assertThat(rows).hasSize(3);
        assertThat(rows.get(2).homeDriver()).isEqualTo("H3");
        assertThat(rows.get(2).homePoints()).isEqualTo(14);
        assertThat(rows.get(2).awayDriver()).isEmpty();
        assertThat(rows.get(2).awayPoints()).isZero();
    }
}
