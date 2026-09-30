package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Removes teams from a season whose phases still roster other teams and checks that only the
 * selected team's own phase places block the removal.
 */
@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class SeasonTeamRemovalIT {

	@Autowired private SeasonManagementService seasonManagementService;
	@Autowired private SeasonPhaseService seasonPhaseService;
	@Autowired private TestHelper testHelper;
	@Autowired private SeasonRepository seasonRepository;
	@Autowired private PhaseTeamRepository phaseTeamRepository;
	@Autowired private EntityManager entityManager;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private Season season;
	private SeasonPhase regular;
	private Team alpha;
	private Team bravo;

	@BeforeEach
	void createSeasonWithTwoRosteredTeams() {
		season = testHelper.createSeason("Test_Removal_" + id);
		regular = seasonPhaseService.findRegularPhase(season.getId());
		alpha = testHelper.createTeam("Test Removal Alpha " + id, "Test_RMA_" + id);
		bravo = testHelper.createTeam("Test Removal Bravo " + id, "Test_RMB_" + id);
		seasonManagementService.addTeamToSeason(season.getId(), alpha.getId());
		seasonManagementService.addTeamToSeason(season.getId(), bravo.getId());
	}

	@Test
	void givenTeamWithoutPhasePlacesWhileAnotherTeamIsRostered_whenRemoved_thenItLeavesTheSeason() {
		// given
		unroster(alpha);

		// when
		assertThatCode(() -> seasonManagementService.removeTeamFromSeason(season.getId(), alpha.getId()))
				.as("only bravo is still rostered").doesNotThrowAnyException();

		// then
		var stored = reloadedSeason();
		assertThat(stored.containsTeam(alpha)).as("alpha left the season").isFalse();
		assertThat(stored.containsTeam(bravo)).as("bravo stays").isTrue();
		assertThat(phaseTeamRepository.findByPhaseIdAndTeamId(regular.getId(), bravo.getId()))
				.as("bravo keeps its phase place").isPresent();
	}

	@Test
	void givenTeamStillRostered_whenRemoved_thenRejectedAndItStays() {
		// when / then
		assertThatThrownBy(() -> seasonManagementService.removeTeamFromSeason(season.getId(), alpha.getId()))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessage("Cannot remove team from season: team is still assigned to one or more phase rosters. "
						+ "Remove it from all phases first.");
		assertThat(reloadedSeason().containsTeam(alpha)).as("alpha stays in the season").isTrue();
	}

	@Test
	void givenLastSubTeamWhoseParentHasNoPhasePlace_whenRemoved_thenTheParentLeavesToo() {
		// given
		var sub = subTeamOf(alpha);
		unroster(sub);
		unroster(alpha);

		// when
		assertThatCode(() -> seasonManagementService.removeTeamFromSeason(season.getId(), sub.getId()))
				.as("the sub-team has no phase place").doesNotThrowAnyException();

		// then
		var stored = reloadedSeason();
		assertThat(stored.containsTeam(sub)).as("sub-team left").isFalse();
		assertThat(stored.containsTeam(alpha)).as("parent without sub-teams and phase place left").isFalse();
	}

	@Test
	void givenLastSubTeamWhoseParentStillHasAPhasePlace_whenRemoved_thenTheParentStays() {
		// given
		var sub = subTeamOf(alpha);
		unroster(sub);

		// when
		assertThatCode(() -> seasonManagementService.removeTeamFromSeason(season.getId(), sub.getId()))
				.as("the sub-team has no phase place").doesNotThrowAnyException();

		// then
		var stored = reloadedSeason();
		assertThat(stored.containsTeam(sub)).as("sub-team left").isFalse();
		assertThat(stored.containsTeam(alpha)).as("the rostered parent stays in the season").isTrue();
	}

	private Team subTeamOf(Team parent) {
		var sub = testHelper.createSubTeam("Test Removal Sub " + id, "Test_RMS_" + id, parent);
		seasonManagementService.addTeamToSeason(season.getId(), sub.getId());
		return sub;
	}

	private void unroster(Team team) {
		phaseTeamRepository.findByPhaseIdAndTeamId(regular.getId(), team.getId()).ifPresent(phaseTeamRepository::delete);
		entityManager.flush();
	}

	private Season reloadedSeason() {
		entityManager.flush();
		entityManager.clear();
		return seasonRepository.findById(season.getId()).orElseThrow();
	}
}
