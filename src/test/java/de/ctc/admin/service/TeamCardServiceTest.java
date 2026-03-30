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
    void cardExists_returnsFalseWhenNoFile() {
        var season = new Season("Season 4");
        season.setId(UUID.randomUUID());
        var team = new Team("Test", "TST");

        var seasonTeam = new SeasonTeam(season, team);

        assertThat(service.cardExists(seasonTeam)).isFalse();
    }
}
