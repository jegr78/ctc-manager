package org.ctc.domain.service;

import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RaceLineupServiceTest {

    @Mock private RaceRepository raceRepository;
    @Mock private RaceLineupRepository raceLineupRepository;
    @Mock private SeasonDriverRepository seasonDriverRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private DriverRepository driverRepository;

    @InjectMocks
    private RaceLineupService service;

    // --- saveLineup ---

    @Test
    void saveLineup_createsEntries() {
        var raceId = UUID.randomUUID();
        var driverId = UUID.randomUUID();
        var teamId = UUID.randomUUID();

        var race = new Race();
        race.setId(raceId);
        var driver = new Driver();
        driver.setId(driverId);
        var team = new Team();
        team.setId(teamId);

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceLineupRepository.findByRaceId(raceId)).thenReturn(List.of());
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(raceLineupRepository.save(any(RaceLineup.class))).thenAnswer(inv -> inv.getArgument(0));

        int count = service.saveLineup(raceId, Map.of(driverId, teamId));

        assertThat(count).isEqualTo(1);
        verify(raceLineupRepository).save(any(RaceLineup.class));
    }

    @Test
    void saveLineup_clearsExistingBeforeSaving() {
        var raceId = UUID.randomUUID();
        var driverId = UUID.randomUUID();
        var teamId = UUID.randomUUID();

        var race = new Race();
        race.setId(raceId);
        var driver = new Driver();
        driver.setId(driverId);
        var team = new Team();
        team.setId(teamId);

        var existingLineup = new RaceLineup(race, driver, team);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceLineupRepository.findByRaceId(raceId)).thenReturn(List.of(existingLineup));
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(raceLineupRepository.save(any(RaceLineup.class))).thenAnswer(inv -> inv.getArgument(0));

        int count = service.saveLineup(raceId, Map.of(driverId, teamId));

        assertThat(count).isEqualTo(1);
        verify(raceLineupRepository).deleteAll(List.of(existingLineup));
        verify(raceLineupRepository).save(any(RaceLineup.class));
    }
}
