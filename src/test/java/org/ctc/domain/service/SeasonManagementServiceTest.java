package org.ctc.domain.service;

import org.ctc.domain.exception.EntityNotFoundException;
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
import java.util.*;

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
    @Mock
    private PlayoffRepository playoffRepository;
    @Mock
    private RaceScoringRepository raceScoringRepository;
    @Mock
    private MatchScoringRepository matchScoringRepository;
    @Mock
    private ScoringService scoringService;

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

    // --- Season CRUD tests ---

    @Test
    void whenFindAll_thenReturnsAllSeasons() {
        // given
        var s1 = createSeason("Season 1");
        var s2 = createSeason("Season 2");
        when(seasonRepository.findAll()).thenReturn(List.of(s1, s2));

        // when
        var result = service.findAll();

        // then
        assertThat(result).hasSize(2);
        verify(seasonRepository).findAll();
    }

    @Test
    void givenExistingId_whenFindById_thenReturnsSeason() {
        // given
        var season = createSeason("Test Season");
        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

        // when
        var result = service.findById(season.getId());

        // then
        assertThat(result.getName()).isEqualTo("Test Season");
    }

    @Test
    void givenNonExistentId_whenFindById_thenThrowsEntityNotFoundException() {
        // given
        var id = UUID.randomUUID();
        when(seasonRepository.findById(id)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Season");
    }

    @Test
    void givenExistingId_whenGetDetailData_thenReturnsSeasonWithPlayoffAndComputedFlags() {
        // given
        var season = createSeason("Detail Season");
        var team1 = createTeam("T1", "Team 1");
        var team2 = createTeam("T2", "Team 2");
        season.addTeam(team1);
        season.addTeam(team2);
        season.setFormat(SeasonFormat.LEAGUE);

        var playoff = new Playoff();
        playoff.setId(UUID.randomUUID());

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(playoffRepository.findBySeasonId(season.getId())).thenReturn(Optional.of(playoff));

        // when
        var result = service.getDetailData(season.getId());

        // then
        assertThat(result.season()).isEqualTo(season);
        assertThat(result.playoff()).isEqualTo(playoff);
        assertThat(result.hasTeams()).isTrue();
        assertThat(result.hasMatchdays()).isFalse();
        assertThat(result.canGenerate()).isTrue(); // LEAGUE + no matchdays + >=2 teams
        assertThat(result.isSwiss()).isFalse();
    }

    @Test
    void givenExistingId_whenGetEditFormData_thenReturnsSeasonWithAllTeamsCarsTracksScorings() {
        // given
        var season = createSeason("Edit Season");
        var rs = new RaceScoring();
        rs.setId(UUID.randomUUID());
        rs.setName("Default");
        season.setRaceScoring(rs);
        var ms = new MatchScoring();
        ms.setId(UUID.randomUUID());
        ms.setName("Default");
        season.setMatchScoring(ms);

        var team = createTeam("T1", "Team 1");
        var car = new Car();
        car.setId(UUID.randomUUID());
        var track = new Track();
        track.setId(UUID.randomUUID());

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        when(teamRepository.findAll()).thenReturn(List.of(team));
        when(carRepository.findAllByOrderByManufacturerAscNameAsc()).thenReturn(List.of(car));
        when(trackRepository.findAllByOrderByNameAsc()).thenReturn(List.of(track));
        when(raceScoringRepository.findAll()).thenReturn(List.of(rs));
        when(matchScoringRepository.findAll()).thenReturn(List.of(ms));

        // when
        var result = service.getEditFormData(season.getId());

        // then
        assertThat(result.season()).isEqualTo(season);
        assertThat(result.allTeams()).containsExactly(team);
        assertThat(result.allCars()).containsExactly(car);
        assertThat(result.allTracks()).containsExactly(track);
        assertThat(result.allRaceScorings()).containsExactly(rs);
        assertThat(result.allMatchScorings()).containsExactly(ms);
    }

    @Test
    void givenNullId_whenGetEditFormData_thenReturnsNullSeasonWithLists() {
        // given
        var rs = new RaceScoring();
        rs.setId(UUID.randomUUID());
        var ms = new MatchScoring();
        ms.setId(UUID.randomUUID());

        when(teamRepository.findAll()).thenReturn(List.of());
        when(carRepository.findAllByOrderByManufacturerAscNameAsc()).thenReturn(List.of());
        when(trackRepository.findAllByOrderByNameAsc()).thenReturn(List.of());
        when(raceScoringRepository.findAll()).thenReturn(List.of(rs));
        when(matchScoringRepository.findAll()).thenReturn(List.of(ms));

        // when
        var result = service.getEditFormData(null);

        // then
        assertThat(result.season()).isNull();
        assertThat(result.allRaceScorings()).hasSize(1);
        assertThat(result.allMatchScorings()).hasSize(1);
    }

    @Test
    void givenNewSeasonPrimitives_whenSave_thenCreatesSeasonWithScoringLookups() {
        // given
        var rs = new RaceScoring();
        rs.setId(UUID.randomUUID());
        var ms = new MatchScoring();
        ms.setId(UUID.randomUUID());

        when(raceScoringRepository.findById(rs.getId())).thenReturn(Optional.of(rs));
        when(matchScoringRepository.findById(ms.getId())).thenReturn(Optional.of(ms));
        when(seasonRepository.save(any(Season.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        var result = service.save(null, "New Season", 2026, 1, null, null, null,
                true, SeasonFormat.LEAGUE, null, 1, null, rs.getId(), ms.getId());

        // then
        assertThat(result.getName()).isEqualTo("New Season");
        assertThat(result.getRaceScoring()).isEqualTo(rs);
        assertThat(result.getMatchScoring()).isEqualTo(ms);
        verify(seasonRepository).save(any(Season.class));
    }

    @Test
    void givenExistingSeasonPrimitives_whenSave_thenUpdatesSeasonFields() {
        // given
        var existing = createSeason("Old Name");
        var rs = new RaceScoring();
        rs.setId(UUID.randomUUID());
        existing.setRaceScoring(rs);
        var ms = new MatchScoring();
        ms.setId(UUID.randomUUID());
        existing.setMatchScoring(ms);

        when(seasonRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(raceScoringRepository.findById(rs.getId())).thenReturn(Optional.of(rs));
        when(matchScoringRepository.findById(ms.getId())).thenReturn(Optional.of(ms));
        when(seasonRepository.save(any(Season.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        var result = service.save(existing.getId(), "Updated Name", 2027, 2, null, null, null,
                false, SeasonFormat.SWISS, null, 2, null, rs.getId(), ms.getId());

        // then
        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getYear()).isEqualTo(2027);
        assertThat(result.getFormat()).isEqualTo(SeasonFormat.SWISS);
    }

    @Test
    void givenExistingSeason_whenDelete_thenRemoves() {
        // given
        var season = createSeason("Delete Me");
        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

        // when
        service.delete(season.getId());

        // then
        verify(seasonRepository).delete(season);
    }

    @Test
    void whenGetAllRaceScorings_thenDelegatesToRepository() {
        // given
        var rs = new RaceScoring();
        rs.setId(UUID.randomUUID());
        when(raceScoringRepository.findAll()).thenReturn(List.of(rs));

        // when
        var result = service.getAllRaceScorings();

        // then
        assertThat(result).hasSize(1);
        verify(raceScoringRepository).findAll();
    }

    @Test
    void whenGetAllMatchScorings_thenDelegatesToRepository() {
        // given
        var ms = new MatchScoring();
        ms.setId(UUID.randomUUID());
        when(matchScoringRepository.findAll()).thenReturn(List.of(ms));

        // when
        var result = service.getAllMatchScorings();

        // then
        assertThat(result).hasSize(1);
        verify(matchScoringRepository).findAll();
    }

    @Test
    void givenSeasonWithSwissFormat_whenGetSwissRoundData_thenReturnsRoundDataWithDriverTeamInfo() {
        // given
        var season = createSeason("Swiss Season");
        season.setFormat(SeasonFormat.SWISS);

        var homeTeam = createTeam("HOM", "Home Team");
        var awayTeam = createTeam("AWY", "Away Team");
        season.addTeam(homeTeam);
        season.addTeam(awayTeam);

        // Create a matchday with a race that has match scores
        var matchday = new Matchday(season, "Round 1", 1);
        matchday.setId(UUID.randomUUID());

        var match = new Match(matchday, homeTeam, awayTeam);
        match.setId(UUID.randomUUID());
        match.setHomeScore(10);
        match.setAwayScore(8);

        var race = new Race();
        race.setId(UUID.randomUUID());
        race.setMatch(match);
        race.setMatchday(matchday);
        match.getRaces().add(race);
        matchday.getRaces().add(race);
        matchday.getMatches().add(match);
        season.getMatchdays().add(matchday);

        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

        // when
        var result = service.getSwissRoundData(season.getId());

        // then
        assertThat(result.season()).isEqualTo(season);
        assertThat(result.raceScores()).containsKey(race.getId());
        assertThat(result.raceScores().get(race.getId())).isEqualTo(new int[]{10, 8});
    }

    @Nested
    class GetSeasonGroupOptions {

        @Test
        void givenTwoSeasonsWithSameYearAndNumber_whenGetSeasonGroupOptions_thenGroupedIntoOne() {
            // given
            var s1 = createSeason("Season 1A");
            s1.setYear(2026);
            s1.setNumber(1);
            var team1 = createTeam("T1", "Team 1");
            var team2 = createTeam("T2", "Team 2");
            season_addTeam(s1, team1);
            season_addTeam(s1, team2);

            var s2 = createSeason("Season 1B");
            s2.setYear(2026);
            s2.setNumber(1);
            var team3 = createTeam("T3", "Team 3");
            season_addTeam(s2, team3);

            when(seasonRepository.findAll()).thenReturn(List.of(s1, s2));

            // when
            var result = service.getSeasonGroupOptions();

            // then
            assertThat(result).hasSize(1);
            var option = result.getFirst();
            assertThat(option.year()).isEqualTo(2026);
            assertThat(option.number()).isEqualTo(1);
            assertThat(option.teamCount()).isEqualTo(3);
        }

        @Test
        void givenSeasonsFromDifferentYears_whenGetSeasonGroupOptions_thenSortedByYearDescThenNumberDesc() {
            // given
            var s1 = createSeason("Season 2025/1");
            s1.setYear(2025);
            s1.setNumber(1);

            var s2 = createSeason("Season 2026/2");
            s2.setYear(2026);
            s2.setNumber(2);

            when(seasonRepository.findAll()).thenReturn(List.of(s1, s2));

            // when
            var result = service.getSeasonGroupOptions();

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).year()).isEqualTo(2026);
            assertThat(result.get(0).number()).isEqualTo(2);
            assertThat(result.get(1).year()).isEqualTo(2025);
            assertThat(result.get(1).number()).isEqualTo(1);
        }

        @Test
        void givenNoSeasons_whenGetSeasonGroupOptions_thenReturnsEmptyList() {
            // given
            when(seasonRepository.findAll()).thenReturn(List.of());

            // when
            var result = service.getSeasonGroupOptions();

            // then
            assertThat(result).isEmpty();
        }

        private void season_addTeam(Season season, Team team) {
            var st = new SeasonTeam(season, team);
            season.getSeasonTeams().add(st);
        }
    }

    @Nested
    class FindActiveSeasonTest {

        @Test
        void givenActiveSeasonExists_whenFindActiveSeason_thenReturnsOptionalWithSeason() {
            // given
            var activeSeason = createSeason("Active Season");
            activeSeason.setActive(true);
            var inactiveSeason = createSeason("Inactive Season");
            inactiveSeason.setActive(false);
            when(seasonRepository.findAll()).thenReturn(List.of(inactiveSeason, activeSeason));

            // when
            var result = service.findActiveSeason();

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Active Season");
        }

        @Test
        void givenNoActiveSeason_whenFindActiveSeason_thenReturnsEmptyOptional() {
            // given
            var inactiveSeason = createSeason("Inactive Season");
            inactiveSeason.setActive(false);
            when(seasonRepository.findAll()).thenReturn(List.of(inactiveSeason));

            // when
            var result = service.findActiveSeason();

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    class FindByIdOptionalTest {

        @Test
        void givenSeasonExists_whenFindByIdOptional_thenReturnsOptionalWithSeason() {
            // given
            var season = createSeason("Test Season");
            when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

            // when
            var result = service.findByIdOptional(season.getId());

            // then
            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Test Season");
        }

        @Test
        void givenSeasonNotFound_whenFindByIdOptional_thenReturnsEmptyOptional() {
            // given
            var id = UUID.randomUUID();
            when(seasonRepository.findById(id)).thenReturn(Optional.empty());

            // when
            var result = service.findByIdOptional(id);

            // then
            assertThat(result).isEmpty();
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
