package org.ctc.domain.service;

import org.ctc.admin.dto.RaceForm;
import org.ctc.admin.dto.RaceResultForm;
import org.ctc.admin.service.LineupGraphicService;
import org.ctc.admin.service.ResultsGraphicService;
import org.ctc.admin.service.SettingsGraphicService;
import org.ctc.admin.service.TeamCardService;
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
class RaceManagementServiceTest {

    @Mock private RaceRepository raceRepository;
    @Mock private MatchRepository matchRepository;
    @Mock private MatchdayRepository matchdayRepository;
    @Mock private SeasonRepository seasonRepository;
    @Mock private TeamRepository teamRepository;
    @Mock private DriverRepository driverRepository;
    @Mock private SeasonDriverRepository seasonDriverRepository;
    @Mock private RaceLineupRepository raceLineupRepository;
    @Mock private RaceAttachmentRepository raceAttachmentRepository;
    @Mock private CarRepository carRepository;
    @Mock private TrackRepository trackRepository;
    @Mock private SeasonTeamRepository seasonTeamRepository;
    @Mock private ScoringService scoringService;
    @Mock private FileStorageService fileStorageService;
    @Mock private LineupGraphicService lineupGraphicService;
    @Mock private ResultsGraphicService resultsGraphicService;
    @Mock private SettingsGraphicService settingsGraphicService;
    @Mock private TeamCardService teamCardService;

    @InjectMocks
    private RaceManagementService service;

    // --- getRaceListData ---

    @Test
    void getRaceListData_byMatchdayId_returnsFilteredRaces() {
        var matchdayId = UUID.randomUUID();
        var race = createRaceWithScore(10, 5);
        var matchday = new Matchday();
        matchday.setId(matchdayId);

        when(raceRepository.findByMatchdayId(matchdayId)).thenReturn(List.of(race));
        when(matchdayRepository.findById(matchdayId)).thenReturn(Optional.of(matchday));
        when(seasonRepository.findAll()).thenReturn(List.of());

        var result = service.getRaceListData(matchdayId, null);

        assertThat(result.races()).hasSize(1);
        assertThat(result.matchday()).isNotNull();
        assertThat(result.raceScores()).containsKey(race.getId());
        assertThat(result.selectedSeasonId()).isNull();
    }

    @Test
    void getRaceListData_bySeasonId_returnsFilteredRaces() {
        var seasonId = UUID.randomUUID();
        when(raceRepository.findByMatchdaySeasonId(seasonId)).thenReturn(List.of());
        when(seasonRepository.findAll()).thenReturn(List.of());

        var result = service.getRaceListData(null, seasonId);

        assertThat(result.selectedSeasonId()).isEqualTo(seasonId);
        assertThat(result.matchday()).isNull();
    }

    @Test
    void getRaceListData_noFilter_returnsAll() {
        when(raceRepository.findAll()).thenReturn(List.of());
        when(seasonRepository.findAll()).thenReturn(List.of());

        var result = service.getRaceListData(null, null);

        assertThat(result.races()).isEmpty();
        verify(raceRepository).findAll();
    }

    // --- saveRace ---

    @Test
    void saveRace_newRace_createsMatchAndSaves() {
        var form = new RaceForm();
        form.setMatchdayId(UUID.randomUUID());
        form.setHomeTeamId(UUID.randomUUID());
        form.setAwayTeamId(UUID.randomUUID());

        var matchday = createMatchday();
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");

        when(matchdayRepository.findById(form.getMatchdayId())).thenReturn(Optional.of(matchday));
        when(teamRepository.findById(form.getHomeTeamId())).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(form.getAwayTeamId())).thenReturn(Optional.of(awayTeam));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.saveRace(form);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("HOM").contains("AWY");
        verify(raceRepository).save(any(Race.class));
    }

    @Test
    void saveRace_carNotInSeasonPool_returnsError() {
        var form = new RaceForm();
        form.setMatchdayId(UUID.randomUUID());
        form.setHomeTeamId(UUID.randomUUID());
        form.setAwayTeamId(UUID.randomUUID());
        form.setCarId(UUID.randomUUID());

        var matchday = createMatchday();
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var car = new Car();
        car.setId(form.getCarId());

        when(matchdayRepository.findById(form.getMatchdayId())).thenReturn(Optional.of(matchday));
        when(teamRepository.findById(form.getHomeTeamId())).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(form.getAwayTeamId())).thenReturn(Optional.of(awayTeam));
        when(carRepository.findById(form.getCarId())).thenReturn(Optional.of(car));

        var result = service.saveRace(form);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Car is not in this season's pool");
    }

    // --- saveResults ---

    @Test
    void saveResults_calculatesPointsAndAggregates() {
        var raceId = UUID.randomUUID();
        var driverId = UUID.randomUUID();
        var race = createRaceWithScoring();
        race.setId(raceId);

        var rf = new RaceResultForm();
        rf.setDriverId(driverId);
        rf.setPosition(1);
        rf.setQualiPosition(1);
        rf.setFastestLap(true);

        var driver = new Driver("TestPSN", "TestNick");
        driver.setId(driverId);

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(driverRepository.findById(driverId)).thenReturn(Optional.of(driver));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        var message = service.saveResults(raceId, List.of(rf));

        assertThat(message).contains("Results saved");
        verify(scoringService).calculatePoints(any(RaceResult.class), eq(race.getMatchday().getSeason().getRaceScoring()));
        verify(scoringService).aggregateMatchScores(race);
    }

    @Test
    void saveResults_skipsNullDriverIds() {
        var raceId = UUID.randomUUID();
        var race = createRaceWithScoring();
        race.setId(raceId);

        var rf = new RaceResultForm(); // driverId is null

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        service.saveResults(raceId, List.of(rf));

        verify(driverRepository, never()).findById(any());
    }

    // --- quickScore ---

    @Test
    void quickScore_setsMatchScores() {
        var raceId = UUID.randomUUID();
        var race = createRaceWithScore(0, 0);
        race.setId(raceId);

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        var message = service.quickScore(raceId, 42, 38);

        assertThat(message).contains("42").contains("38");
        assertThat(race.getMatch().getHomeScore()).isEqualTo(42);
        assertThat(race.getMatch().getAwayScore()).isEqualTo(38);
    }

    // --- addLink ---

    @Test
    void addLink_validUrl_savesAttachment() {
        var raceId = UUID.randomUUID();
        var race = new Race();
        race.setId(raceId);

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));

        var name = service.addLink(raceId, "Replay", "https://youtube.com/watch?v=123");

        assertThat(name).isEqualTo("Replay");
        verify(raceAttachmentRepository).save(any(RaceAttachment.class));
    }

    @Test
    void addLink_invalidUrl_throwsException() {
        assertThatThrownBy(() -> service.addLink(UUID.randomUUID(), "Bad", "ftp://invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http");
    }

    // --- deleteAttachment ---

    @Test
    void deleteAttachment_fileType_deletesFileAndRecord() {
        var attachmentId = UUID.randomUUID();
        var raceId = UUID.randomUUID();
        var race = new Race();
        race.setId(raceId);

        var attachment = new RaceAttachment(race, AttachmentType.FILE, "screenshot.png", "/uploads/test.png");
        attachment.setId(attachmentId);

        when(raceAttachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        var result = service.deleteAttachment(attachmentId);

        assertThat(result).isEqualTo(raceId);
        verify(fileStorageService).delete("/uploads/test.png");
        verify(raceAttachmentRepository).delete(attachment);
    }

    @Test
    void deleteAttachment_linkType_doesNotDeleteFile() {
        var attachmentId = UUID.randomUUID();
        var raceId = UUID.randomUUID();
        var race = new Race();
        race.setId(raceId);

        var attachment = new RaceAttachment(race, AttachmentType.LINK, "Replay", "https://youtube.com");
        attachment.setId(attachmentId);

        when(raceAttachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        service.deleteAttachment(attachmentId);

        verify(fileStorageService, never()).delete(any());
        verify(raceAttachmentRepository).delete(attachment);
    }

    // --- deleteRace ---

    @Test
    void deleteRace_returnsMatchdayId() {
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

        var result = service.deleteRace(raceId);

        assertThat(result).isEqualTo(matchdayId);
        verify(raceRepository).delete(race);
    }

    // --- getUsedSelections ---

    @Test
    void getUsedSelections_returnsBothSets() {
        var seasonId = UUID.randomUUID();
        var homeTeamId = UUID.randomUUID();
        when(raceRepository.findByMatchdaySeasonId(seasonId)).thenReturn(List.of());

        var result = service.getUsedSelections(seasonId, homeTeamId, null);

        assertThat(result).containsKeys("usedCarIds", "usedTrackIds");
    }

    // --- getRaceDetailData ---

    @Test
    void getRaceDetailData_withResults_returnsScoresAndFlags() {
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

        var data = service.getRaceDetailData(race.getId());

        assertThat(data.homeTotal()).isEqualTo(20);
        assertThat(data.awayTotal()).isEqualTo(17);
        assertThat(data.driverTeamMap()).containsEntry(driver1.getId(), "HOM");
        assertThat(data.driverTeamMap()).containsEntry(driver2.getId(), "AWY");
        assertThat(data.resultsMissing()).isFalse();
        assertThat(data.canGenerateLineup()).isFalse(); // no team cards
    }

    @Test
    void getRaceDetailData_withoutResults_flagsResultsMissing() {
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

        var data = service.getRaceDetailData(race.getId());

        assertThat(data.resultsMissing()).isTrue();
        assertThat(data.canGenerateResults()).isFalse();
        assertThat(data.resultsExist()).isFalse();
    }

    // --- generateResults ---

    @Test
    void generateResults_createsAttachment() throws Exception {
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        matchday.setLabel("MD 1");
        matchday.setSeason(new Season("S"));
        var match = new Match(matchday, homeTeam, awayTeam);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(resultsGraphicService.generateResults(race)).thenReturn("/uploads/races/" + race.getId() + "/results.png");

        service.generateResults(race.getId());

        verify(raceAttachmentRepository).save(argThat(att ->
                att.getName().equals("MD 1-HOM-AWY-Results")
                        && att.getUrl().endsWith("/results.png")
                        && att.getType() == AttachmentType.FILE));
    }

    @Test
    void generateResults_onFailure_throwsRuntimeException() throws Exception {
        var race = new Race();
        race.setId(UUID.randomUUID());

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(resultsGraphicService.generateResults(race)).thenThrow(new RuntimeException("Playwright failed"));

        assertThatThrownBy(() -> service.generateResults(race.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Playwright failed");
    }

    // --- generateSettings ---

    @Test
    void generateSettings_createsAttachment() throws Exception {
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        matchday.setLabel("MD 1");
        matchday.setSeason(new Season("S"));
        var match = new Match(matchday, homeTeam, awayTeam);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(settingsGraphicService.generateSettings(race)).thenReturn("/uploads/races/" + race.getId() + "/settings.png");

        service.generateSettings(race.getId());

        verify(raceAttachmentRepository).save(argThat(att ->
                att.getName().equals("MD 1-HOM-AWY-Settings")
                        && att.getUrl().endsWith("/settings.png")
                        && att.getType() == AttachmentType.FILE));
    }

    @Test
    void generateSettings_onFailure_throwsRuntimeException() throws Exception {
        var race = new Race();
        race.setId(UUID.randomUUID());

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(settingsGraphicService.generateSettings(race)).thenThrow(new RuntimeException("Playwright failed"));

        assertThatThrownBy(() -> service.generateSettings(race.getId()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Playwright failed");
    }

    // --- getRaceDetailData settings flags ---

    @Test
    void getRaceDetailData_withoutSettings_flagsSettingsMissing() {
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

        var data = service.getRaceDetailData(race.getId());

        assertThat(data.settingsMissing()).isTrue();
        assertThat(data.canGenerateSettings()).isFalse();
        assertThat(data.settingsExist()).isFalse();
    }

    // --- saveRace with settings ---

    @Test
    void saveRace_withSettings_createsRaceSettings() {
        var form = new RaceForm();
        form.setMatchdayId(UUID.randomUUID());
        form.setHomeTeamId(UUID.randomUUID());
        form.setAwayTeamId(UUID.randomUUID());
        form.setNumberOfLaps(20);
        form.setTyreWearMultiplier(3);
        form.setFuelConsumptionMultiplier(4);
        form.setRefuelingSpeed(10);
        form.setInitialFuel("90");
        form.setNumberOfRequiredPitStops(0);
        form.setTimeProgressionMultiplier(5);
        form.setWeather("Preset S02");
        form.setTimeOfDay("Afternoon");
        form.setAvailableTyres("RS, RM");
        form.setMandatoryTyres("RS");

        var matchday = createMatchday();
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");

        when(matchdayRepository.findById(form.getMatchdayId())).thenReturn(Optional.of(matchday));
        when(teamRepository.findById(form.getHomeTeamId())).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(form.getAwayTeamId())).thenReturn(Optional.of(awayTeam));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> {
            Race saved = inv.getArgument(0);
            assertThat(saved.getSettings()).isNotNull();
            assertThat(saved.getSettings().getNumberOfLaps()).isEqualTo(20);
            assertThat(saved.getSettings().getWeather()).isEqualTo("Preset S02");
            return saved;
        });

        var result = service.saveRace(form);

        assertThat(result.success()).isTrue();
    }

    // --- getNewRaceFormData ---

    @Test
    void getNewRaceFormData_withMatchdayId_populatesSeasonPools() {
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

        var data = service.getNewRaceFormData(matchday.getId());

        assertThat(data.form().getMatchdayId()).isEqualTo(matchday.getId());
        assertThat(data.seasonCars()).containsExactly(car);
        assertThat(data.seasonTracks()).containsExactly(track);
    }

    @Test
    void getNewRaceFormData_withoutMatchdayId_returnsEmptyPools() {
        when(matchdayRepository.findAll()).thenReturn(List.of());
        when(teamRepository.findAll()).thenReturn(List.of());

        var data = service.getNewRaceFormData(null);

        assertThat(data.form().getMatchdayId()).isNull();
        assertThat(data.seasonCars()).isEmpty();
        assertThat(data.seasonTracks()).isEmpty();
    }

    // --- getRaceFormData (edit) ---

    @Test
    void getRaceFormData_populatesFormFromExistingRace() {
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

        var data = service.getRaceFormData(race.getId());

        assertThat(data.form().getId()).isEqualTo(race.getId());
        assertThat(data.form().getHomeTeamId()).isEqualTo(homeTeam.getId());
        assertThat(data.form().getAwayTeamId()).isEqualTo(awayTeam.getId());
    }

    // --- getResultsFormData ---

    @Test
    void getResultsFormData_withoutResults_populatesDriversFromLineup() {
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

        var data = service.getResultsFormData(race.getId());

        assertThat(data.form().getResults()).isNotEmpty();
        assertThat(data.form().getResults().get(0).getDriverId()).isEqualTo(driver.getId());
    }

    // --- uploadAttachment ---

    @Test
    void uploadAttachment_storesFileAndCreatesAttachment() throws Exception {
        var raceId = UUID.randomUUID();
        var race = new Race();
        race.setId(raceId);

        var file = mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("screenshot.png");

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(fileStorageService.store(eq(raceId), any())).thenReturn("/uploads/races/" + raceId + "/screenshot.png");

        var name = service.uploadAttachment(raceId, file);

        assertThat(name).isEqualTo("screenshot.png");
        verify(fileStorageService).store(eq(raceId), any());
        verify(raceAttachmentRepository).save(argThat(att ->
                att.getName().equals("screenshot.png") && att.getType() == AttachmentType.FILE));
    }

    // --- downloadAttachment ---

    @Test
    void downloadAttachment_linkType_returnsBadRequest() {
        var attachmentId = UUID.randomUUID();
        var race = new Race();
        race.setId(UUID.randomUUID());
        var attachment = new RaceAttachment(race, AttachmentType.LINK, "Replay", "https://youtube.com");
        attachment.setId(attachmentId);

        when(raceAttachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        var response = service.downloadAttachment(attachmentId);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    // --- saveRace edit ---

    @Test
    void saveRace_editExisting_updatesRace() {
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var matchday = createMatchday();
        var existingMatch = new Match(matchday, homeTeam, awayTeam);
        var existingRace = new Race();
        existingRace.setId(UUID.randomUUID());
        existingRace.setMatchday(matchday);
        existingRace.setMatch(existingMatch);

        var form = new RaceForm();
        form.setId(existingRace.getId());
        form.setMatchdayId(matchday.getId());
        form.setHomeTeamId(homeTeam.getId());
        form.setAwayTeamId(awayTeam.getId());

        when(matchdayRepository.findById(form.getMatchdayId())).thenReturn(Optional.of(matchday));
        when(teamRepository.findById(form.getHomeTeamId())).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(form.getAwayTeamId())).thenReturn(Optional.of(awayTeam));
        when(raceRepository.findById(existingRace.getId())).thenReturn(Optional.of(existingRace));
        when(raceRepository.save(any(Race.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = service.saveRace(form);

        assertThat(result.success()).isTrue();
        verify(raceRepository).save(existingRace);
        verify(matchRepository, never()).save(any());
    }

    // --- saveRace track not in pool ---

    @Test
    void saveRace_trackNotInSeasonPool_returnsError() {
        var form = new RaceForm();
        form.setMatchdayId(UUID.randomUUID());
        form.setHomeTeamId(UUID.randomUUID());
        form.setAwayTeamId(UUID.randomUUID());
        form.setTrackId(UUID.randomUUID());

        var matchday = createMatchday();
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var track = new Track();
        track.setId(form.getTrackId());

        when(matchdayRepository.findById(form.getMatchdayId())).thenReturn(Optional.of(matchday));
        when(teamRepository.findById(form.getHomeTeamId())).thenReturn(Optional.of(homeTeam));
        when(teamRepository.findById(form.getAwayTeamId())).thenReturn(Optional.of(awayTeam));
        when(trackRepository.findById(form.getTrackId())).thenReturn(Optional.of(track));

        var result = service.saveRace(form);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Track is not in this season's pool");
    }

    // --- generateLineup ---

    @Test
    void generateLineup_createsAttachment() throws Exception {
        var homeTeam = createTeam("HOM", "Home");
        var awayTeam = createTeam("AWY", "Away");
        var matchday = new Matchday();
        matchday.setId(UUID.randomUUID());
        matchday.setLabel("MD 1");
        matchday.setSeason(new Season("S"));
        var match = new Match(matchday, homeTeam, awayTeam);
        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatchday(matchday);
        race.setMatch(match);

        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(lineupGraphicService.generateLineup(race)).thenReturn("/uploads/races/" + race.getId() + "/lineup.png");

        service.generateLineup(race.getId());

        verify(raceAttachmentRepository).save(argThat(att ->
                att.getName().equals("MD 1-HOM-AWY-Lineups")
                        && att.getUrl().endsWith("/lineup.png")
                        && att.getType() == AttachmentType.FILE));
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
