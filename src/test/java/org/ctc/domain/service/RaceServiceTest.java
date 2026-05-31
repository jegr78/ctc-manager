package org.ctc.domain.service;

import java.util.*;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RaceServiceTest {

    @Mock private RaceRepository raceRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private MatchdayRepository matchdayRepository;
    @Mock private SeasonRepository seasonRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private SeasonDriverRepository seasonDriverRepository;
    @Mock private RaceLineupRepository raceLineupRepository;
    @Mock private CarRepository carRepository;
    @Mock private TrackRepository trackRepository;
    @Mock private SeasonTeamRepository seasonTeamRepository;
    @Mock private ScoringService scoringService;
    @Mock private RaceCalendarService raceCalendarService;

    @InjectMocks
    private RaceService service;


    @Test
    void givenMatchdayId_whenGetRaceListData_thenReturnsFilteredRaces() {
        // given
        var matchdayId = UUID.randomUUID();
        var race = createRaceWithScore(10, 5);
        var matchday = new Matchday();
        matchday.setId(matchdayId);

        when(raceRepository.findByMatchdayId(matchdayId)).thenReturn(List.of(race));
        when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
        when(seasonRepository.findAll()).thenReturn(List.of());

        // when
        var result = service.getRaceListData(matchdayId, null);

        // then
        assertThat(result.races()).hasSize(1);
        assertThat(result.matchday()).isNotNull();
        assertThat(result.raceScores()).containsKey(race.getId());
        assertThat(result.selectedSeasonId()).isNull();
    }

    @Test
    void givenSeasonId_whenGetRaceListData_thenReturnsFilteredRaces() {
        // given
        var seasonId = UUID.randomUUID();
        when(raceRepository.findByMatchdaySeasonId(seasonId)).thenReturn(List.of());
        when(seasonRepository.findAll()).thenReturn(List.of());

        // when
        var result = service.getRaceListData(null, seasonId);

        // then
        assertThat(result.selectedSeasonId()).isEqualTo(seasonId);
        assertThat(result.matchday()).isNull();
    }

    @Test
    void givenNoFilter_whenGetRaceListData_thenReturnsAllRaces() {
        // given
        when(raceRepository.findAll()).thenReturn(List.of());
        when(seasonRepository.findAll()).thenReturn(List.of());

        // when
        var result = service.getRaceListData(null, null);

        // then
        assertThat(result.races()).isEmpty();
        verify(raceRepository).findAll();
    }


    @Test
    void givenNewRaceData_whenSaveRace_thenCreatesMatchAndSaves() {
        // given
        var matchdayId = UUID.randomUUID();
        var homeTeamId = UUID.randomUUID();
        var awayTeamId = UUID.randomUUID();

        var matchday = createMatchday();
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");

        when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
        when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(awayTeamId)).thenReturn(Optional.of(awayTeam));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        var result = service.saveRace(null, matchdayId, homeTeamId, awayTeamId,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // then
        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("HOM").contains("AWY");
        verify(raceRepository).save(any(Race.class));
    }

    @Test
    void givenCarNotInSeasonPool_whenSaveRace_thenReturnsError() {
        // given
        var matchdayId = UUID.randomUUID();
        var homeTeamId = UUID.randomUUID();
        var awayTeamId = UUID.randomUUID();
        var carId = UUID.randomUUID();

        var matchday = createMatchday();
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var car = new Car();
        car.setId(carId);

        when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
        when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(awayTeamId)).thenReturn(Optional.of(awayTeam));
        when(carRepository.findById(carId)).thenReturn(Optional.of(car));

        // when
        var result = service.saveRace(null, matchdayId, homeTeamId, awayTeamId,
                null, carId, null, null, null, null, null, null, null, null, null, null, null, null);

        // then
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Car is not in this season's pool");
    }


    @Test
    void givenResultData_whenSaveResults_thenCalculatesPointsAndAggregates() {
        // given
        var raceId = UUID.randomUUID();
        var driverId = UUID.randomUUID();
        var race = createRaceWithScoring();
        race.setId(raceId);

        var rd = new RaceService.RaceResultData(driverId, "TestPSN", "HOM", 1, 1, true);

        var driver = new Driver("TestPSN", "TestNick");
        driver.setId(driverId);

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        var message = service.saveResults(raceId, List.of(rd));

        // then
        assertThat(message).contains("Results saved");
        verify(scoringService).calculatePoints(any(RaceResult.class), eq(race.getMatchday().getPhase().getRaceScoring()));
        verify(scoringService).aggregateMatchScores(race);
    }

    @Test
    void givenNullDriverId_whenSaveResults_thenDriverLookupSkipped() {
        // given
        var raceId = UUID.randomUUID();
        var race = createRaceWithScoring();
        race.setId(raceId);

        var rd = new RaceService.RaceResultData(null, null, null, 0, 0, false);

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        service.saveResults(raceId, List.of(rd));

        // then
        verify(driverRepository, never()).findById(any());
    }


    @Test
    void givenRace_whenQuickScore_thenMatchScoresUpdated() {
        // given
        var raceId = UUID.randomUUID();
        var race = createRaceWithScore(0, 0);
        race.setId(raceId);

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        var message = service.quickScore(raceId, 42, 38);

        // then
        assertThat(message).contains("42").contains("38");
        assertThat(race.getMatch().getHomeScore()).isEqualTo(42);
        assertThat(race.getMatch().getAwayScore()).isEqualTo(38);
    }


    @Test
    void givenExistingRace_whenDeleteRace_thenReturnsMatchdayId() {
        // given
        var raceId = UUID.randomUUID();
        var matchdayId = UUID.randomUUID();

        var matchday = new Matchday();
        matchday.setId(matchdayId);

        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var match = new Match(matchday, homeTeam, awayTeam);

        var race = new Race();
        race.setId(raceId);
        race.setMatchday(matchday);
        race.setMatch(match);

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));

        // when
        var result = service.deleteRace(raceId);

        // then
        assertThat(result).isEqualTo(matchdayId);
        verify(raceRepository).delete(race);
    }


    @Test
    void givenRaceWithResults_whenGetRaceDetailData_thenReturnsScoresAndFlags() {
        // given
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var season = new Season("Test Season 2026");
        season.setId(UUID.randomUUID());
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        // matchday.getSeason() derives from phase; wire a phase.
        matchday.setPhase(PhaseTestFixtures.regularPhase(season, null, null));
        var match = new Match(matchday, homeTeam, awayTeam);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);

        var driver1 = new Driver("psn1", "Nick1");
        driver1.setId(UUID.randomUUID());
        driver1.setSeasonDrivers(List.of());
        var driver2 = new Driver("psn2", "Nick2");
        driver2.setId(UUID.randomUUID());
        driver2.setSeasonDrivers(List.of());

        var r1 = new RaceResult(race, driver1, 1, 1, false);
        r1.setPointsTotal(20);
        var r2 = new RaceResult(race, driver2, 2, 2, false);
        r2.setPointsTotal(17);
        race.getResults().addAll(List.of(r1, r2));

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(scoringService.isDriverInTeam(eq(r1), any(), eq(homeTeam.getId()))).thenReturn(true);
        when(scoringService.isDriverInTeam(eq(r2), any(), eq(homeTeam.getId()))).thenReturn(false);
        when(raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driver1.getId()))
                .thenReturn(Optional.of(new RaceLineup(race, driver1, homeTeam)));
        when(raceLineupRepository.findByRaceIdAndDriverId(race.getId(), driver2.getId()))
                .thenReturn(Optional.of(new RaceLineup(race, driver2, awayTeam)));
        when(raceLineupRepository.findByRaceId(race.getId()))
                .thenReturn(List.of(new RaceLineup(race, driver1, homeTeam), new RaceLineup(race, driver2, awayTeam)));

        // when
        var data = service.getRaceDetailData(race.getId());

        // then
        assertThat(data.homeTotal()).isEqualTo(20);
        assertThat(data.awayTotal()).isEqualTo(17);
        assertThat(data.driverTeamMap()).containsEntry(driver1.getId(), "HOM");
        assertThat(data.driverTeamMap()).containsEntry(driver2.getId(), "AWY");
        assertThat(data.resultsMissing()).isFalse();
        assertThat(data.canGenerateLineup()).isFalse(); // no team cards
    }

    @Test
    void givenRaceWithoutResults_whenGetRaceDetailData_thenFlagsResultsMissing() {
        // given
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var season = new Season("Test Season 2026");
        season.setId(UUID.randomUUID());
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        // matchday.getSeason() derives from phase; wire a phase.
        matchday.setPhase(PhaseTestFixtures.regularPhase(season, null, null));
        var match = new Match(matchday, homeTeam, awayTeam);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(raceLineupRepository.findByRaceId(race.getId())).thenReturn(List.of());

        // when
        var data = service.getRaceDetailData(race.getId());

        // then
        assertThat(data.resultsMissing()).isTrue();
        assertThat(data.canGenerateResults()).isFalse();
        assertThat(data.resultsExist()).isFalse();
    }


    @Test
    void givenRaceWithoutSettings_whenGetRaceDetailData_thenFlagsSettingsMissing() {
        // given
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var season = new Season("Test Season 2026");
        season.setId(UUID.randomUUID());
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        // matchday.getSeason() derives from phase; wire a phase.
        matchday.setPhase(PhaseTestFixtures.regularPhase(season, null, null));
        var match = new Match(matchday, homeTeam, awayTeam);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(raceLineupRepository.findByRaceId(race.getId())).thenReturn(List.of());

        // when
        var data = service.getRaceDetailData(race.getId());

        // then
        assertThat(data.settingsMissing()).isTrue();
        assertThat(data.canGenerateSettings()).isFalse();
        assertThat(data.settingsExist()).isFalse();
    }

    @Test
    void givenSettingsTrackTeamsButNoCar_whenGetRaceDetailData_thenCanGenerateLobbySettings() {
        // given
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var season = new Season("Test Season 2026");
        season.setId(UUID.randomUUID());
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        matchday.setPhase(PhaseTestFixtures.regularPhase(season, null, null));
        var match = new Match(matchday, homeTeam, awayTeam);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);
        race.setTrack(new Track("Test Track"));
        completeSettings(race);

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(raceLineupRepository.findByRaceId(race.getId())).thenReturn(List.of());

        // when
        var data = service.getRaceDetailData(race.getId());

        // then
        assertThat(data.canGenerateLobbySettings()).isTrue();
        assertThat(data.lobbySettingsMissing()).isFalse();
        assertThat(data.lobbySettingsExist()).isFalse();
        assertThat(data.canGenerateSettings()).isFalse();
    }

    @Test
    void givenCompleteSettingsButNoAwayTeam_whenGetRaceDetailData_thenCannotGenerateLobbySettings() {
        // given
        var homeTeam = createTeam("HOM", "Home");
        var season = new Season("Test Season 2026");
        season.setId(UUID.randomUUID());
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        matchday.setPhase(PhaseTestFixtures.regularPhase(season, null, null));
        var match = new Match(matchday, homeTeam, null);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);
        race.setTrack(new Track("Test Track"));
        completeSettings(race);

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(raceLineupRepository.findByRaceId(race.getId())).thenReturn(List.of());

        // when
        var data = service.getRaceDetailData(race.getId());

        // then
        assertThat(data.canGenerateLobbySettings()).isFalse();
        assertThat(data.lobbySettingsMissing()).isFalse();
    }

    private void completeSettings(Race race) {
        var settings = new RaceSettings(race);
        settings.setNumberOfLaps(20);
        settings.setTyreWearMultiplier(3);
        settings.setFuelConsumptionMultiplier(3);
        settings.setRefuelingSpeed(10);
        settings.setInitialFuel("90");
        settings.setNumberOfRequiredPitStops(0);
        settings.setTimeProgressionMultiplier(5);
        settings.setWeather("Preset S02");
        settings.setTimeOfDay("Afternoon");
        settings.setAvailableTyres("RS, RM, RH, I, W");
        settings.setMandatoryTyres("RS, RM, RH");
        race.setSettings(settings);
    }


    @Test
    void givenRaceDataWithSettings_whenSaveRace_thenRaceSettingsCreated() {
        // given
        var matchdayId = UUID.randomUUID();
        var homeTeamId = UUID.randomUUID();
        var awayTeamId = UUID.randomUUID();

        var matchday = createMatchday();
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");

        when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
        when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(awayTeamId)).thenReturn(Optional.of(awayTeam));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> {
            Race saved = inv.getArgument(0);
            assertThat(saved.getSettings()).isNotNull();
            assertThat(saved.getSettings().getNumberOfLaps()).isEqualTo(20);
            assertThat(saved.getSettings().getWeather()).isEqualTo("Preset S02");
            return saved;
        });

        // when
        var result = service.saveRace(null, matchdayId, homeTeamId, awayTeamId,
                null, null, null, 20, 3, 4, 10, "90", 0, 5, "Preset S02", "Afternoon", "RS, RM", "RS");

        // then
        assertThat(result.success()).isTrue();
    }


    @Test
    void givenExistingRace_whenSaveRace_thenUpdatesRaceWithoutCreatingMatch() {
        // given
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var matchday = createMatchday();
        var existingMatch = new Match(matchday, homeTeam, awayTeam);
        var existingRace = new Race();
        existingRace.setId(UUID.randomUUID());
        existingRace.setMatchday(matchday);
        existingRace.setMatch(existingMatch);

        when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));
        when(teamRepository.findById(homeTeam.getId())).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(awayTeam.getId())).thenReturn(Optional.of(awayTeam));
        when(raceRepository.findById(existingRace.getId())).thenReturn(Optional.of(existingRace));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        var result = service.saveRace(existingRace.getId(), matchday.getId(), homeTeam.getId(), awayTeam.getId(),
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // then
        assertThat(result.success()).isTrue();
        verify(raceRepository).save(existingRace);
        verify(matchRepository, never()).save(any());
    }


    @Test
    void givenTrackNotInSeasonPool_whenSaveRace_thenReturnsError() {
        // given
        var matchdayId = UUID.randomUUID();
        var homeTeamId = UUID.randomUUID();
        var awayTeamId = UUID.randomUUID();
        var trackId = UUID.randomUUID();

        var matchday = createMatchday();
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var track = new Track();
        track.setId(trackId);

        when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
        when(teamRepository.findById(homeTeamId)).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(awayTeamId)).thenReturn(Optional.of(awayTeam));
        when(trackRepository.findById(trackId)).thenReturn(Optional.of(track));

        // when
        var result = service.saveRace(null, matchdayId, homeTeamId, awayTeamId,
                trackId, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // then
        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Track is not in this season's pool");
    }


    private Race createRaceWithScore(int homeScore, int awayScore) {
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        var match = new Match(matchday, homeTeam, awayTeam);
        match.setHomeScore(homeScore);
        match.setAwayScore(awayScore);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);
        return race;
    }

    private Race createRaceWithScoring() {
        // scoring lives on the SeasonPhase, not the Season.
        var scoring = new RaceScoring("Test", "10,8,6,4,2,1", "3,2,1", 1);
        var season = new Season();
        season.setId(UUID.randomUUID());
        var phase = PhaseTestFixtures.regularPhase(season, scoring, null);
        phase.setRaceScoring(scoring);

        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        matchday.setPhase(phase);

        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var match = new Match(matchday, homeTeam, awayTeam);
        match.setHomeScore(0);
        match.setAwayScore(0);

        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);
        return race;
    }

    private Matchday createMatchday() {
        var season = new Season();
        season.setId(UUID.randomUUID());
        season.setCars(new ArrayList<>());
        season.setTracks(new ArrayList<>());
        var scoring = new RaceScoring("Default", "10,8,6", "3,2,1", 1);

        // matchday.getSeason() now derives from phase; wire a phase carrying scoring.
        var phase = PhaseTestFixtures.regularPhase(season, scoring, null);
        phase.setRaceScoring(scoring);
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        matchday.setPhase(phase);
        return matchday;
    }

    private Team createTeam(String shortName, String name) {
        var team = new Team(name, shortName);
        team.setId(UUID.randomUUID());
        return team;
    }
}
