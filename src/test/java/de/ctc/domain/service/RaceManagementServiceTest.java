package de.ctc.domain.service;

import de.ctc.admin.dto.RaceForm;
import de.ctc.admin.dto.RaceResultForm;
import de.ctc.admin.service.LineupGraphicService;
import de.ctc.admin.service.TeamCardService;
import de.ctc.domain.model.*;
import de.ctc.domain.repository.*;
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
