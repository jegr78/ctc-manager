package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Playoff;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Saves the seeding form of a two-team playoff the way the page submits it and reads the seeding
 * data back.
 */
@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class PlayoffSeedingClearIT {

	@Autowired private PlayoffSeedingService playoffSeedingService;
	@Autowired private PlayoffService playoffService;
	@Autowired private TestHelper testHelper;
	@Autowired private SeasonRepository seasonRepository;
	@Autowired private PlayoffMatchupRepository playoffMatchupRepository;
	@Autowired private EntityManager entityManager;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private Playoff playoff;
	private PlayoffMatchup finale;
	private Team alpha;
	private Team bravo;

	@BeforeEach
	void createTwoTeamPlayoff() {
		var season = testHelper.createSeason("Test_SeedClear_" + id);
		alpha = testHelper.createTeam("Test SeedClear Alpha " + id, "Test_SCA_" + id);
		bravo = testHelper.createTeam("Test SeedClear Bravo " + id, "Test_SCB_" + id);
		season.addTeam(alpha);
		season.addTeam(bravo);
		seasonRepository.save(season);
		playoff = playoffService.createPlayoff(season.getId(), "Test SeedClear " + id, 2);
		finale = playoffMatchupRepository.findByRoundPlayoffId(playoff.getId()).stream()
				.min(Comparator.comparing(PlayoffMatchup::getBracketPosition)).orElseThrow();
	}

	@Test
	void givenSeededSlots_whenSavedWithOneSlotCleared_thenTheSlotAndItsSeedNumberAreGone() {
		// given
		save(alpha.getId(), 1, bravo.getId(), 2);

		// when
		save(alpha.getId(), 1, null, null);

		// then
		var data = reloaded();
		assertThat(data.seededTeamIds()).as("seeded teams after the clear").containsExactly(alpha.getId());
		assertThat(data.seedNumbers()).as("seed numbers after the clear").containsOnlyKeys(alpha.getId());
		assertThat(data.firstRound().getMatchups().getFirst().getTeam2()).as("cleared slot").isNull();
	}

	@Test
	void givenSeededSlots_whenSavedAllEmpty_thenNoSlotAndNoSeedNumberRemains() {
		// given
		save(alpha.getId(), 1, bravo.getId(), 2);

		// when
		save(null, null, null, null);

		// then
		var data = reloaded();
		assertThat(data.seededTeamIds()).as("seeded teams").isEmpty();
		assertThat(data.seedNumbers()).as("seed numbers").isEmpty();
	}

	@Test
	void givenStartedPlayoff_whenSeedingSaved_thenRejectedWithoutChanges() {
		// given
		save(alpha.getId(), 1, bravo.getId(), 2);
		playoffService.addRaceToMatchup(finale.getId(), null, null, null);
		playoffService.setWinnerManually(finale.getId(), alpha.getId(), "Test_SeedClear bravo withdrew");

		// when / then
		assertThat(reloaded().seedingFrozen()).as("seeding is frozen").isTrue();
		assertThatThrownBy(() -> save(null, null, null, null))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Seeding is frozen once a playoff matchup has results or a winner");
		var data = reloaded();
		assertThat(data.seededTeamIds()).as("slots unchanged").containsExactlyInAnyOrder(alpha.getId(), bravo.getId());
		assertThat(data.seedNumbers()).as("seed numbers unchanged").containsOnlyKeys(alpha.getId(), bravo.getId());
	}

	@Test
	void givenSlotOfAnotherPlayoff_whenSeedingSaved_thenRejected() {
		// given
		var otherSeason = testHelper.createSeason("Test_SeedClear_Other_" + id);
		var other = playoffService.createPlayoff(otherSeason.getId(), "Test SeedClear Other " + id, 2);
		var foreignMatchup = playoffMatchupRepository.findByRoundPlayoffId(other.getId()).getFirst();

		// when / then
		assertThatThrownBy(() -> playoffSeedingService.saveSeed(playoff.getId(), List.of(
				new PlayoffSeedingService.SeedEntry(foreignMatchup.getId(), 1, alpha.getId(), 1))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("The seeding slot does not belong to this playoff");
		entityManager.flush();
		entityManager.clear();
		assertThat(playoffMatchupRepository.findById(foreignMatchup.getId()).orElseThrow().getTeam1())
				.as("the other playoff's slot").isNull();
	}

	@Test
	void givenSlotOfALaterRoundOrWithoutMatchup_whenSeedingSaved_thenRejected() {
		// given
		var fourTeams = testHelper.createSeason("Test_SeedClear_Four_" + id);
		var bracket = playoffService.createPlayoff(fourTeams.getId(), "Test SeedClear Four " + id, 4);
		var laterRound = playoffMatchupRepository.findByRoundPlayoffId(bracket.getId()).stream()
				.filter(matchup -> matchup.getRound().getRoundIndex() == 1).findFirst().orElseThrow();

		// when / then
		assertThatThrownBy(() -> playoffSeedingService.saveSeed(bracket.getId(), List.of(
				new PlayoffSeedingService.SeedEntry(laterRound.getId(), 1, alpha.getId(), 1))))
				.as("a later-round slot").isInstanceOf(IllegalArgumentException.class)
				.hasMessage("The seeding slot does not belong to this playoff");
		assertThatThrownBy(() -> playoffSeedingService.saveSeed(bracket.getId(), List.of(
				new PlayoffSeedingService.SeedEntry(null, 1, alpha.getId(), 1))))
				.as("a slot without matchup").isInstanceOf(IllegalArgumentException.class)
				.hasMessage("The seeding slot does not belong to this playoff");
	}

	@Test
	void givenOneSeedNumberForTwoSlots_whenAutoSeeded_thenRejectedWithAMessage() {
		// given
		save(alpha.getId(), 1, bravo.getId(), null);

		// when / then
		assertThatThrownBy(() -> playoffSeedingService.autoSeedBracket(playoff.getId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Auto-seeding needs seed numbers for all 2 teams, found 1");
	}

	private void save(UUID team1, Integer seed1, UUID team2, Integer seed2) {
		playoffSeedingService.saveSeed(playoff.getId(), List.of(
				new PlayoffSeedingService.SeedEntry(finale.getId(), 1, team1, seed1),
				new PlayoffSeedingService.SeedEntry(finale.getId(), 2, team2, seed2)));
		entityManager.flush();
		entityManager.clear();
	}

	private PlayoffSeedingService.SeedingData reloaded() {
		entityManager.flush();
		entityManager.clear();
		return playoffSeedingService.getSeedingData(playoff.getId());
	}
}
