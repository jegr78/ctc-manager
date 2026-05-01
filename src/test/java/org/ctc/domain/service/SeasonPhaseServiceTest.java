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
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private SeasonPhaseService seasonPhaseService;

    // ---------------------------------------------------------------------------
    // findRegularPhase
    // ---------------------------------------------------------------------------

    @Test
    void givenSeasonWithRegularPhase_whenFindRegularPhase_thenReturnsPhase() {
        // given
        var season = buildSeason("Phase58-Test-Season-1");
        var phase = PhaseTestFixtures.regularPhase(season, getRs(season), getMs(season));
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
        var playoffPhase = PhaseTestFixtures.playoffPhase(season, "Phase58-Test-Playoff", getRs(season), getMs(season));
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
        var regular = PhaseTestFixtures.regularPhase(season, getRs(season), getMs(season));
        var playoff = PhaseTestFixtures.playoffPhase(season, "Phase58-Test-Playoff", getRs(season), getMs(season));
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
        var existingPhase = PhaseTestFixtures.regularPhase(season, getRs(season), getMs(season));
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
        var existing = PhaseTestFixtures.playoffPhase(season, "Phase58-Test-Playoff", getRs(season), getMs(season));
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
        existing.setRaceScoring(getRs(season));
        existing.setMatchScoring(getMs(season));
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
        var savedPhase = PhaseTestFixtures.regularPhase(season, getRs(season), getMs(season));
        when(seasonPhaseRepository.save(any(SeasonPhase.class))).thenReturn(savedPhase);
        when(phaseTeamRepository.save(any(PhaseTeam.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        seasonPhaseService.create(season.getId(), PhaseType.REGULAR, PhaseLayout.LEAGUE, 0,
                null, getRs(season), getMs(season),
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
        var savedPhase = PhaseTestFixtures.playoffPhase(season, "Phase58-Playoff", getRs(season), getMs(season));
        when(seasonPhaseRepository.save(any(SeasonPhase.class))).thenReturn(savedPhase);

        // when
        seasonPhaseService.create(season.getId(), PhaseType.PLAYOFF, PhaseLayout.BRACKET, 10,
                "Phase58-Playoff", getRs(season), getMs(season),
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
        var savedPhase = PhaseTestFixtures.groupsRegularPhase(season, getRs(season), getMs(season),
                "Phase58-Group-A", "Phase58-Group-B");
        when(seasonPhaseRepository.save(any(SeasonPhase.class))).thenReturn(savedPhase);

        // when
        seasonPhaseService.create(season.getId(), PhaseType.REGULAR, PhaseLayout.GROUPS, 0,
                null, getRs(season), getMs(season),
                SeasonFormat.LEAGUE, null, null, null, 1, null);

        // then — no PhaseTeam rows created (GROUPS roster starts empty)
        verify(phaseTeamRepository, never()).save(any(PhaseTeam.class));
    }

    // ---------------------------------------------------------------------------
    // Phase 60: update / delete / updateGroup / deleteGroup / assignTeamsToPhase
    // ---------------------------------------------------------------------------

    @Test
    void givenPhaseWithMatchdays_whenChangeLayout_thenThrowsBusinessRule() {
        // given: existing LEAGUE-layout phase that has matchdays
        var season = buildSeason("Phase60-Test-Season-Layout");
        var phase = PhaseTestFixtures.regularPhase(season, getRs(season), getMs(season));
        var matchday = mock(Matchday.class);
        when(seasonPhaseRepository.findById(phase.getId())).thenReturn(Optional.of(phase));
        when(matchdayRepository.findByPhaseId(phase.getId())).thenReturn(List.of(matchday));

        // when / then — changing layout when matchdays exist must throw
        assertThatThrownBy(() -> seasonPhaseService.update(
                phase.getId(), PhaseLayout.GROUPS, phase.getFormat(),
                null, null, null, null, null, phase.getLegs(), null, null, phase.getSortIndex()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("matchdays");
    }

    @Test
    void givenPhaseWithMatchdays_whenDelete_thenThrowsBusinessRule() {
        // given
        var season = buildSeason("Phase60-Test-Season-Delete");
        var phase = PhaseTestFixtures.regularPhase(season, getRs(season), getMs(season));
        var matchday = mock(Matchday.class);
        when(seasonPhaseRepository.findById(phase.getId())).thenReturn(Optional.of(phase));
        when(matchdayRepository.findByPhaseId(phase.getId())).thenReturn(List.of(matchday));

        // when / then
        assertThatThrownBy(() -> seasonPhaseService.delete(phase.getId()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenEmptyPhase_whenDelete_thenSucceeds() {
        // given
        var season = buildSeason("Phase60-Test-Season-EmptyDelete");
        var phase = PhaseTestFixtures.regularPhase(season, getRs(season), getMs(season));
        when(seasonPhaseRepository.findById(phase.getId())).thenReturn(Optional.of(phase));
        when(matchdayRepository.findByPhaseId(phase.getId())).thenReturn(List.of());
        when(phaseTeamRepository.findByPhaseId(phase.getId())).thenReturn(List.of());

        // when
        seasonPhaseService.delete(phase.getId());

        // then
        verify(seasonPhaseRepository).delete(phase);
    }

    @Test
    void givenGroupWithNewName_whenUpdateGroup_thenGroupNameUpdated() {
        // given
        var season = buildSeason("Phase60-Test-Season-UpdateGroup");
        var phase = PhaseTestFixtures.regularPhase(season, getRs(season), getMs(season));
        var group = new SeasonPhaseGroup(phase, "Phase60-Original-Name", 0);
        group.setId(UUID.randomUUID());
        when(seasonPhaseGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(seasonPhaseGroupRepository.save(any(SeasonPhaseGroup.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        seasonPhaseService.updateGroup(group.getId(), "Phase60-Renamed-Group", null);

        // then
        assertThat(group.getName()).isEqualTo("Phase60-Renamed-Group");
        verify(seasonPhaseGroupRepository).save(group);
    }

    @Test
    void givenGroupWithPhaseTeams_whenDeleteGroup_thenThrowsBusinessRule() {
        // given: group with at least one PhaseTeam (D-28 strict guard)
        var season = buildSeason("Phase60-Test-Season-DeleteGroup");
        var phase = PhaseTestFixtures.groupsRegularPhase(season, getRs(season), getMs(season), "Phase60-Group-A");
        var group = phase.getGroups().get(0);
        var phaseTeam = mock(PhaseTeam.class);
        when(seasonPhaseGroupRepository.findById(group.getId())).thenReturn(Optional.of(group));
        when(phaseTeamRepository.findByPhaseIdAndGroupId(phase.getId(), group.getId()))
                .thenReturn(List.of(phaseTeam));

        // when / then
        assertThatThrownBy(() -> seasonPhaseService.deleteGroup(group.getId()))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void givenRosterDiff_whenAssignTeamsToPhase_thenInsertsUpdatesAndDeletes() {
        // given: existing PhaseTeams [T1@GroupA, T2@null]
        var season = buildSeasonWithTeams("Phase60-Test-Season-Roster", 3);
        var teams = season.getSeasonTeams().stream().map(SeasonTeam::getTeam).toList();
        var teamT1 = teams.get(0);
        var teamT2 = teams.get(1);
        var teamT3 = teams.get(2);

        var phase = PhaseTestFixtures.groupsRegularPhase(season, getRs(season), getMs(season), "Phase60-Group-A", "Phase60-Group-B");
        var groupA = phase.getGroups().get(0);
        var groupB = phase.getGroups().get(1);

        // Existing roster: T1 in GroupA, T2 unassigned
        var existingPtT1 = PhaseTestFixtures.assignTeam(phase, teamT1, groupA);
        var existingPtT2 = PhaseTestFixtures.assignTeam(phase, teamT2, null);

        when(seasonPhaseRepository.findById(phase.getId())).thenReturn(Optional.of(phase));
        when(phaseTeamRepository.findByPhaseId(phase.getId())).thenReturn(List.of(existingPtT1, existingPtT2));
        when(seasonPhaseGroupRepository.findById(groupB.getId())).thenReturn(Optional.of(groupB));
        when(phaseTeamRepository.save(any(PhaseTeam.class))).thenAnswer(inv -> inv.getArgument(0));
        // T3 is an INSERT — service calls teamRepository.findById to resolve the Team entity
        when(teamRepository.findById(teamT3.getId())).thenReturn(Optional.of(teamT3));

        // New assignments: T1@GroupB (update group), T2 not included (delete), T3@null (insert)
        var assignments = List.of(
                new SeasonPhaseService.Assignment(teamT1.getId(), true, groupB.getId()),
                new SeasonPhaseService.Assignment(teamT2.getId(), false, null),
                new SeasonPhaseService.Assignment(teamT3.getId(), true, null)
        );

        // when
        seasonPhaseService.assignTeamsToPhase(phase.getId(), assignments);

        // then: T3 inserted (save), T1 updated (save), T2 removed (delete)
        var savedCaptor = ArgumentCaptor.forClass(PhaseTeam.class);
        verify(phaseTeamRepository, atLeastOnce()).save(savedCaptor.capture());
        verify(phaseTeamRepository).delete(existingPtT2);
        // T1's group updated to GroupB
        assertThat(existingPtT1.getGroup()).isEqualTo(groupB);
    }

    @Test
    void givenAssignmentUnchanged_whenAssignTeamsToPhase_thenNoWrite() {
        // given: T1 is already in GroupA and the new assignment says T1@GroupA included
        var season = buildSeasonWithTeams("Phase60-Test-Season-NoOp", 1);
        var teamT1 = season.getSeasonTeams().stream().map(SeasonTeam::getTeam).findFirst().orElseThrow();
        var phase = PhaseTestFixtures.groupsRegularPhase(season, getRs(season), getMs(season), "Phase60-Group-NoOp");
        var groupA = phase.getGroups().get(0);
        var existingPt = PhaseTestFixtures.assignTeam(phase, teamT1, groupA);

        when(seasonPhaseRepository.findById(phase.getId())).thenReturn(Optional.of(phase));
        when(phaseTeamRepository.findByPhaseId(phase.getId())).thenReturn(List.of(existingPt));
        // Note: seasonPhaseGroupRepository.findById NOT stubbed — no-op path avoids the group lookup

        // New assignment: T1@GroupA unchanged
        var assignments = List.of(new SeasonPhaseService.Assignment(teamT1.getId(), true, groupA.getId()));

        // when
        seasonPhaseService.assignTeamsToPhase(phase.getId(), assignments);

        // then: no save or delete on phaseTeamRepository (no-op)
        verify(phaseTeamRepository, never()).save(any(PhaseTeam.class));
        verify(phaseTeamRepository, never()).delete(any(PhaseTeam.class));
    }

    // W-11: phaseType immutability — update() signature must not accept phaseType
    @Test
    void givenExistingPhase_whenUpdateWithSameLayout_thenPhaseTypePersisted() {
        // given: existing REGULAR phase
        var season = buildSeason("Phase60-Test-Season-PhaseTypeImmutable");
        var phase = PhaseTestFixtures.regularPhase(season, getRs(season), getMs(season));
        when(seasonPhaseRepository.findById(phase.getId())).thenReturn(Optional.of(phase));
        when(matchdayRepository.findByPhaseId(phase.getId())).thenReturn(List.of());
        when(seasonPhaseRepository.save(any(SeasonPhase.class))).thenAnswer(inv -> inv.getArgument(0));

        // when: update() is called — no phaseType parameter exists in this signature (W-11)
        seasonPhaseService.update(phase.getId(), phase.getLayout(), phase.getFormat(),
                null, null, null, null, null, phase.getLegs(), null, "T-Phase60-Renamed",
                phase.getSortIndex());

        // then: phaseType is unchanged (update signature does not accept phaseType → W-11)
        var captor = ArgumentCaptor.forClass(SeasonPhase.class);
        verify(seasonPhaseRepository).save(captor.capture());
        assertThat(captor.getValue().getPhaseType()).isEqualTo(PhaseType.REGULAR);
    }

    // ---------------------------------------------------------------------------
    // Helpers (Phase 60 additions)
    // ---------------------------------------------------------------------------

    @Mock
    private MatchdayRepository matchdayRepository;

    // Phase 61 MIGR-06: scoring lives on SeasonPhase. The test-class stashes per-season RS/MS
    // so test methods can pass them into PhaseTestFixtures without needing a phase fixture upfront.
    private final java.util.Map<UUID, RaceScoring> rsBySeason = new java.util.HashMap<>();
    private final java.util.Map<UUID, MatchScoring> msBySeason = new java.util.HashMap<>();

    private Season buildSeason(String name) {
        var rs = new RaceScoring();
        rs.setId(UUID.randomUUID());
        var ms = new MatchScoring();
        ms.setId(UUID.randomUUID());
        var season = new Season(name, 9997, 1);
        season.setId(UUID.randomUUID());
        rsBySeason.put(season.getId(), rs);
        msBySeason.put(season.getId(), ms);
        return season;
    }

    private RaceScoring getRs(Season season) {
        return rsBySeason.get(season.getId());
    }

    private MatchScoring getMs(Season season) {
        return msBySeason.get(season.getId());
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
