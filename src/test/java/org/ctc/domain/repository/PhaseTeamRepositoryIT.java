package org.ctc.domain.repository;

// @SpringBootTest precedent honored over D-13 @DataJpaTest — see RESEARCH Open Question 1

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying D-22 magic-naming finders on {@link PhaseTeamRepository}
 * against H2 (dev profile). Also covers D-18 delete-guard helper {@code existsByPhaseSeasonId}.
 *
 * Test data uses the "Phase58-Test-PT-" prefix per CLAUDE.md "Isolate Test Data Completely".
 * {@code @Transactional} rolls back each test — no rows leak between tests.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PhaseTeamRepositoryIT {

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private SeasonPhaseRepository seasonPhaseRepository;

    @Autowired
    private SeasonPhaseGroupRepository seasonPhaseGroupRepository;

    @Autowired
    private PhaseTeamRepository phaseTeamRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private RaceScoringRepository raceScoringRepository;

    @Autowired
    private MatchScoringRepository matchScoringRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void givenPhaseTeams_whenFindByPhaseId_thenReturnsAll() {
        // given
        var season = seasonRepository.save(newSeason("Phase58-Test-PT-S1", 9988, 1));
        var phase = seasonPhaseRepository.save(newPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0));
        var teamA = teamRepository.save(new Team("Phase58-Test-PT-TeamA", "P58A"));
        var teamB = teamRepository.save(new Team("Phase58-Test-PT-TeamB", "P58B"));
        phaseTeamRepository.save(new PhaseTeam(phase, teamA));
        phaseTeamRepository.save(new PhaseTeam(phase, teamB));
        entityManager.flush();
        entityManager.clear();

        // when
        List<PhaseTeam> result = phaseTeamRepository.findByPhaseId(phase.getId());

        // then
        assertThat(result).hasSize(2);
    }

    @Test
    void givenLeagueRosterWithNullGroup_whenFindByPhaseIdAndGroupIdNull_thenReturnsLeagueTeams() {
        // given
        var season = seasonRepository.save(newSeason("Phase58-Test-PT-S2", 9988, 2));
        var phase = seasonPhaseRepository.save(newPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0));
        var group = seasonPhaseGroupRepository.save(new SeasonPhaseGroup(phase, "Phase58-Test-PT-Grp", 0));
        var teamA = teamRepository.save(new Team("Phase58-Test-PT-TeamC", "P58C"));
        var teamB = teamRepository.save(new Team("Phase58-Test-PT-TeamD", "P58D"));

        // teamA has no group (LEAGUE-style)
        var ptA = new PhaseTeam(phase, teamA);
        phaseTeamRepository.save(ptA);

        // teamB is assigned to a group
        var ptB = new PhaseTeam(phase, teamB);
        ptB.setGroup(group);
        phaseTeamRepository.save(ptB);

        entityManager.flush();
        entityManager.clear();

        // when — null groupId should derive IS NULL in the query
        List<PhaseTeam> result = phaseTeamRepository.findByPhaseIdAndGroupId(phase.getId(), null);

        // then — only teamA (no group) is returned
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTeam().getId()).isEqualTo(teamA.getId());
    }

    @Test
    void givenSeasonWithPhaseTeams_whenExistsByPhaseSeasonId_thenTrue() {
        // given
        var season = seasonRepository.save(newSeason("Phase58-Test-PT-S3", 9988, 3));
        var phase = seasonPhaseRepository.save(newPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0));
        var team = teamRepository.save(new Team("Phase58-Test-PT-TeamE", "P58E"));
        phaseTeamRepository.save(new PhaseTeam(phase, team));
        entityManager.flush();
        entityManager.clear();

        // when
        boolean result = phaseTeamRepository.existsByPhaseSeasonId(season.getId());

        // then
        assertThat(result).isTrue();
    }

    @Test
    void givenSeasonWithoutPhaseTeams_whenExistsByPhaseSeasonId_thenFalse() {
        // given — season has a phase but no phase-teams
        var season = seasonRepository.save(newSeason("Phase58-Test-PT-S4", 9988, 4));
        seasonPhaseRepository.save(newPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0));
        entityManager.flush();
        entityManager.clear();

        // when
        boolean result = phaseTeamRepository.existsByPhaseSeasonId(season.getId());

        // then
        assertThat(result).isFalse();
    }

    // --- helpers ---

    private Season newSeason(String name, int year, int number) {
        // Phase 61 MIGR-06: scoring lives on the SeasonPhase, not the Season.
        return new Season(name, year, number);
    }

    private SeasonPhase newPhase(Season season, PhaseType phaseType, PhaseLayout layout, int sortIndex) {
        var phase = new SeasonPhase(season, phaseType, layout, sortIndex);
        phase.setRaceScoring(raceScoringRepository.findAll().get(0));
        phase.setMatchScoring(matchScoringRepository.findAll().get(0));
        return phase;
    }
}
