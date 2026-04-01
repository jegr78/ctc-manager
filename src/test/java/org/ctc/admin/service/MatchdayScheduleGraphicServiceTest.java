package org.ctc.admin.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.service.StandingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MatchdayScheduleGraphicServiceTest {

    @TempDir
    Path tempDir;

    private MatchdayScheduleGraphicService createService() {
        return new MatchdayScheduleGraphicService(null, null, null, tempDir.toString());
    }

    @Test
    void givenNoCustomTemplate_whenHasCustomTemplate_thenReturnsFalse() {
        assertThat(createService().hasCustomTemplate()).isFalse();
    }

    @Test
    void givenSavedTemplate_whenLoadTemplate_thenReturnsCustomContent() throws IOException {
        var service = createService();
        service.saveTemplate("<html>custom schedule</html>");
        assertThat(service.hasCustomTemplate()).isTrue();
        assertThat(service.loadTemplate()).isEqualTo("<html>custom schedule</html>");
    }

    @Test
    void givenMatchesWithDifferentDateTimes_whenPrepareBaseContext_thenSortedByDateTime() {
        // given
        var standingsService = mock(StandingsService.class);
        var seasonTeamRepository = mock(SeasonTeamRepository.class);
        var service = new MatchdayScheduleGraphicService(null, standingsService, seasonTeamRepository, tempDir.toString());

        var season = new Season("CTC", 2026, 1);
        season.setId(UUID.randomUUID());

        var teamA = new Team("Team A", "TA");
        teamA.setId(UUID.randomUUID());
        var teamB = new Team("Team B", "TB");
        teamB.setId(UUID.randomUUID());
        var teamC = new Team("Team C", "TC");
        teamC.setId(UUID.randomUUID());
        var teamD = new Team("Team D", "TD");
        teamD.setId(UUID.randomUUID());
        var teamE = new Team("Team E", "TE");
        teamE.setId(UUID.randomUUID());
        var teamF = new Team("Team F", "TF");
        teamF.setId(UUID.randomUUID());
        season.addTeam(teamA);
        season.addTeam(teamB);
        season.addTeam(teamC);
        season.addTeam(teamD);
        season.addTeam(teamE);
        season.addTeam(teamF);

        var matchday = new Matchday(season, "MD 1", 1);
        matchday.setId(UUID.randomUUID());

        // Match 1: Friday
        var match1 = new Match(matchday, teamA, teamB);
        match1.setId(UUID.randomUUID());
        var race1 = new Race();
        race1.setId(UUID.randomUUID());
        race1.setDateTime(LocalDateTime.of(2026, 3, 20, 19, 30));
        match1.getRaces().add(race1);

        // Match 2: no dateTime
        var match2 = new Match(matchday, teamC, teamD);
        match2.setId(UUID.randomUUID());

        // Match 3: Wednesday (earlier)
        var match3 = new Match(matchday, teamE, teamF);
        match3.setId(UUID.randomUUID());
        var race3 = new Race();
        race3.setId(UUID.randomUUID());
        race3.setDateTime(LocalDateTime.of(2026, 3, 18, 19, 0));
        match3.getRaces().add(race3);

        matchday.getMatches().addAll(List.of(match1, match2, match3));

        when(standingsService.calculateStandings(season.getId())).thenReturn(List.of());
        when(seasonTeamRepository.findBySeasonId(season.getId()))
                .thenReturn(List.copyOf(season.getSeasonTeams()));

        // when
        var data = service.prepareBaseContext(matchday);

        // then — sorted: Wed (TE), Fri (TA), null (TC)
        assertThat(data.matches()).hasSize(3);
        assertThat(data.matches().get(0).homeTeamShortName()).isEqualTo("TE");
        assertThat(data.matches().get(1).homeTeamShortName()).isEqualTo("TA");
        assertThat(data.matches().get(2).homeTeamShortName()).isEqualTo("TC");
        assertThat(data.matches().get(2).scheduledDateTime()).isNull();
    }
}
