package org.ctc.domain.model;

import org.ctc.domain.repository.MatchScoringRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.RaceScoringRepository;
import org.ctc.domain.repository.SeasonPhaseGroupRepository;
import org.ctc.domain.repository.SeasonPhaseRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests proving the V3-declared UNIQUE constraints reject duplicates
 * at the database level (REQ MODEL-02 / MODEL-04, decision D-03).
 *
 * Verified constraints:
 * - uk_season_phase_type — at most one (season_id, phase_type) tuple per season
 *   (a season cannot host two REGULAR phases, two PLAYOFF phases, etc.)
 * - uk_phase_team — at most one (phase_id, team_id) tuple per phase
 *   (a team appears at most once in a phase, regardless of group_id)
 *
 * Each test forces the constraint check inside the test method via
 * `saveAndFlush(...)` (rather than `save(...)` which defers the check to commit
 * time after the test has already returned). The exact exception type
 * `DataIntegrityViolationException` is asserted — a green test conclusively
 * proves the DB rejected the duplicate (mitigates threat T-56-19 in the plan
 * threat register).
 *
 * Test data uses the "Phase56-Uniq" prefix per CLAUDE.md "Isolate Test Data
 * Completely" rule. `@Transactional` rolls each test back so failed inserts and
 * partial state never leak into the dev seed data.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PhaseTeamUniquenessIntegrationTest {

	@Autowired
	private SeasonRepository seasonRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private RaceScoringRepository raceScoringRepository;

	@Autowired
	private MatchScoringRepository matchScoringRepository;

	@Autowired
	private SeasonPhaseRepository seasonPhaseRepository;

	@Autowired
	private SeasonPhaseGroupRepository seasonPhaseGroupRepository;

	@Autowired
	private PhaseTeamRepository phaseTeamRepository;

	@Test
	void givenSeasonWithRegularPhase_whenSecondRegularPhaseInserted_thenViolatesUniqueSeasonPhaseType() {
		// given
		var season = seasonRepository.save(newSeason("Phase56-Uniq-S1", 9998, 1));
		var firstRegular = newPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		seasonPhaseRepository.saveAndFlush(firstRegular);

		// positive control: a different phase_type on the same season is allowed
		// because the constraint is on the (season_id, phase_type) tuple, not on
		// season_id alone.
		var playoffOnSameSeason = newPhase(season, PhaseType.PLAYOFF, PhaseLayout.BRACKET, 1);
		var savedPlayoff = seasonPhaseRepository.saveAndFlush(playoffOnSameSeason);
		assertThat(savedPlayoff.getId()).isNotNull();

		var secondRegular = newPhase(season, PhaseType.REGULAR, PhaseLayout.LEAGUE, 2);

		// when / then — DB-level uk_season_phase_type rejects the duplicate REGULAR phase
		assertThatThrownBy(() -> seasonPhaseRepository.saveAndFlush(secondRegular))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void givenPhaseTeamForTeamA_whenSecondPhaseTeamForTeamAInserted_thenViolatesUniquePhaseTeam() {
		// given
		var season = seasonRepository.save(newSeason("Phase56-Uniq-S2", 9998, 2));
		var phase = newPhase(season, PhaseType.REGULAR, PhaseLayout.GROUPS, 0);
		seasonPhaseRepository.saveAndFlush(phase);
		var groupA = seasonPhaseGroupRepository.saveAndFlush(new SeasonPhaseGroup(phase, "Phase56-Uniq-Group-A", 0));
		var groupB = seasonPhaseGroupRepository.saveAndFlush(new SeasonPhaseGroup(phase, "Phase56-Uniq-Group-B", 1));
		var team = teamRepository.save(new Team("Phase56-Uniq-Team", "P56U"));

		var first = new PhaseTeam(phase, team);
		first.setGroup(groupA);
		phaseTeamRepository.saveAndFlush(first);

		// Same (phase, team) pair again, but in a different group — uk_phase_team
		// must still reject because the constraint is on (phase_id, team_id) only.
		var duplicate = new PhaseTeam(phase, team);
		duplicate.setGroup(groupB);

		// when / then — DB-level uk_phase_team rejects the duplicate (phase, team) tuple
		assertThatThrownBy(() -> phaseTeamRepository.saveAndFlush(duplicate))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// --- helpers -----------------------------------------------------------

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
