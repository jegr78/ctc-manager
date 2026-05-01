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
class RaceFormDataServiceTest {

    @Mock private RaceRepository raceRepository;
    @Mock private MatchdayRepository matchdayRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private CarRepository carRepository;
    @Mock private TrackRepository trackRepository;
    @Mock private SeasonDriverRepository seasonDriverRepository;
    @Mock private RaceLineupRepository raceLineupRepository;

    @InjectMocks
    private RaceFormDataService service;

    // --- getNewRaceFormData ---

    @Test
    void givenMatchdayId_whenGetNewRaceFormData_thenReturnsPopulatedFormDataWithSeasonPools() {
        // given
        var season = new Season();
        season.setId(UUID.randomUUID());
        season.setCars(new ArrayList<>());
        season.setTracks(new ArrayList<>());
        var car = new Car();
        car.setId(UUID.randomUUID());
        season.getCars().add(car);
        var track = new Track();
        track.setId(UUID.randomUUID());
        season.getTracks().add(track);

        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());

        when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));
        when(matchdayRepository.findAll()).thenReturn(List.of(matchday));
        when(teamRepository.findAll()).thenReturn(List.of());

        // when
        var result = service.getNewRaceFormData(matchday.getId());

        // then
        assertThat(result).isNotNull();
        assertThat(result.data().matchdayId()).isEqualTo(matchday.getId());
        assertThat(result.data().id()).isNull();
        assertThat(result.seasonCars()).containsExactly(car);
        assertThat(result.seasonTracks()).containsExactly(track);
        assertThat(result.matchdays()).hasSize(1);
        assertThat(result.usedCarIds()).isEmpty();
        assertThat(result.usedTrackIds()).isEmpty();
    }

    @Test
    void givenNullMatchdayId_whenGetNewRaceFormData_thenReturnsEmptyPools() {
        // given
        when(matchdayRepository.findAll()).thenReturn(List.of());
        when(teamRepository.findAll()).thenReturn(List.of());

        // when
        var result = service.getNewRaceFormData(null);

        // then
        assertThat(result.data().matchdayId()).isNull();
        assertThat(result.seasonCars()).isEmpty();
        assertThat(result.seasonTracks()).isEmpty();
        assertThat(result.usedCarIds()).isEmpty();
        assertThat(result.usedTrackIds()).isEmpty();
    }

    // --- getRaceFormData ---

    @Test
    void givenExistingRace_whenGetRaceFormData_thenReturnsPrePopulatedFormData() {
        // given
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var season = new Season();
        season.setId(UUID.randomUUID());
        season.setCars(new ArrayList<>());
        season.setTracks(new ArrayList<>());
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        var match = new Match(matchday, homeTeam, awayTeam);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(matchdayRepository.findAll()).thenReturn(List.of(matchday));
        when(teamRepository.findAll()).thenReturn(List.of(homeTeam, awayTeam));
        when(raceRepository.findByMatchdaySeasonId(any())).thenReturn(List.of());

        // when
        var result = service.getRaceFormData(race.getId());

        // then
        assertThat(result.data().id()).isEqualTo(race.getId());
        assertThat(result.data().homeTeamId()).isEqualTo(homeTeam.getId());
        assertThat(result.data().awayTeamId()).isEqualTo(awayTeam.getId());
        assertThat(result.matchdays()).hasSize(1);
        assertThat(result.teams()).hasSize(2);
    }

    // --- getResultsFormData ---

    @Test
    void givenRaceWithLineupAndNoResults_whenGetResultsFormData_thenReturnsResultsFormDataWithDriversFromLineup() {
        // given
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var scoring = new RaceScoring("Test", "10,8,6", "3,2,1", 1);
        var season = new Season();
        season.setId(UUID.randomUUID());
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        var match = new Match(matchday, homeTeam, awayTeam);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);

        var driver = new Driver("psn1", "Nick1");
        driver.setId(UUID.randomUUID());
        driver.setSeasonDrivers(List.of());
        var lineup = new RaceLineup(race, driver, homeTeam);

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(raceLineupRepository.findByRaceId(race.getId())).thenReturn(List.of(lineup));

        // when
        var result = service.getResultsFormData(race.getId());

        // then
        assertThat(result).isNotNull();
        assertThat(result.race()).isEqualTo(race);
        assertThat(result.raceScoring()).isEqualTo(scoring);
        assertThat(result.data().results()).isNotEmpty();
        assertThat(result.data().results().get(0).driverId()).isEqualTo(driver.getId());
    }

    @Test
    void givenRaceWithExistingResults_whenGetResultsFormData_thenReturnsExistingResults() {
        // given
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var scoring = new RaceScoring("Test", "10,8,6", "3,2,1", 1);
        var season = new Season();
        season.setId(UUID.randomUUID());
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        var match = new Match(matchday, homeTeam, awayTeam);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);

        var driver = new Driver("psn1", "Nick1");
        driver.setId(UUID.randomUUID());
        driver.setSeasonDrivers(List.of());
        var raceResult = new RaceResult(race, driver, 1, 1, false);
        race.getResults().add(raceResult);

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driver.getId()))
                .thenReturn(Optional.empty());

        // when
        var result = service.getResultsFormData(race.getId());

        // then
        assertThat(result.data().results()).hasSize(1);
        verify(raceLineupRepository, never()).findByRaceId(any());
    }

    // --- Bye race null safety ---

    @Test
    void givenByeRaceWithNullHomeTeam_whenGetRaceFormData_thenReturnsFormDataWithEmptyUsedSets() {
        // given
        var season = new Season();
        season.setId(UUID.randomUUID());
        season.setCars(new ArrayList<>());
        season.setTracks(new ArrayList<>());
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        var match = new Match();
        match.setMatchday(matchday);
        match.setBye(true);
        // homeTeam and awayTeam are null
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(matchdayRepository.findAll()).thenReturn(List.of(matchday));
        when(teamRepository.findAll()).thenReturn(List.of());

        // when
        var result = service.getRaceFormData(race.getId());

        // then
        assertThat(result).isNotNull();
        assertThat(result.usedCarIds()).isEmpty();
        assertThat(result.usedTrackIds()).isEmpty();
        assertThat(result.data().homeTeamId()).isNull();
        assertThat(result.data().awayTeamId()).isNull();
    }

    @Test
    void givenByeRace_whenGetResultsFormData_thenReturnsResultsWithoutNPE() {
        // given — bye match: homeTeam set, awayTeam null
        var homeTeam = createTeam("HOM", "Home");
        var scoring = new RaceScoring("Test", "10,8,6", "3,2,1", 1);
        var season = new Season();
        season.setId(UUID.randomUUID());
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        var match = new Match();
        match.setMatchday(matchday);
        match.setHomeTeam(homeTeam);
        match.setBye(true);
        // awayTeam is null
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(raceLineupRepository.findByRaceId(race.getId())).thenReturn(List.of());
        when(seasonDriverRepository.findBySeasonIdAndTeamId(season.getId(), homeTeam.getId())).thenReturn(List.of());

        // when
        var result = service.getResultsFormData(race.getId());

        // then
        assertThat(result).isNotNull();
        assertThat(result.data().results()).isEmpty();
    }

    // --- Helper ---

    private Team createTeam(String shortName, String name) {
        var team = new Team(name, shortName);
        team.setId(UUID.randomUUID());
        return team;
    }
}
