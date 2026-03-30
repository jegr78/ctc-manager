package de.ctc.admin.service;

import de.ctc.domain.model.Season;
import de.ctc.domain.model.SeasonTeam;
import de.ctc.domain.model.Team;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TeamCardServiceTest {

    private final TeamCardService service = new TeamCardService(null, null, "uploads");

    @Test
    void getCardPath_returnsExpectedPath() {
        var season = new Season("Season 4 - 2026");
        season.setId(UUID.randomUUID());
        var team = new Team("Test Team", "TST");
        team.setPrimaryColor("#ff0000");

        var seasonTeam = new SeasonTeam(season, team);

        assertThat(service.getCardPath(seasonTeam))
                .isEqualTo("/uploads/team-cards/" + season.getId() + "/TST.png");
    }

    @Test
    void getCardPath_sanitizesSpacesInShortName() {
        var season = new Season("Season 4");
        season.setId(UUID.randomUUID());
        var team = new Team("Community League Racing 1", "CLR 1");
        team.setPrimaryColor("#2196f3");

        var seasonTeam = new SeasonTeam(season, team);

        assertThat(service.getCardPath(seasonTeam))
                .contains("CLR_1.png");
    }

    @Test
    void computeGradientColor_picksDarkestColor() {
        // Dark blue is darkest (lowest luminance)
        assertThat(service.computeGradientColor("#1a1a2e", "#e0e0e0", "#ff6600"))
                .isEqualTo("#1a1a2e");
    }

    @Test
    void computeGradientColor_picksDarkestFromBrightSet() {
        // Red (#ff0000) has lower luminance than yellow (#ffff00) and cyan (#00ffff)
        assertThat(service.computeGradientColor("#ffff00", "#00ffff", "#ff0000"))
                .isEqualTo("#ff0000");
    }

    @Test
    void computeGradientColor_handlesNullColors() {
        assertThat(service.computeGradientColor("#336699", null, null))
                .isEqualTo("#336699");
    }

    @Test
    void cardExists_returnsFalseWhenNoFile() {
        var season = new Season("Season 4");
        season.setId(UUID.randomUUID());
        var team = new Team("Test", "TST");

        var seasonTeam = new SeasonTeam(season, team);

        assertThat(service.cardExists(seasonTeam)).isFalse();
    }
}
