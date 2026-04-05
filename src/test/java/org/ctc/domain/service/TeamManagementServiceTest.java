package org.ctc.domain.service;

import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonDriverRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private SeasonTeamRepository seasonTeamRepository;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private TeamManagementService service;

    @Nested
    class PropagateColorsTest {

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
    }

    @Nested
    class PropagateLogoTest {

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
    }

    @Nested
    class FindParentTeamsSortedTest {

        @Test
        void whenFindParentTeamsSorted_thenReturnsOnlyParentTeamsSortedByShortName() {
            // given
            var teamB = createTeam("BRV", "Bravo");
            var teamA = createTeam("ALF", "Alpha");
            var subTeam = createTeam("SUB", "Sub");
            subTeam.setParentTeam(teamA);

            when(teamRepository.findAll()).thenReturn(List.of(teamB, teamA, subTeam));

            // when
            var result = service.findParentTeamsSorted();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).getShortName()).isEqualTo("ALF");
            assertThat(result.get(1).getShortName()).isEqualTo("BRV");
        }
    }

    @Nested
    class FindByIdTest {

        @Test
        void givenExistingId_whenFindById_thenReturnsTeam() {
            // given
            var id = UUID.randomUUID();
            var team = createTeam("TA", "Team A");
            when(teamRepository.findById(id)).thenReturn(Optional.of(team));

            // when
            var result = service.findById(id);

            // then
            assertThat(result).isEqualTo(team);
        }

        @Test
        void givenNonExistentId_whenFindById_thenThrowsEntityNotFoundException() {
            // given
            var id = UUID.randomUUID();
            when(teamRepository.findById(id)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> service.findById(id))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("Team")
                    .hasMessageContaining(id.toString());
        }
    }

    @Nested
    class SaveTest {

        @Test
        void givenNewTeamData_whenSave_thenCreatesTeam() {
            // given
            when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            var result = service.save(null, "New Team", "NT", null, null, null);

            // then
            assertThat(result.getName()).isEqualTo("New Team");
            assertThat(result.getShortName()).isEqualTo("NT");
            verify(teamRepository).save(any(Team.class));
        }

        @Test
        void givenExistingTeamData_whenSave_thenUpdatesAndPropagatesColors() {
            // given
            var id = UUID.randomUUID();
            var existing = createTeam("OT", "Old Team");
            existing.setPrimaryColor("#000000");

            var sub = createTeam("SUB", "Sub");
            sub.setParentTeam(existing);
            existing.setSubTeams(List.of(sub));

            when(teamRepository.findById(id)).thenReturn(Optional.of(existing));
            when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            var result = service.save(id, "Updated Team", "UT", "#FF0000", "#00FF00", "#0000FF");

            // then
            assertThat(result.getName()).isEqualTo("Updated Team");
            assertThat(result.getShortName()).isEqualTo("UT");
            assertThat(result.getPrimaryColor()).isEqualTo("#FF0000");
            verify(teamRepository).save(existing);
        }
    }

    @Nested
    class DeleteTeamTest {

        @Test
        void givenExistingTeam_whenDelete_thenRemoves() {
            // given
            var id = UUID.randomUUID();
            var team = createTeam("TA", "Team A");
            when(teamRepository.findById(id)).thenReturn(Optional.of(team));

            // when
            service.delete(id);

            // then
            verify(teamRepository).delete(team);
            verify(teamRepository).flush();
        }
    }

    @Nested
    class UploadLogoTest {

        @Test
        void givenValidLogo_whenUploadLogo_thenStoresAndPropagates() throws Exception {
            // given
            var id = UUID.randomUUID();
            var team = createTeam("TA", "Team A");
            team.setLogoUrl("/uploads/teams/old.png");

            var sub = createTeam("SUB", "Sub");
            sub.setParentTeam(team);
            team.setSubTeams(List.of(sub));

            var logo = mock(MultipartFile.class);

            when(teamRepository.findById(id)).thenReturn(Optional.of(team));
            when(fileStorageService.storeImage("teams", id, logo)).thenReturn("/uploads/teams/new.png");
            when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            service.uploadLogo(id, logo);

            // then
            verify(fileStorageService).delete("/uploads/teams/old.png");
            verify(fileStorageService).storeImage("teams", id, logo);
            assertThat(team.getLogoUrl()).isEqualTo("/uploads/teams/new.png");
        }

        @Test
        void givenUploadFailure_whenUploadLogo_thenThrowsBusinessRuleException() throws Exception {
            // given
            var id = UUID.randomUUID();
            var team = createTeam("TA", "Team A");
            var logo = mock(MultipartFile.class);

            when(teamRepository.findById(id)).thenReturn(Optional.of(team));
            when(fileStorageService.storeImage("teams", id, logo)).thenThrow(new RuntimeException("IO error"));

            // when / then
            assertThatThrownBy(() -> service.uploadLogo(id, logo))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Logo upload failed");
        }
    }

    @Nested
    class SubTeamTest {

        @Test
        void givenParentTeam_whenAddSubTeam_thenCreatesSubTeam() {
            // given
            var parentId = UUID.randomUUID();
            var parent = createTeam("PAR", "Parent");
            parent.setSubTeams(new ArrayList<>());

            when(teamRepository.findById(parentId)).thenReturn(Optional.of(parent));
            when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

            // when
            var result = service.addSubTeam(parentId, "Sub Team", "SUB");

            // then
            assertThat(result.getName()).isEqualTo("Sub Team");
            assertThat(result.getShortName()).isEqualTo("SUB");
            assertThat(result.getParentTeam()).isEqualTo(parent);
            verify(teamRepository).save(any(Team.class));
        }

        @Test
        void givenSubTeam_whenRemoveSubTeam_thenDeletesSubTeam() {
            // given
            var subId = UUID.randomUUID();
            var sub = createTeam("SUB", "Sub Team");
            when(teamRepository.findById(subId)).thenReturn(Optional.of(sub));

            // when
            service.removeSubTeam(subId);

            // then
            verify(teamRepository).delete(sub);
        }
    }

    @Nested
    class FindSeasonTeamTest {

        @Test
        void givenExistingId_whenFindSeasonTeamById_thenReturnsSeasonTeam() {
            // given
            var id = UUID.randomUUID();
            var season = new Season("Test Season");
            season.setId(UUID.randomUUID());
            var team = createTeam("TST", "Test Team");
            var seasonTeam = new SeasonTeam(season, team);
            seasonTeam.setId(id);

            when(seasonTeamRepository.findById(id)).thenReturn(Optional.of(seasonTeam));

            // when
            var result = service.findSeasonTeamById(id);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(id);
        }

        @Test
        void givenNonExistentId_whenFindSeasonTeamById_thenThrowsEntityNotFoundException() {
            // given
            var id = UUID.randomUUID();
            when(seasonTeamRepository.findById(id)).thenReturn(Optional.empty());

            // when / then
            assertThatThrownBy(() -> service.findSeasonTeamById(id))
                    .isInstanceOf(EntityNotFoundException.class)
                    .hasMessageContaining("SeasonTeam");
        }

        @Test
        void whenFindSeasonTeamsBySeasonId_thenReturnsList() {
            // given
            var seasonId = UUID.randomUUID();
            var season = new Season("Test Season");
            season.setId(seasonId);
            var team = createTeam("TST", "Test Team");
            var seasonTeam = new SeasonTeam(season, team);
            seasonTeam.setId(UUID.randomUUID());

            when(seasonTeamRepository.findBySeasonId(seasonId)).thenReturn(List.of(seasonTeam));

            // when
            var result = service.findSeasonTeamsBySeasonId(seasonId);

            // then
            assertThat(result).hasSize(1);
            verify(seasonTeamRepository).findBySeasonId(seasonId);
        }
    }

    private Team createTeam(String shortName, String name) {
        var team = new Team(name, shortName);
        team.setId(UUID.randomUUID());
        return team;
    }
}
