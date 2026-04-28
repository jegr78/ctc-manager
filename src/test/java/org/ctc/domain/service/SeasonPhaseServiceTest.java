package org.ctc.domain.service;

import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class SeasonPhaseServiceTest {

    @Mock
    private SeasonPhaseRepository seasonPhaseRepository;

    @Mock
    private SeasonPhaseGroupRepository seasonPhaseGroupRepository;

    @Mock
    private PhaseTeamRepository phaseTeamRepository;

    @Mock
    private SeasonRepository seasonRepository;

    @InjectMocks
    private SeasonPhaseService seasonPhaseService;

    // ---------------------------------------------------------------------------
    // findRegularPhase
    // ---------------------------------------------------------------------------

    @Test
    void givenSeasonWithRegularPhase_whenFindRegularPhase_thenReturnsPhase() {
        // given
        var season = buildSeason("Phase58-Test-Season-1");
        var phase = PhaseTestFixtures.regularPhase(season, season.getRaceScoring(), season.getMatchScoring());
        when(seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR))
                .thenReturn(Optional.of(phase));

        // when
        SeasonPhase result = seasonPhaseService.findRegularPhase(season.getId());

        // then
        assertThat(result).isEqualTo(phase);
        assertThat(result.getPhaseType()).isEqualTo(PhaseType.REGULAR);
    }

    @Test
    void givenMissingRegularPhase_whenFindRegularPhase_thenThrowsEntityNotFound() {
        // given
        var seasonId = UUID.randomUUID();
        when(seasonPhaseRepository.findBySeasonIdAndPhaseType(seasonId, PhaseType.REGULAR))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> seasonPhaseService.findRegularPhase(seasonId))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ---------------------------------------------------------------------------
    // findByType
    // ---------------------------------------------------------------------------

    @Test
    void givenSeasonWithPlayoffPhase_whenFindByTypePlayoff_thenReturnsOptionalOfPhase() {
        // given
        var season = buildSeason("Phase58-Test-Season-2");
        var playoffPhase = PhaseTestFixtures.playoffPhase(season, "Phase58-Test-Playoff", season.getRaceScoring(), season.getMatchScoring());
        when(seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.PLAYOFF))
                .thenReturn(Optional.of(playoffPhase));

        // when
        Optional<SeasonPhase> result = seasonPhaseService.findByType(season.getId(), PhaseType.PLAYOFF);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getPhaseType()).isEqualTo(PhaseType.PLAYOFF);
    }

    @Test
    void givenSeasonWithoutPlayoffPhase_whenFindByTypePlayoff_thenReturnsEmptyOptional() {
        // given
        var seasonId = UUID.randomUUID();
        when(seasonPhaseRepository.findBySeasonIdAndPhaseType(seasonId, PhaseType.PLAYOFF))
                .thenReturn(Optional.empty());

        // when
        Optional<SeasonPhase> result = seasonPhaseService.findByType(seasonId, PhaseType.PLAYOFF);

        // then
        assertThat(result).isEmpty();
    }

    // ---------------------------------------------------------------------------
    // findAllPhases
    // ---------------------------------------------------------------------------

    @Test
    void givenSeasonWithMultiplePhases_whenFindAllPhases_thenReturnsOrderedBySortIndex() {
        // given
        var season = buildSeason("Phase58-Test-Season-3");
        var regular = PhaseTestFixtures.regularPhase(season, season.getRaceScoring(), season.getMatchScoring());
        var playoff = PhaseTestFixtures.playoffPhase(season, "Phase58-Test-Playoff", season.getRaceScoring(), season.getMatchScoring());
        when(seasonPhaseRepository.findBySeasonIdOrderBySortIndex(season.getId()))
                .thenReturn(List.of(regular, playoff));

        // when
        List<SeasonPhase> result = seasonPhaseService.findAllPhases(season.getId());

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSortIndex()).isLessThanOrEqualTo(result.get(1).getSortIndex());
    }

    // ---------------------------------------------------------------------------
    // create — duplicate guard (D-14)
    // ---------------------------------------------------------------------------

    @Test
    void givenExistingRegularPhase_whenCreateRegular_thenThrowsBusinessRuleException() {
        // given
        var season = buildSeason("Phase58-Test-Season-4");
        var existingPhase = PhaseTestFixtures.regularPhase(season, season.getRaceScoring(), season.getMatchScoring());
        when(seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR))
                .thenReturn(Optional.of(existingPhase));

        // when / then
        assertThatThrownBy(() -> seasonPhaseService.create(season.getId(), PhaseType.REGULAR, PhaseLayout.LEAGUE, 0,
                null, null, null, null, null, null, null, 1, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Season already has REGULAR phase");
    }

    @Test
    void givenExistingPlayoffPhase_whenCreatePlayoff_thenThrowsBusinessRuleException() {
        // given
        var season = buildSeason("Phase58-Test-Season-5");
        var existing = PhaseTestFixtures.playoffPhase(season, "Phase58-Test-Playoff", season.getRaceScoring(), season.getMatchScoring());
        when(seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.PLAYOFF))
                .thenReturn(Optional.of(existing));

        // when / then
        assertThatThrownBy(() -> seasonPhaseService.create(season.getId(), PhaseType.PLAYOFF, PhaseLayout.BRACKET, 10,
                "Phase58-Playoff", null, null, null, null, null, null, 1, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Season already has PLAYOFF phase");
    }

    @Test
    void givenExistingPlacementPhase_whenCreatePlacement_thenThrowsBusinessRuleException() {
        // given
        var season = buildSeason("Phase58-Test-Season-6");
        var existing = new SeasonPhase(season, PhaseType.PLACEMENT, PhaseLayout.LEAGUE, 20);
        existing.setId(UUID.randomUUID());
        existing.setRaceScoring(season.getRaceScoring());
        existing.setMatchScoring(season.getMatchScoring());
        when(seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.PLACEMENT))
                .thenReturn(Optional.of(existing));

        // when / then
        assertThatThrownBy(() -> seasonPhaseService.create(season.getId(), PhaseType.PLACEMENT, PhaseLayout.LEAGUE, 20,
                null, null, null, null, null, null, null, 1, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Season already has PLACEMENT phase");
    }

    // ---------------------------------------------------------------------------
    // create — PhaseTeam auto-derivation (D-20)
    // ---------------------------------------------------------------------------

    @Test
    void givenSeasonWith3SeasonTeams_whenCreateRegularLeaguePhase_then3PhaseTeamsCreated() {
        // given
        var season = buildSeasonWithTeams("Phase58-Test-Season-7", 3);
        when(seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR))
                .thenReturn(Optional.empty());
        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        var savedPhase = PhaseTestFixtures.regularPhase(season, season.getRaceScoring(), season.getMatchScoring());
        when(seasonPhaseRepository.save(any(SeasonPhase.class))).thenReturn(savedPhase);
        when(phaseTeamRepository.save(any(PhaseTeam.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        seasonPhaseService.create(season.getId(), PhaseType.REGULAR, PhaseLayout.LEAGUE, 0,
                null, season.getRaceScoring(), season.getMatchScoring(),
                SeasonFormat.LEAGUE, null, null, null, 1, null);

        // then — exactly 3 PhaseTeam rows created
        verify(phaseTeamRepository, times(3)).save(any(PhaseTeam.class));
    }

    @Test
    void givenPlayoffType_whenCreatePlayoffPhase_thenNoPhaseTeamsCreated() {
        // given
        var season = buildSeasonWithTeams("Phase58-Test-Season-8", 4);
        when(seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.PLAYOFF))
                .thenReturn(Optional.empty());
        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        var savedPhase = PhaseTestFixtures.playoffPhase(season, "Phase58-Playoff", season.getRaceScoring(), season.getMatchScoring());
        when(seasonPhaseRepository.save(any(SeasonPhase.class))).thenReturn(savedPhase);

        // when
        seasonPhaseService.create(season.getId(), PhaseType.PLAYOFF, PhaseLayout.BRACKET, 10,
                "Phase58-Playoff", season.getRaceScoring(), season.getMatchScoring(),
                SeasonFormat.LEAGUE, null, null, null, 1, null);

        // then — no PhaseTeam rows created (PLAYOFF starts empty)
        verify(phaseTeamRepository, never()).save(any(PhaseTeam.class));
    }

    @Test
    void givenGroupsLayout_whenCreateRegularGroupsPhase_thenNoPhaseTeamsCreated() {
        // given
        var season = buildSeasonWithTeams("Phase58-Test-Season-9", 4);
        when(seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR))
                .thenReturn(Optional.empty());
        when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
        var savedPhase = PhaseTestFixtures.groupsRegularPhase(season, season.getRaceScoring(), season.getMatchScoring(),
                "Phase58-Group-A", "Phase58-Group-B");
        when(seasonPhaseRepository.save(any(SeasonPhase.class))).thenReturn(savedPhase);

        // when
        seasonPhaseService.create(season.getId(), PhaseType.REGULAR, PhaseLayout.GROUPS, 0,
                null, season.getRaceScoring(), season.getMatchScoring(),
                SeasonFormat.LEAGUE, null, null, null, 1, null);

        // then — no PhaseTeam rows created (GROUPS roster starts empty)
        verify(phaseTeamRepository, never()).save(any(PhaseTeam.class));
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private Season buildSeason(String name) {
        var rs = new RaceScoring();
        rs.setId(UUID.randomUUID());
        var ms = new MatchScoring();
        ms.setId(UUID.randomUUID());
        var season = new Season(name, 9997, 1);
        season.setId(UUID.randomUUID());
        season.setRaceScoring(rs);
        season.setMatchScoring(ms);
        return season;
    }

    private Season buildSeasonWithTeams(String name, int teamCount) {
        var season = buildSeason(name);
        for (int i = 0; i < teamCount; i++) {
            var team = new Team("Phase58-Test-Team-" + i, "P58T" + i);
            team.setId(UUID.randomUUID());
            var st = new SeasonTeam(season, team);
            st.setId(UUID.randomUUID());
            season.getSeasonTeams().add(st);
        }
        return season;
    }
}
