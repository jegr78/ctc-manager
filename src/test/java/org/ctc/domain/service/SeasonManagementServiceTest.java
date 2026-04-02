package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.mock.web.MockMultipartFile;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SeasonManagementServiceTest {

    @Mock
    private SeasonRepository seasonRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private CarRepository carRepository;
    @Mock
    private TrackRepository trackRepository;
    @Mock
    private SeasonTeamRepository seasonTeamRepository;
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private SeasonManagementService service;

    @Test
    void givenTeamNotInSeason_whenAddTeamToSeason_thenTeamAdded() {
        // given
        var season = createSeason("Test Season");
        var team = createTeam("TST", "Test Team");

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        // when
        String result = service.addTeamToSeason(season.getId(), team.getId());

        // then
        assertThat(result).isEqualTo("TST");
        assertThat(season.containsTeam(team)).isTrue();
        verify(seasonRepository).save(season);
    }

    @Test
    void givenSubTeam_whenAddTeamToSeason_thenParentAutoAdded() {
        // given
        var season = createSeason("Test Season");
        var parent = createTeam("PAR", "Parent Team");
        var subTeam = createTeam("SUB", "Sub Team");
        subTeam.setParentTeam(parent);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(teamRepository.findById(subTeam.getId())).thenReturn(Optional.of(subTeam));

        // when
        service.addTeamToSeason(season.getId(), subTeam.getId());

        // then
        assertThat(season.containsTeam(parent)).isTrue();
        assertThat(season.containsTeam(subTeam)).isTrue();
        verify(seasonRepository).save(season);
    }

    @Test
    void givenTeamAlreadyInSeason_whenAddTeamToSeason_thenNoOp() {
        // given
        var season = createSeason("Test Season");
        var team = createTeam("TST", "Test Team");
        season.addTeam(team);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        // when
        service.addTeamToSeason(season.getId(), team.getId());

        // then
        verify(seasonRepository, never()).save(any());
    }

    @Test
    void givenTeamInSeason_whenRemoveTeamFromSeason_thenTeamRemoved() {
        // given
        var season = createSeason("Test Season");
        var team = createTeam("TST", "Test Team");
        season.addTeam(team);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        // when
        service.removeTeamFromSeason(season.getId(), team.getId());

        // then
        assertThat(season.containsTeam(team)).isFalse();
        verify(seasonRepository).save(season);
    }

    @Test
    void givenParentTeamWithSubTeams_whenRemoveTeamFromSeason_thenThrowsException() {
        // given
        var season = createSeason("Test Season");
        var parent = createTeam("PAR", "Parent Team");
        var subTeam = createTeam("SUB", "Sub Team");
        subTeam.setParentTeam(parent);
        parent.setSubTeams(List.of(subTeam));
        season.addTeam(parent);
        season.addTeam(subTeam);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(teamRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        // when / then
        assertThatThrownBy(() -> service.removeTeamFromSeason(season.getId(), parent.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot remove parent team PAR");
    }

    @Test
    void givenCarIds_whenAddCarsToSeason_thenCarsAddedToPool() {
        // given
        var season = createSeason("Test Season");
        var car1 = new Car();
        car1.setId(UUID.randomUUID());
        var car2 = new Car();
        car2.setId(UUID.randomUUID());

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(carRepository.findById(car1.getId())).thenReturn(Optional.of(car1));
        when(carRepository.findById(car2.getId())).thenReturn(Optional.of(car2));

        // when
        int added = service.addCarsToSeason(season.getId(), List.of(car1.getId(), car2.getId()));

        // then
        assertThat(added).isEqualTo(2);
        assertThat(season.getCars()).containsExactly(car1, car2);
        verify(seasonRepository).save(season);
    }

    @Test
    void givenCarInPool_whenRemoveCarsFromSeason_thenCarRemovedFromPool() {
        // given
        var season = createSeason("Test Season");
        var car = new Car();
        car.setId(UUID.randomUUID());
        season.getCars().add(car);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

        // when
        int removed = service.removeCarsFromSeason(season.getId(), List.of(car.getId()));

        // then
        assertThat(removed).isEqualTo(1);
        assertThat(season.getCars()).isEmpty();
        verify(seasonRepository).save(season);
    }

    @Test
    void givenSubTeamInSeason_whenRemoveTeamFromSeason_thenParentAutoRemoved() {
        // given
        var season = createSeason("Test Season");
        var parent = createTeam("PAR", "Parent Team");
        var subTeam = createTeam("SUB", "Sub Team");
        subTeam.setParentTeam(parent);
        parent.setSubTeams(List.of(subTeam));
        season.addTeam(parent);
        season.addTeam(subTeam);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(teamRepository.findById(subTeam.getId())).thenReturn(Optional.of(subTeam));

        // when
        service.removeTeamFromSeason(season.getId(), subTeam.getId());

        // then
        assertThat(season.containsTeam(subTeam)).isFalse();
        assertThat(season.containsTeam(parent)).isFalse();
        verify(seasonRepository).save(season);
    }

    @Test
    void givenRatingAndColors_whenUpdateSeasonTeam_thenSetsRatingAndColors() throws Exception {
        // given
        var season = createSeason("Test Season");
        var team = createTeam("TST", "Test Team");
        var seasonTeam = new SeasonTeam(season, team);
        seasonTeam.setId(UUID.randomUUID());

        when(seasonTeamRepository.findById(seasonTeam.getId())).thenReturn(Optional.of(seasonTeam));

        // when
        service.updateSeasonTeam(seasonTeam.getId(), 85, "#ff0000", "#00ff00", "#0000ff", null);

        // then
        assertThat(seasonTeam.getRating()).isEqualTo(85);
        assertThat(seasonTeam.getPrimaryColor()).isEqualTo("#ff0000");
        assertThat(seasonTeam.getSecondaryColor()).isEqualTo("#00ff00");
        assertThat(seasonTeam.getAccentColor()).isEqualTo("#0000ff");
        verify(seasonTeamRepository).save(seasonTeam);
    }

    @Test
    void givenNewLogoFile_whenUpdateSeasonTeam_thenUploadsNewLogo() throws Exception {
        // given
        var season = createSeason("Test Season");
        var team = createTeam("TST", "Test Team");
        var seasonTeam = new SeasonTeam(season, team);
        seasonTeam.setId(UUID.randomUUID());
        seasonTeam.setLogoUrl("/uploads/old-logo.png");

        when(seasonTeamRepository.findById(seasonTeam.getId())).thenReturn(Optional.of(seasonTeam));
        when(fileStorageService.storeImage(eq("season-teams"), eq(seasonTeam.getId()), any()))
                .thenReturn("/uploads/season-teams/" + seasonTeam.getId() + "/new-logo.png");

        var logoFile = new MockMultipartFile("logo", "logo.png", "image/png", new byte[]{1, 2, 3});

        // when
        service.updateSeasonTeam(seasonTeam.getId(), null, null, null, null, logoFile);

        // then
        verify(fileStorageService).delete("/uploads/old-logo.png");
        verify(fileStorageService).storeImage(eq("season-teams"), eq(seasonTeam.getId()), any());
        assertThat(seasonTeam.getLogoUrl()).contains("new-logo.png");
        verify(seasonTeamRepository).save(seasonTeam);
    }

    @Test
    void givenTrackIds_whenAddTracksToSeason_thenTracksAddedToPool() {
        // given
        var season = createSeason("Test Season");
        var track1 = new Track();
        track1.setId(UUID.randomUUID());
        var track2 = new Track();
        track2.setId(UUID.randomUUID());

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(trackRepository.findById(track1.getId())).thenReturn(Optional.of(track1));
        when(trackRepository.findById(track2.getId())).thenReturn(Optional.of(track2));

        // when
        int added = service.addTracksToSeason(season.getId(), List.of(track1.getId(), track2.getId()));

        // then
        assertThat(added).isEqualTo(2);
        assertThat(season.getTracks()).containsExactly(track1, track2);
        verify(seasonRepository).save(season);
    }

    @Test
    void givenTrackInPool_whenRemoveTracksFromSeason_thenTrackRemovedFromPool() {
        // given
        var season = createSeason("Test Season");
        var track = new Track();
        track.setId(UUID.randomUUID());
        season.getTracks().add(track);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

        // when
        int removed = service.removeTracksFromSeason(season.getId(), List.of(track.getId()));

        // then
        assertThat(removed).isEqualTo(1);
        assertThat(season.getTracks()).isEmpty();
        verify(seasonRepository).save(season);
    }

    @Test
    void givenBlankColors_whenUpdateSeasonTeam_thenColorsBecomeNull() throws Exception {
        // given
        var season = createSeason("Test Season");
        var team = createTeam("TST", "Test Team");
        var seasonTeam = new SeasonTeam(season, team);
        seasonTeam.setId(UUID.randomUUID());

        when(seasonTeamRepository.findById(seasonTeam.getId())).thenReturn(Optional.of(seasonTeam));

        // when
        service.updateSeasonTeam(seasonTeam.getId(), 90, "  ", "", null, null);

        // then
        assertThat(seasonTeam.getPrimaryColor()).isNull();
        assertThat(seasonTeam.getSecondaryColor()).isNull();
        assertThat(seasonTeam.getAccentColor()).isNull();
        verify(seasonTeamRepository).save(seasonTeam);
    }

    @Nested
    class ReplaceTeamTest {

        @Test
        void givenValidTeams_whenReplaceTeam_thenSuccessorSetAndTeamAdded() {
            // given
            var season = createSeason("Test Season");
            var predecessor = createTeam("OLD", "Old Team");
            var successor = createTeam("NEW", "New Team");
            season.addTeam(predecessor);

            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
            when(teamRepository.findById(predecessor.getId())).thenReturn(Optional.of(predecessor));
            when(teamRepository.findById(successor.getId())).thenReturn(Optional.of(successor));
            when(seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), successor.getId()))
                    .thenAnswer(inv -> season.findSeasonTeam(successor));

            var replacedAt = LocalDate.of(2026, 3, 15);

            // when
            String result = service.replaceTeam(season.getId(), predecessor.getId(), successor.getId(), replacedAt);

            // then
            assertThat(result).isEqualTo("OLD → NEW");
            assertThat(season.containsTeam(successor)).isTrue();

            var stOld = season.findSeasonTeam(predecessor).orElseThrow();
            assertThat(stOld.isReplaced()).isTrue();
            assertThat(stOld.getReplacedAt()).isEqualTo(replacedAt);
            assertThat(stOld.getSuccessor().getTeam().getId()).isEqualTo(successor.getId());
            verify(seasonRepository).save(season);
        }

        @Test
        void givenPredecessorNotInSeason_whenReplaceTeam_thenThrowsException() {
            // given
            var season = createSeason("Test Season");
            var predecessor = createTeam("OLD", "Old Team");
            var successor = createTeam("NEW", "New Team");

            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
            when(teamRepository.findById(predecessor.getId())).thenReturn(Optional.of(predecessor));
            when(teamRepository.findById(successor.getId())).thenReturn(Optional.of(successor));

            // when / then
            assertThatThrownBy(() -> service.replaceTeam(season.getId(), predecessor.getId(),
                    successor.getId(), LocalDate.of(2026, 3, 15)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not in season");
        }

        @Test
        void givenAlreadyReplacedTeam_whenReplaceTeam_thenThrowsException() {
            // given
            var season = createSeason("Test Season");
            var predecessor = createTeam("OLD", "Old Team");
            var firstSuccessor = createTeam("MID", "Mid Team");
            var secondSuccessor = createTeam("NEW", "New Team");
            season.addTeam(predecessor);
            season.addTeam(firstSuccessor);

            // Already replaced
            var stOld = season.findSeasonTeam(predecessor).orElseThrow();
            var stMid = season.findSeasonTeam(firstSuccessor).orElseThrow();
            stOld.setSuccessor(stMid);

            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
            when(teamRepository.findById(predecessor.getId())).thenReturn(Optional.of(predecessor));
            when(teamRepository.findById(secondSuccessor.getId())).thenReturn(Optional.of(secondSuccessor));

            // when / then
            assertThatThrownBy(() -> service.replaceTeam(season.getId(), predecessor.getId(),
                    secondSuccessor.getId(), LocalDate.of(2026, 3, 15)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("already replaced");
        }

        @Test
        void givenSuccessorAlreadyInSeason_whenReplaceTeam_thenNoDoubleAdd() {
            // given
            var season = createSeason("Test Season");
            var predecessor = createTeam("OLD", "Old Team");
            var successor = createTeam("NEW", "New Team");
            season.addTeam(predecessor);
            season.addTeam(successor);

            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
            when(teamRepository.findById(predecessor.getId())).thenReturn(Optional.of(predecessor));
            when(teamRepository.findById(successor.getId())).thenReturn(Optional.of(successor));
            when(seasonTeamRepository.findBySeasonIdAndTeamId(season.getId(), successor.getId()))
                    .thenAnswer(inv -> season.findSeasonTeam(successor));

            // when
            service.replaceTeam(season.getId(), predecessor.getId(), successor.getId(), LocalDate.of(2026, 3, 15));

            // then — only 2 season teams (no duplicate)
            assertThat(season.getSeasonTeams()).hasSize(2);
        }
    }

    private Season createSeason(String name) {
        var season = new Season(name);
        season.setId(UUID.randomUUID());
        return season;
    }

    private Team createTeam(String shortName, String name) {
        var team = new Team(name, shortName);
        team.setId(UUID.randomUUID());
        return team;
    }
}
