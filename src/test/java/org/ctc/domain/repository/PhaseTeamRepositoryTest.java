package org.ctc.domain.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for the finder {@code findByPhaseIdAndTeamId}.
 *
 * Test data uses the "Phase59-Test-PT-" prefix per CLAUDE.md "Isolate Test Data Completely".
 * {@code @Transactional} rolls back each test — no rows leak between tests.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PhaseTeamRepositoryTest {

    @Autowired
    private SeasonRepository seasonRepository;

    @Autowired
    private SeasonPhaseRepository seasonPhaseRepository;

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
    void givenPhaseTeamForTeam_whenFindByPhaseIdAndTeamId_thenReturnsIt() {
        // given
        var season = seasonRepository.save(newSeason("Phase59-Test-PT-S1", 9901, 1));
        var phase = seasonPhaseRepository.save(newPhase(season));
        var team = teamRepository.save(new Team("Phase59-Test-PT-TeamA", "P59A-" + UUID.randomUUID().toString().substring(0, 4)));
        var pt = phaseTeamRepository.save(new PhaseTeam(phase, team));
        entityManager.flush();
        entityManager.clear();

        // when
        Optional<PhaseTeam> found = phaseTeamRepository.findByPhaseIdAndTeamId(phase.getId(), team.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(pt.getId());
    }

    @Test
    void givenNoPhaseTeam_whenFindByPhaseIdAndTeamId_thenReturnsEmpty() {
        // given
        var season = seasonRepository.save(newSeason("Phase59-Test-PT-S2", 9902, 1));
        var phase = seasonPhaseRepository.save(newPhase(season));
        var team = teamRepository.save(new Team("Phase59-Test-PT-TeamB", "P59B-" + UUID.randomUUID().toString().substring(0, 4)));
        phaseTeamRepository.save(new PhaseTeam(phase, team));
        entityManager.flush();
        entityManager.clear();

        // when — same phase, different (random) team UUID
        Optional<PhaseTeam> found = phaseTeamRepository.findByPhaseIdAndTeamId(phase.getId(), UUID.randomUUID());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void givenSameTeamOnDifferentPhase_whenFindByPhaseIdAndTeamId_thenReturnsEmpty() {
        // given — team T is bound to phase1, but we query with phase2
        var season1 = seasonRepository.save(newSeason("Phase59-Test-PT-S3", 9903, 1));
        var phase1 = seasonPhaseRepository.save(newPhase(season1));
        var team = teamRepository.save(new Team("Phase59-Test-PT-TeamC", "P59C-" + UUID.randomUUID().toString().substring(0, 4)));
        phaseTeamRepository.save(new PhaseTeam(phase1, team));

        var season2 = seasonRepository.save(newSeason("Phase59-Test-PT-S4", 9904, 1));
        var phase2 = seasonPhaseRepository.save(newPhase(season2));
        // team is NOT added to phase2
        entityManager.flush();
        entityManager.clear();

        // when — phase2, same team → no PhaseTeam
        Optional<PhaseTeam> found = phaseTeamRepository.findByPhaseIdAndTeamId(phase2.getId(), team.getId());

        // then
        assertThat(found).isEmpty();
    }


    private Season newSeason(String name, int year, int number) {
        // scoring lives on the SeasonPhase, not the Season.
        return new Season(name, year, number);
    }

    private SeasonPhase newPhase(Season season) {
        var phase = new SeasonPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
        phase.setRaceScoring(raceScoringRepository.findAll().get(0));
        phase.setMatchScoring(matchScoringRepository.findAll().get(0));
        return phase;
    }
}
