package org.ctc.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.repository.SeasonRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class TestDataServiceIntegrationTest {

    @Autowired
    private SeasonRepository seasonRepository;

    // --- Helper methods ---

    private Season findSeason(int year, String name) {
        return seasonRepository.findAll().stream()
                .filter(s -> s.getYear() == year && s.getName().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "Season not found: year=" + year + " name=" + name));
    }

    private Season findSeason(int year, int number) {
        return seasonRepository.findAll().stream()
                .filter(s -> s.getYear() == year && s.getNumber() == number)
                .findFirst().orElseThrow(() -> new AssertionError(
                        "Season not found: year=" + year + " number=" + number));
    }

    // --- Task 1 Phase 23: Season structure tests ---

    @Test
    void givenDevSeed_whenStarted_thenS1GroupAHasFormatRoundRobin() {
        // given
        var season = findSeason(2023, "Group A");

        // then
        assertThat(season.getFormat()).isEqualTo(SeasonFormat.ROUND_ROBIN);
    }

    @Test
    void givenDevSeed_whenStarted_thenS1GroupBHasFormatRoundRobin() {
        // given
        var season = findSeason(2023, "Group B");

        // then
        assertThat(season.getFormat()).isEqualTo(SeasonFormat.ROUND_ROBIN);
    }

    @Test
    void givenDevSeed_whenStarted_thenS2HasFormatSwiss() {
        // given
        var season = findSeason(2024, "Regular Season");

        // then
        assertThat(season.getFormat()).isEqualTo(SeasonFormat.SWISS);
    }

    @Test
    void givenDevSeed_whenStarted_thenS4HasFormatLeague() {
        // given
        var season = findSeason(2026, 4);

        // then
        assertThat(season.getFormat()).isEqualTo(SeasonFormat.LEAGUE);
    }

    @Test
    void givenDevSeed_whenStarted_thenS1GroupAHasSixTeams() {
        // given
        var season = findSeason(2023, "Group A");

        // then
        assertThat(season.getSeasonTeams()).hasSize(6);
    }

    @Test
    void givenDevSeed_whenStarted_thenS1GroupBHasSixTeams() {
        // given
        var season = findSeason(2023, "Group B");

        // then
        assertThat(season.getSeasonTeams()).hasSize(6);
    }

    @Test
    void givenDevSeed_whenStarted_thenS4HasFourteenMatchTeams() {
        // given
        var season = findSeason(2026, 4);

        // when
        var teams = season.getSeasonTeams();

        // then
        assertThat(teams).hasSize(14);
        // CLR, TNR, AHR parents should NOT be present (only their sub-teams)
        var teamShortNames = teams.stream()
                .map(st -> st.getTeam().getShortName())
                .toList();
        assertThat(teamShortNames).doesNotContain("CLR", "TNR", "AHR");
    }

    @Test
    void givenDevSeed_whenStarted_thenS1GroupsContainSubTeams() {
        // given
        var groupA = findSeason(2023, "Group A");
        var groupB = findSeason(2023, "Group B");

        // then - at least one sub-team in each group
        var groupATeams = groupA.getSeasonTeams().stream()
                .map(st -> st.getTeam())
                .toList();
        assertThat(groupATeams).anyMatch(t -> t.getParentTeam() != null);

        var groupBTeams = groupB.getSeasonTeams().stream()
                .map(st -> st.getTeam())
                .toList();
        assertThat(groupBTeams).anyMatch(t -> t.getParentTeam() != null);
    }
}
