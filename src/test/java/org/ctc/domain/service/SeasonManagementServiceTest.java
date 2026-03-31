package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.springframework.mock.web.MockMultipartFile;
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

    @Test
    void removeTeamFromSeason_subTeamAutoRemovesParent() {
        var season = createSeason("Test Season");
        var parent = createTeam("PAR", "Parent Team");
        var subTeam = createTeam("SUB", "Sub Team");
        subTeam.setParentTeam(parent);
        parent.setSubTeams(List.of(subTeam));
        season.addTeam(parent);
        season.addTeam(subTeam);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(teamRepository.findById(subTeam.getId())).thenReturn(Optional.of(subTeam));

        service.removeTeamFromSeason(season.getId(), subTeam.getId());

        assertThat(season.containsTeam(subTeam)).isFalse();
        assertThat(season.containsTeam(parent)).isFalse();
        verify(seasonRepository).save(season);
    }

    @Test
    void updateSeasonTeam_setsRatingAndColors() throws Exception {
        var season = createSeason("Test Season");
        var team = createTeam("TST", "Test Team");
        var seasonTeam = new SeasonTeam(season, team);
        seasonTeam.setId(UUID.randomUUID());

        when(seasonTeamRepository.findById(seasonTeam.getId())).thenReturn(Optional.of(seasonTeam));

        service.updateSeasonTeam(seasonTeam.getId(), 85, "#ff0000", "#00ff00", "#0000ff", null);

        assertThat(seasonTeam.getRating()).isEqualTo(85);
        assertThat(seasonTeam.getPrimaryColor()).isEqualTo("#ff0000");
        assertThat(seasonTeam.getSecondaryColor()).isEqualTo("#00ff00");
        assertThat(seasonTeam.getAccentColor()).isEqualTo("#0000ff");
        verify(seasonTeamRepository).save(seasonTeam);
    }

    @Test
    void updateSeasonTeam_uploadsNewLogo() throws Exception {
        var season = createSeason("Test Season");
        var team = createTeam("TST", "Test Team");
        var seasonTeam = new SeasonTeam(season, team);
        seasonTeam.setId(UUID.randomUUID());
        seasonTeam.setLogoUrl("/uploads/old-logo.png");

        when(seasonTeamRepository.findById(seasonTeam.getId())).thenReturn(Optional.of(seasonTeam));
        when(fileStorageService.storeImage(eq("season-teams"), eq(seasonTeam.getId()), any()))
                .thenReturn("/uploads/season-teams/" + seasonTeam.getId() + "/new-logo.png");

        var logoFile = new MockMultipartFile("logo", "logo.png", "image/png", new byte[]{1, 2, 3});
        service.updateSeasonTeam(seasonTeam.getId(), null, null, null, null, logoFile);

        verify(fileStorageService).delete("/uploads/old-logo.png");
        verify(fileStorageService).storeImage(eq("season-teams"), eq(seasonTeam.getId()), any());
        assertThat(seasonTeam.getLogoUrl()).contains("new-logo.png");
        verify(seasonTeamRepository).save(seasonTeam);
    }

    @Test
    void addTracksToSeason_addsTracksToPool() {
        var season = createSeason("Test Season");
        var track1 = new Track();
        track1.setId(UUID.randomUUID());
        var track2 = new Track();
        track2.setId(UUID.randomUUID());

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(trackRepository.findById(track1.getId())).thenReturn(Optional.of(track1));
        when(trackRepository.findById(track2.getId())).thenReturn(Optional.of(track2));

        int added = service.addTracksToSeason(season.getId(), List.of(track1.getId(), track2.getId()));

        assertThat(added).isEqualTo(2);
        assertThat(season.getTracks()).containsExactly(track1, track2);
        verify(seasonRepository).save(season);
    }

    @Test
    void removeTracksFromSeason_removesTracksFromPool() {
        var season = createSeason("Test Season");
        var track = new Track();
        track.setId(UUID.randomUUID());
        season.getTracks().add(track);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

        int removed = service.removeTracksFromSeason(season.getId(), List.of(track.getId()));

        assertThat(removed).isEqualTo(1);
        assertThat(season.getTracks()).isEmpty();
        verify(seasonRepository).save(season);
    }

    @Test
    void updateSeasonTeam_blanksColorsBecomeNull() throws Exception {
        var season = createSeason("Test Season");
        var team = createTeam("TST", "Test Team");
        var seasonTeam = new SeasonTeam(season, team);
        seasonTeam.setId(UUID.randomUUID());

        when(seasonTeamRepository.findById(seasonTeam.getId())).thenReturn(Optional.of(seasonTeam));

        service.updateSeasonTeam(seasonTeam.getId(), 90, "  ", "", null, null);

        assertThat(seasonTeam.getPrimaryColor()).isNull();
        assertThat(seasonTeam.getSecondaryColor()).isNull();
        assertThat(seasonTeam.getAccentColor()).isNull();
        verify(seasonTeamRepository).save(seasonTeam);
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
