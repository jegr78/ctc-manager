package org.ctc.domain.service;

import org.ctc.domain.model.Car;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    void addTeamToSeason_addsTeamToSeason() {
        var season = createSeason("Test Season");
        var team = createTeam("TST", "Test Team");

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        String result = service.addTeamToSeason(season.getId(), team.getId());

        assertThat(result).isEqualTo("TST");
        assertThat(season.containsTeam(team)).isTrue();
        verify(seasonRepository).save(season);
    }

    @Test
    void addTeamToSeason_subTeamAutoAddsParent() {
        var season = createSeason("Test Season");
        var parent = createTeam("PAR", "Parent Team");
        var subTeam = createTeam("SUB", "Sub Team");
        subTeam.setParentTeam(parent);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(teamRepository.findById(subTeam.getId())).thenReturn(Optional.of(subTeam));

        service.addTeamToSeason(season.getId(), subTeam.getId());

        assertThat(season.containsTeam(parent)).isTrue();
        assertThat(season.containsTeam(subTeam)).isTrue();
        verify(seasonRepository).save(season);
    }

    @Test
    void addTeamToSeason_alreadyPresentIsNoOp() {
        var season = createSeason("Test Season");
        var team = createTeam("TST", "Test Team");
        season.addTeam(team);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        service.addTeamToSeason(season.getId(), team.getId());

        verify(seasonRepository, never()).save(any());
    }

    @Test
    void removeTeamFromSeason_removesTeam() {
        var season = createSeason("Test Season");
        var team = createTeam("TST", "Test Team");
        season.addTeam(team);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));

        service.removeTeamFromSeason(season.getId(), team.getId());

        assertThat(season.containsTeam(team)).isFalse();
        verify(seasonRepository).save(season);
    }

    @Test
    void removeTeamFromSeason_rejectsParentWithSubTeams() {
        var season = createSeason("Test Season");
        var parent = createTeam("PAR", "Parent Team");
        var subTeam = createTeam("SUB", "Sub Team");
        subTeam.setParentTeam(parent);
        parent.setSubTeams(List.of(subTeam));
        season.addTeam(parent);
        season.addTeam(subTeam);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(teamRepository.findById(parent.getId())).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.removeTeamFromSeason(season.getId(), parent.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot remove parent team PAR");
    }

    @Test
    void addCarsToSeason_addsCarsToPool() {
        var season = createSeason("Test Season");
        var car1 = new Car();
        car1.setId(UUID.randomUUID());
        var car2 = new Car();
        car2.setId(UUID.randomUUID());

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(carRepository.findById(car1.getId())).thenReturn(Optional.of(car1));
        when(carRepository.findById(car2.getId())).thenReturn(Optional.of(car2));

        int added = service.addCarsToSeason(season.getId(), List.of(car1.getId(), car2.getId()));

        assertThat(added).isEqualTo(2);
        assertThat(season.getCars()).containsExactly(car1, car2);
        verify(seasonRepository).save(season);
    }

    @Test
    void removeCarsFromSeason_removesCarsFromPool() {
        var season = createSeason("Test Season");
        var car = new Car();
        car.setId(UUID.randomUUID());
        season.getCars().add(car);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

        int removed = service.removeCarsFromSeason(season.getId(), List.of(car.getId()));

        assertThat(removed).isEqualTo(1);
        assertThat(season.getCars()).isEmpty();
        verify(seasonRepository).save(season);
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
