package org.ctc.domain.service;

import org.ctc.admin.service.TeamCardService;
import org.ctc.dataimport.GoogleCalendarService;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    @Mock private TeamCardService teamCardService;
    @Mock private GoogleCalendarService googleCalendarService;

    @InjectMocks
    private RaceService service;

    // --- getRaceListData ---

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
    void givenNoFilter_whenGetRaceListData_thenReturnsAll() {
        // given
        when(raceRepository.findAll()).thenReturn(List.of());
        when(seasonRepository.findAll()).thenReturn(List.of());

        // when
        var result = service.getRaceListData(null, null);

        // then
        assertThat(result.races()).isEmpty();
        verify(raceRepository).findAll();
    }

    // --- saveRace ---

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

    // --- saveResults ---

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
        verify(scoringService).calculatePoints(any(RaceResult.class), eq(race.getMatchday().getSeason().getRaceScoring()));
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

    // --- quickScore ---

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

    // --- deleteRace ---

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

    // --- getUsedSelections ---

    @Test
    void givenSeasonAndTeam_whenGetUsedSelections_thenReturnsBothSets() {
        // given
        var seasonId = UUID.randomUUID();
        var homeTeamId = UUID.randomUUID();
        when(raceRepository.findByMatchdaySeasonId(seasonId)).thenReturn(List.of());

        // when
        var result = service.getUsedSelections(seasonId, homeTeamId, null);

        // then
        assertThat(result).containsKeys("usedCarIds", "usedTrackIds");
    }

    // --- getRaceDetailData ---

    @Test
    void givenRaceWithResults_whenGetRaceDetailData_thenReturnsScoresAndFlags() {
        // given
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var season = new Season("Test Season 2026");
        season.setId(UUID.randomUUID());
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        matchday.setSeason(season);
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
        matchday.setSeason(season);
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

    // --- getRaceDetailData settings flags ---

    @Test
    void givenRaceWithoutSettings_whenGetRaceDetailData_thenFlagsSettingsMissing() {
        // given
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var season = new Season("Test Season 2026");
        season.setId(UUID.randomUUID());
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        matchday.setSeason(season);
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

    // --- saveRace with settings ---

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

    // --- getNewRaceFormData ---

    @Test
    void givenMatchdayId_whenGetNewRaceFormData_thenPopulatesSeasonPools() {
        // given
        var matchday = createMatchday();
        var season = matchday.getSeason();
        var car = new Car();
        car.setId(UUID.randomUUID());
        season.getCars().add(car);
        var track = new Track();
        track.setId(UUID.randomUUID());
        season.getTracks().add(track);

        when(matchdayRepository.findById(matchday.getId())).thenReturn(Optional.of(matchday));
        when(matchdayRepository.findAll()).thenReturn(List.of(matchday));
        when(teamRepository.findAll()).thenReturn(List.of());

        // when
        var data = service.getNewRaceFormData(matchday.getId());

        // then
        assertThat(data.data().matchdayId()).isEqualTo(matchday.getId());
        assertThat(data.seasonCars()).containsExactly(car);
        assertThat(data.seasonTracks()).containsExactly(track);
    }

    @Test
    void givenNoMatchdayId_whenGetNewRaceFormData_thenReturnsEmptyPools() {
        // given
        when(matchdayRepository.findAll()).thenReturn(List.of());
        when(teamRepository.findAll()).thenReturn(List.of());

        // when
        var data = service.getNewRaceFormData(null);

        // then
        assertThat(data.data().matchdayId()).isNull();
        assertThat(data.seasonCars()).isEmpty();
        assertThat(data.seasonTracks()).isEmpty();
    }

    // --- getRaceFormData (edit) ---

    @Test
    void givenExistingRace_whenGetRaceFormData_thenPopulatesFormFromRace() {
        // given
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var matchday = createMatchday();
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
        var data = service.getRaceFormData(race.getId());

        // then
        assertThat(data.data().id()).isEqualTo(race.getId());
        assertThat(data.data().homeTeamId()).isEqualTo(homeTeam.getId());
        assertThat(data.data().awayTeamId()).isEqualTo(awayTeam.getId());
    }

    // --- getResultsFormData ---

    @Test
    void givenRaceWithLineupButNoResults_whenGetResultsFormData_thenPopulatesDriversFromLineup() {
        // given
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var matchday = createMatchday();
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
        var data = service.getResultsFormData(race.getId());

        // then
        assertThat(data.data().results()).isNotEmpty();
        assertThat(data.data().results().get(0).driverId()).isEqualTo(driver.getId());
    }

    // --- saveRace edit ---

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

    // --- saveRace track not in pool ---

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

    // --- createOrUpdateCalendarEvent ---

    @Test
    void givenRaceWithDateTimeAndTeams_whenCreateCalendarEvent_thenCreatesEvent() throws Exception {
        // given
        var homeTeam = createTeam("DTR", "Delta Racing");
        var awayTeam = createTeam("MRL", "Maranello");
        var matchday = createMatchday();
        matchday.setLabel("MD 2");
        matchday.getSeason().setEventDurationMinutes(90);
        var match = new Match(matchday, homeTeam, awayTeam);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);
        race.setDateTime(java.time.LocalDateTime.of(2026, 3, 20, 19, 30));

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(googleCalendarService.isAvailable()).thenReturn(true);
        when(googleCalendarService.createEvent("MD 2 - DTR vs. MRL",
                java.time.LocalDateTime.of(2026, 3, 20, 19, 30), 90))
                .thenReturn("event-id-123");
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        service.createOrUpdateCalendarEvent(race.getId());

        // then
        verify(googleCalendarService).createEvent("MD 2 - DTR vs. MRL",
                java.time.LocalDateTime.of(2026, 3, 20, 19, 30), 90);
        verify(raceRepository).save(argThat(r -> "event-id-123".equals(r.getCalendarEventId())));
    }

    @Test
    void givenRaceWithExistingEventId_whenCreateCalendarEvent_thenUpdatesEvent() throws Exception {
        // given
        var homeTeam = createTeam("DTR", "Delta Racing");
        var awayTeam = createTeam("MRL", "Maranello");
        var matchday = createMatchday();
        matchday.setLabel("MD 2");
        matchday.getSeason().setEventDurationMinutes(90);
        var match = new Match(matchday, homeTeam, awayTeam);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);
        race.setDateTime(java.time.LocalDateTime.of(2026, 3, 27, 20, 0));
        race.setCalendarEventId("existing-event-id");

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(googleCalendarService.isAvailable()).thenReturn(true);

        // when
        service.createOrUpdateCalendarEvent(race.getId());

        // then
        verify(googleCalendarService).updateEvent("existing-event-id", "MD 2 - DTR vs. MRL",
                java.time.LocalDateTime.of(2026, 3, 27, 20, 0), 90);
        verify(googleCalendarService, never()).createEvent(any(), any(), anyInt());
    }

    @Test
    void givenRaceWithoutDateTime_whenCreateCalendarEvent_thenThrowsException() {
        // given
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(createMatchday());

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(googleCalendarService.isAvailable()).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.createOrUpdateCalendarEvent(race.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("date/time");
    }

    @Test
    void givenSeasonWithoutDuration_whenCreateCalendarEvent_thenThrowsException() {
        // given
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var matchday = createMatchday();
        matchday.getSeason().setEventDurationMinutes(null);
        var match = new Match(matchday, homeTeam, awayTeam);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);
        race.setDateTime(java.time.LocalDateTime.of(2026, 3, 20, 19, 30));

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(googleCalendarService.isAvailable()).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.createOrUpdateCalendarEvent(race.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duration");
    }

    @Test
    void givenCalendarNotAvailable_whenCreateCalendarEvent_thenThrowsException() {
        // given
        var raceId = UUID.randomUUID();
        var race = new Race();
        race.setId(raceId);
        race.setMatchday(createMatchday());

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(googleCalendarService.isAvailable()).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> service.createOrUpdateCalendarEvent(raceId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Calendar");
    }

    @Test
    void givenPlayoffRace_whenCreateCalendarEvent_thenUsesPlayoffDuration() throws Exception {
        // given
        var homeTeam = createTeam("DTR", "Delta Racing");
        var awayTeam = createTeam("MRL", "Maranello");
        var matchday = createMatchday();
        matchday.setLabel("PO 1");
        matchday.getSeason().setEventDurationMinutes(90);

        var playoff = new Playoff(matchday.getSeason(), "Playoffs");
        playoff.setEventDurationMinutes(120);
        var round = new PlayoffRound();
        round.setPlayoff(playoff);
        var matchup = new PlayoffMatchup();
        matchup.setRound(round);
        matchup.setTeam1(homeTeam);
        matchup.setTeam2(awayTeam);

        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setPlayoffMatchup(matchup);
        race.setDateTime(java.time.LocalDateTime.of(2026, 4, 10, 20, 0));

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(googleCalendarService.isAvailable()).thenReturn(true);
        when(googleCalendarService.createEvent(any(), any(), eq(120))).thenReturn("playoff-event-id");
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        service.createOrUpdateCalendarEvent(race.getId());

        // then
        verify(googleCalendarService).createEvent("PO 1 - DTR vs. MRL",
                java.time.LocalDateTime.of(2026, 4, 10, 20, 0), 120);
    }

    // --- Helper methods ---

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
        var scoring = new RaceScoring("Test", "10,8,6,4,2,1", "3,2,1", 1);
        var season = new Season();
        season.setId(UUID.randomUUID());
        season.setRaceScoring(scoring);

        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        matchday.setSeason(season);

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
        season.setRaceScoring(scoring);

        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        matchday.setSeason(season);
        return matchday;
    }

    private Team createTeam(String shortName, String name) {
        var team = new Team(name, shortName);
        team.setId(UUID.randomUUID());
        return team;
    }
}
