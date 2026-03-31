package org.ctc.domain.service;

import org.ctc.domain.model.Team;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeamManagementServiceTest {

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private SeasonRepository seasonRepository;
    @Mock
    private RaceLineupRepository raceLineupRepository;
    @Mock
    private SeasonDriverRepository seasonDriverRepository;

    @InjectMocks
    private TeamManagementService service;

    @Test
    void givenSubTeamWithNoColors_whenPropagateColorsToSubTeams_thenSubTeamColorsSet() {
        // given
        var parent = createTeam("PAR", "Parent");
        parent.setPrimaryColor("#FF0000");
        parent.setSecondaryColor("#00FF00");
        parent.setAccentColor("#0000FF");

        var sub = createTeam("SUB", "Sub Team");
        sub.setParentTeam(parent);
        parent.setSubTeams(List.of(sub));

        // when
        service.propagateColorsToSubTeams(parent);

        // then
        assertThat(sub.getPrimaryColor()).isEqualTo("#FF0000");
        assertThat(sub.getSecondaryColor()).isEqualTo("#00FF00");
        assertThat(sub.getAccentColor()).isEqualTo("#0000FF");
        verify(teamRepository).save(sub);
    }

    @Test
    void givenSubTeamWithExistingColors_whenPropagateColorsToSubTeams_thenColorsNotOverwritten() {
        // given
        var parent = createTeam("PAR", "Parent");
        parent.setPrimaryColor("#FF0000");
        parent.setSecondaryColor("#00FF00");
        parent.setAccentColor("#0000FF");

        var sub = createTeam("SUB", "Sub Team");
        sub.setParentTeam(parent);
        sub.setPrimaryColor("#111111");
        sub.setSecondaryColor("#222222");
        sub.setAccentColor("#333333");
        parent.setSubTeams(List.of(sub));

        // when
        service.propagateColorsToSubTeams(parent);

        // then
        assertThat(sub.getPrimaryColor()).isEqualTo("#111111");
        assertThat(sub.getSecondaryColor()).isEqualTo("#222222");
        assertThat(sub.getAccentColor()).isEqualTo("#333333");
        verify(teamRepository, never()).save(any());
    }

    @Test
    void givenSubTeamWithNoLogo_whenPropagateLogoToSubTeams_thenLogoSet() {
        // given
        var parent = createTeam("PAR", "Parent");
        var sub = createTeam("SUB", "Sub Team");
        sub.setParentTeam(parent);
        parent.setSubTeams(List.of(sub));

        // when
        service.propagateLogoToSubTeams(parent, "/uploads/teams/logo.png");

        // then
        assertThat(sub.getLogoUrl()).isEqualTo("/uploads/teams/logo.png");
        verify(teamRepository).save(sub);
    }

    @Test
    void givenSubTeamWithExistingLogo_whenPropagateLogoToSubTeams_thenLogoNotOverwritten() {
        // given
        var parent = createTeam("PAR", "Parent");
        var sub = createTeam("SUB", "Sub Team");
        sub.setParentTeam(parent);
        sub.setLogoUrl("/uploads/teams/existing.png");
        parent.setSubTeams(List.of(sub));

        // when
        service.propagateLogoToSubTeams(parent, "/uploads/teams/logo.png");

        // then
        assertThat(sub.getLogoUrl()).isEqualTo("/uploads/teams/existing.png");
        verify(teamRepository, never()).save(any());
    }

    private Team createTeam(String shortName, String name) {
        var team = new Team(name, shortName);
        team.setId(UUID.randomUUID());
        return team;
    }
}
