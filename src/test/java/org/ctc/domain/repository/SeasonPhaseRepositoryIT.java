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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying D-22 magic-naming finders on {@link SeasonPhaseRepository}
 * against H2 (dev profile).
 *
 * Test data uses the "Phase58-Test-" prefix per CLAUDE.md "Isolate Test Data Completely".
 * {@code @Transactional} rolls back each test — no rows leak between tests.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SeasonPhaseRepositoryIT {

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private SeasonPhaseRepository seasonPhaseRepository;

    @Autowired
    private RaceScoringRepository raceScoringRepository;

    @Autowired
    private MatchScoringRepository matchScoringRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void givenFixture_whenFindBySeasonIdAndPhaseType_thenReturnsExpected() {
        // given
        var season = seasonRepository.save(newSeason("Phase58-Test-SPR-S1", 9990, 1));
        var regular = seasonPhaseRepository.save(newPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0));
        seasonPhaseRepository.save(newPhase(season, PhaseType.PLAYOFF, PhaseLayout.BRACKET, 10));
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<SeasonPhase> result = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(regular.getId());
        assertThat(result.get().getPhaseType()).isEqualTo(PhaseType.REGULAR);
    }

    @Test
    void givenSeasonWithMultiplePhases_whenFindBySeasonIdOrderBySortIndex_thenReturnsOrderedList() {
        // given — insert in reverse sort order to confirm ordering is DB-driven
        var season = seasonRepository.save(newSeason("Phase58-Test-SPR-S2", 9990, 2));
        var playoff = seasonPhaseRepository.save(newPhase(season, PhaseType.PLAYOFF, PhaseLayout.BRACKET, 10));
        var regular = seasonPhaseRepository.save(newPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0));
        entityManager.flush();
        entityManager.clear();

        // when
        List<SeasonPhase> result = seasonPhaseRepository.findBySeasonIdOrderBySortIndex(season.getId());

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(regular.getId());
        assertThat(result.get(1).getId()).isEqualTo(playoff.getId());
    }

    @Test
    void givenNoPhaseForType_whenFindBySeasonIdAndPhaseType_thenReturnsEmpty() {
        // given
        var season = seasonRepository.save(newSeason("Phase58-Test-SPR-S3", 9990, 3));
        seasonPhaseRepository.save(newPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0));
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<SeasonPhase> result = seasonPhaseRepository.findBySeasonIdAndPhaseType(season.getId(), PhaseType.PLAYOFF);

        // then
        assertThat(result).isEmpty();
    }

    // --- helpers ---

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
