package org.ctc.admin.service;

import org.ctc.admin.dto.RankedTeamData;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PowerRankingsGraphicServiceTest {

    @Mock
    private SeasonRepository seasonRepository;

    @Mock
    private SeasonTeamRepository seasonTeamRepository;

    @TempDir
    Path tempDir;

    private PowerRankingsGraphicService service;

    @BeforeEach
    void setUp() {
        service = new PowerRankingsGraphicService(null, seasonRepository, seasonTeamRepository, tempDir.toString());
    }

    // --- loadTeamsForSeasonGroup ---

    @Test
    void givenSingleSeasonWithStandaloneTeams_whenLoadTeams_thenReturnsAllTeams() {
        // given
        var season = createSeason(2026, 4);
        var teamA = createTeam("Alpha Racing", "ALF");
        var teamB = createTeam("Beta Racing", "BET");
        var stA = createSeasonTeam(season, teamA, 1500);
        var stB = createSeasonTeam(season, teamB, 1400);

        when(seasonRepository.findByYearAndNumber(2026, 4)).thenReturn(List.of(season));
        when(seasonTeamRepository.findBySeasonId(season.getId())).thenReturn(List.of(stA, stB));

        // when
        var result = service.loadTeamsForSeasonGroup(2026, 4);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(RankedTeamData::teamShortName)
                .containsExactly("ALF", "BET");
    }

    @Test
    void givenTeamsWithRatings_whenLoadTeams_thenSortedByRatingDescending() {
        // given
        var season = createSeason(2026, 4);
        var teamLow = createTeam("Low Rating", "LOW");
        var teamHigh = createTeam("High Rating", "HIG");
        var teamMid = createTeam("Mid Rating", "MID");
        var stLow = createSeasonTeam(season, teamLow, 1200);
        var stHigh = createSeasonTeam(season, teamHigh, 1600);
        var stMid = createSeasonTeam(season, teamMid, 1400);

        when(seasonRepository.findByYearAndNumber(2026, 4)).thenReturn(List.of(season));
        when(seasonTeamRepository.findBySeasonId(season.getId())).thenReturn(List.of(stLow, stHigh, stMid));

        // when
        var result = service.loadTeamsForSeasonGroup(2026, 4);

        // then
        assertThat(result).extracting(RankedTeamData::teamShortName)
                .containsExactly("HIG", "MID", "LOW");
    }

    @Test
    void givenTeamsWithoutRating_whenLoadTeams_thenNullRatingsAtEndSortedByShortName() {
        // given
        var season = createSeason(2026, 4);
        var teamRated = createTeam("Rated Team", "RAT");
        var teamZeta = createTeam("Zeta Unrated", "ZET");
        var teamAlpha = createTeam("Alpha Unrated", "ALP");
        var stRated = createSeasonTeam(season, teamRated, 1500);
        var stZeta = createSeasonTeam(season, teamZeta, null);
        var stAlpha = createSeasonTeam(season, teamAlpha, null);

        when(seasonRepository.findByYearAndNumber(2026, 4)).thenReturn(List.of(season));
        when(seasonTeamRepository.findBySeasonId(season.getId())).thenReturn(List.of(stRated, stZeta, stAlpha));

        // when
        var result = service.loadTeamsForSeasonGroup(2026, 4);

        // then
        assertThat(result).extracting(RankedTeamData::teamShortName)
                .containsExactly("RAT", "ALP", "ZET");
    }

    @Test
    void givenParentTeamWithSubTeamsInSeason_whenLoadTeams_thenParentExcluded() {
        // given
        var season = createSeason(2026, 4);
        var parent = createTeam("Neutrals Racing", "TNR");
        var subA = createTeam("Neutrals Racing A", "TNRA");
        subA.setParentTeam(parent);
        parent.getSubTeams().add(subA);
        var subB = createTeam("Neutrals Racing B", "TNRB");
        subB.setParentTeam(parent);
        parent.getSubTeams().add(subB);
        var standalone = createTeam("Solo Racing", "SOL");

        var stParent = createSeasonTeam(season, parent, 1500);
        var stSubA = createSeasonTeam(season, subA, 1400);
        var stSubB = createSeasonTeam(season, subB, 1300);
        var stStandalone = createSeasonTeam(season, standalone, 1200);

        when(seasonRepository.findByYearAndNumber(2026, 4)).thenReturn(List.of(season));
        when(seasonTeamRepository.findBySeasonId(season.getId()))
                .thenReturn(List.of(stParent, stSubA, stSubB, stStandalone));

        // when
        var result = service.loadTeamsForSeasonGroup(2026, 4);

        // then
        assertThat(result).extracting(RankedTeamData::teamShortName)
                .containsExactly("TNRA", "TNRB", "SOL")
                .doesNotContain("TNR");
    }

    @Test
    void givenParentTeamWithNoSubTeamsInSeason_whenLoadTeams_thenParentIncluded() {
        // given
        var season = createSeason(2026, 4);
        var parent = createTeam("Neutrals Racing", "TNR");
        // parent has sub-teams defined but none are in this season
        var subA = createTeam("Neutrals Racing A", "TNRA");
        subA.setParentTeam(parent);
        parent.getSubTeams().add(subA);

        var stParent = createSeasonTeam(season, parent, 1500);

        when(seasonRepository.findByYearAndNumber(2026, 4)).thenReturn(List.of(season));
        when(seasonTeamRepository.findBySeasonId(season.getId())).thenReturn(List.of(stParent));

        // when
        var result = service.loadTeamsForSeasonGroup(2026, 4);

        // then
        assertThat(result).extracting(RankedTeamData::teamShortName)
                .containsExactly("TNR");
    }

    @Test
    void givenMultipleSeasonsWithSameNumber_whenLoadTeams_thenTeamsDeduplicated() {
        // given
        var seasonA = createSeason(2026, 4);
        var seasonB = createSeason(2026, 4);
        var team1 = createTeam("Alpha Racing", "ALF");
        var team2 = createTeam("Beta Racing", "BET");
        var team3 = createTeam("Gamma Racing", "GAM");

        // team1 in both seasons, team2 only in A, team3 only in B
        var stA1 = createSeasonTeam(seasonA, team1, 1500);
        var stA2 = createSeasonTeam(seasonA, team2, 1400);
        var stB1 = createSeasonTeam(seasonB, team1, 1500);
        var stB3 = createSeasonTeam(seasonB, team3, 1300);

        when(seasonRepository.findByYearAndNumber(2026, 4)).thenReturn(List.of(seasonA, seasonB));
        when(seasonTeamRepository.findBySeasonId(seasonA.getId())).thenReturn(List.of(stA1, stA2));
        when(seasonTeamRepository.findBySeasonId(seasonB.getId())).thenReturn(List.of(stB1, stB3));

        // when
        var result = service.loadTeamsForSeasonGroup(2026, 4);

        // then
        assertThat(result).hasSize(3);
        assertThat(result).extracting(RankedTeamData::teamShortName)
                .containsExactly("ALF", "BET", "GAM");
    }

    @Test
    void givenNoSeasonsFound_whenLoadTeams_thenReturnsEmptyList() {
        // given
        when(seasonRepository.findByYearAndNumber(2026, 99)).thenReturn(List.of());

        // when
        var result = service.loadTeamsForSeasonGroup(2026, 99);

        // then
        assertThat(result).isEmpty();
    }

    // --- Template management ---

    @Test
    void givenNoCustomTemplate_whenHasCustomTemplate_thenReturnsFalse() {
        // when / then
        assertThat(service.hasCustomTemplate()).isFalse();
    }

    @Test
    void givenNoCustomTemplate_whenSaveTemplate_thenCustomTemplateExists() throws IOException {
        // when
        service.saveTemplate("<html>custom</html>");

        // then
        assertThat(service.hasCustomTemplate()).isTrue();
        assertThat(service.loadTemplate()).isEqualTo("<html>custom</html>");
    }

    @Test
    void givenSavedCustomTemplate_whenResetTemplate_thenNoCustomTemplate() throws IOException {
        // given
        service.saveTemplate("<html>custom</html>");

        // when
        service.resetTemplate();

        // then
        assertThat(service.hasCustomTemplate()).isFalse();
    }

    @Test
    void whenLoadDefaultTemplate_thenReturnsNonEmptyHtml() throws IOException {
        // when
        String template = service.loadDefaultTemplate();

        // then
        assertThat(template).isNotEmpty();
        assertThat(template).contains("1920px");
        assertThat(template).contains("data.title");
    }

    // --- Helper methods ---

    private Season createSeason(int year, int number) {
        var season = new Season();
        season.setId(UUID.randomUUID());
        season.setName("Test Season");
        season.setYear(year);
        season.setNumber(number);
        return season;
    }

    private Team createTeam(String name, String shortName) {
        var team = new Team(name, shortName);
        team.setId(UUID.randomUUID());
        team.setPrimaryColor("#333333");
        return team;
    }

    private SeasonTeam createSeasonTeam(Season season, Team team, Integer rating) {
        var st = new SeasonTeam(season, team);
        st.setId(UUID.randomUUID());
        st.setRating(rating);
        return st;
    }
}
