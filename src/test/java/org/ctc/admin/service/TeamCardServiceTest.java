package org.ctc.admin.service;

import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TeamCardServiceTest {

    private final TeamCardService service = new TeamCardService(null, null, "uploads");

    @Test
    void givenTeamWithSimpleShortName_whenGetCardPath_thenReturnsExpectedPath() {
        // given
        var season = new Season("Season 4 - 2026");
        season.setId(UUID.randomUUID());
        var team = new Team("Test Team", "TST");
        team.setPrimaryColor("#ff0000");

        var seasonTeam = new SeasonTeam(season, team);

        // when / then
        assertThat(service.getCardPath(seasonTeam))
                .isEqualTo("/uploads/team-cards/" + season.getId() + "/TST.png");
    }

    @Test
    void givenTeamShortNameWithSpaces_whenGetCardPath_thenSpacesAreSanitized() {
        // given
        var season = new Season("Season 4");
        season.setId(UUID.randomUUID());
        var team = new Team("Community League Racing 1", "CLR 1");
        team.setPrimaryColor("#2196f3");

        var seasonTeam = new SeasonTeam(season, team);

        // when / then
        assertThat(service.getCardPath(seasonTeam))
                .contains("CLR_1.png");
    }

    @Test
    void givenThreeColorsWithOneDark_whenComputeGradientColor_thenPicksDarkestColor() {
        // given
        // Dark blue is darkest (lowest luminance)

        // when / then
        assertThat(service.computeGradientColor("#1a1a2e", "#e0e0e0", "#ff6600"))
                .isEqualTo("#1a1a2e");
    }

    @Test
    void givenThreeBrightColors_whenComputeGradientColor_thenPicksLowestLuminance() {
        // given
        // Red (#ff0000) has lower luminance than yellow (#ffff00) and cyan (#00ffff)

        // when / then
        assertThat(service.computeGradientColor("#ffff00", "#00ffff", "#ff0000"))
                .isEqualTo("#ff0000");
    }

    @Test
    void givenTwoNullColors_whenComputeGradientColor_thenReturnsOnlyNonNullColor() {
        // when / then
        assertThat(service.computeGradientColor("#336699", null, null))
                .isEqualTo("#336699");
    }

    @Test
    void givenNoCardFileOnDisk_whenCardExists_thenReturnsFalse() {
        // given
        var season = new Season("Season 4");
        season.setId(UUID.randomUUID());
        var team = new Team("Test", "TST");

        var seasonTeam = new SeasonTeam(season, team);

        // when / then
        assertThat(service.cardExists(seasonTeam)).isFalse();
    }
}
