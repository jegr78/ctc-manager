package org.ctc.domain.repository;

// @SpringBootTest precedent honored over D-13 @DataJpaTest — see RESEARCH Open Question 1

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying D-22 magic-naming finder on {@link SeasonPhaseGroupRepository}
 * against H2 (dev profile).
 *
 * Test data uses the "Phase58-Test-" prefix per CLAUDE.md "Isolate Test Data Completely".
 * {@code @Transactional} rolls back each test — no rows leak between tests.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
@Tag("integration")
class SeasonPhaseGroupRepositoryIT {

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private SeasonPhaseRepository seasonPhaseRepository;

    @Autowired
    private SeasonPhaseGroupRepository seasonPhaseGroupRepository;

    @Autowired
    private RaceScoringRepository raceScoringRepository;

    @Autowired
    private MatchScoringRepository matchScoringRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void givenPhaseWithGroups_whenFindByPhaseIdOrderBySortIndex_thenReturnsOrderedList() {
        // given — insert groups in reverse sort order to confirm ordering is DB-driven
        var season = seasonRepository.save(newSeason("Phase58-Test-SPGR-S1", 9989, 1));
        var phase = seasonPhaseRepository.save(newPhase(season, PhaseType.REGULAR, PhaseLayout.GROUPS, 0));
        var groupC = seasonPhaseGroupRepository.save(new SeasonPhaseGroup(phase, "Phase58-Test-Group-C", 2));
        var groupA = seasonPhaseGroupRepository.save(new SeasonPhaseGroup(phase, "Phase58-Test-Group-A", 0));
        var groupB = seasonPhaseGroupRepository.save(new SeasonPhaseGroup(phase, "Phase58-Test-Group-B", 1));
        entityManager.flush();
        entityManager.clear();

        // when
        List<SeasonPhaseGroup> result = seasonPhaseGroupRepository.findByPhaseIdOrderBySortIndex(phase.getId());

        // then
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getId()).isEqualTo(groupA.getId());
        assertThat(result.get(1).getId()).isEqualTo(groupB.getId());
        assertThat(result.get(2).getId()).isEqualTo(groupC.getId());
    }

    @Test
    void givenPhaseWithNoGroups_whenFindByPhaseIdOrderBySortIndex_thenReturnsEmptyList() {
        // given
        var season = seasonRepository.save(newSeason("Phase58-Test-SPGR-S2", 9989, 2));
        var phase = seasonPhaseRepository.save(newPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0));
        entityManager.flush();
        entityManager.clear();

        // when
        List<SeasonPhaseGroup> result = seasonPhaseGroupRepository.findByPhaseIdOrderBySortIndex(phase.getId());

        // then
        assertThat(result).isEmpty();
    }


    private Season newSeason(String name, int year, int number) {
        // scoring lives on the SeasonPhase, not the Season.
        return new Season(name, year, number);
    }

    private SeasonPhase newPhase(Season season, PhaseType phaseType, PhaseLayout layout, int sortIndex) {
        var phase = new SeasonPhase(season, phaseType, layout, sortIndex);
        phase.setRaceScoring(raceScoringRepository.findAll().get(0));
        phase.setMatchScoring(matchScoringRepository.findAll().get(0));
        return phase;
    }
}
