package org.ctc.dataimport;

import jakarta.persistence.EntityManager;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.dataimport.exception.ImportRejectedException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceRepository;
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
 * Imports scorecards whose team blocks come in A/B and B/A order and checks that they form one
 * pairing whose legs keep their orientation.
 */
@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class CsvImportReversedBlocksIT {

	@Autowired CsvImportService csvImportService;
	@Autowired TestHelper testHelper;
	@Autowired SeasonRepository seasonRepository;
	@Autowired MatchRepository matchRepository;
	@Autowired RaceRepository raceRepository;
	@Autowired EntityManager entityManager;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private Season season;
	private Matchday matchday;
	private Team alpha;
	private Team bravo;
	private Driver alphaDriver;
	private Driver bravoDriver;

	@BeforeEach
	void createSeasonWithTwoTeams() {
		season = testHelper.createSeason("Test_Reversed_" + id);
		alpha = testHelper.createTeam("Test Reversed Alpha " + id, "Test_RVA_" + id);
		bravo = testHelper.createTeam("Test Reversed Bravo " + id, "Test_RVB_" + id);
		season.addTeam(alpha);
		season.addTeam(bravo);
		seasonRepository.save(season);
		matchday = testHelper.createMatchdayInRegularPhase(season, "Test_Reversed MD " + id, 1);
		alphaDriver = testHelper.createDriver("Test_Reversed_" + id + "_A", "Test Reversed Alpha Driver");
		bravoDriver = testHelper.createDriver("Test_Reversed_" + id + "_B", "Test Reversed Bravo Driver");
		testHelper.createSeasonDriver(season, alphaDriver, alpha);
		testHelper.createSeasonDriver(season, bravoDriver, bravo);
	}

	@Test
	void givenTwoLegsWithReversedBlockOrder_whenImportedTogether_thenOnePairingWithCorrectlyOrientedLegs() throws Exception {
		// when
		csvImportService.executeMultiRaceImport(List.of(preview(alpha, bravo), preview(bravo, alpha)),
				Map.of(), Set.of(), false);

		// then
		var match = singleMatch();
		assertThat(match.getHomeTeam().getId()).as("the first leg sets the pairing's home team").isEqualTo(alpha.getId());
		var legs = legsOf(match);
		assertThat(legs).as("both scorecards are legs of one pairing").hasSize(2);
		assertThat(legs.get(0).getHomeTeam().getId()).as("leg 1 home").isEqualTo(alpha.getId());
		assertThat(legs.get(1).getHomeTeam().getId()).as("leg 2 home, reversed by its block order").isEqualTo(bravo.getId());
		assertThat(legs.get(1).getAwayTeam().getId()).as("leg 2 away").isEqualTo(alpha.getId());
		assertThat(match.getHomeScore()).as("alpha wins leg 1 and loses leg 2: 25 + 19").isEqualTo(44);
		assertThat(match.getAwayScore()).as("bravo: 19 + 25").isEqualTo(44);
	}

	@Test
	void givenImportedPairing_whenReimportedWithReversedBlocks_thenDetectedAsDuplicateAndRejected() throws Exception {
		// given
		csvImportService.executeImport(preview(alpha, bravo), Map.of(), Set.of(), false);
		var reversed = preview(bravo, alpha);

		// when / then
		assertThat(csvImportService.checkDuplicate(reversed)).as("reversed reimport is a duplicate").isTrue();
		assertThatThrownBy(() -> csvImportService.executeImport(reversed, Map.of(), Set.of(), false))
				.isInstanceOf(ImportRejectedException.class);
		assertThat(matchRepository.findByMatchdayId(matchday.getId())).as("no second pairing").hasSize(1);
	}

	@Test
	void givenImportedPairing_whenOverwrittenWithReversedBlocks_thenThePairingStaysAndTheLegIsReversed() throws Exception {
		// given
		csvImportService.executeImport(preview(alpha, bravo), Map.of(), Set.of(), false);

		// when
		csvImportService.executeImport(preview(bravo, alpha), Map.of(), Set.of(), true);

		// then
		var match = singleMatch();
		assertThat(match.getHomeTeam().getId()).as("canonical pairing home").isEqualTo(alpha.getId());
		var legs = legsOf(match);
		assertThat(legs).as("the overwrite replaces the leg").hasSize(1);
		assertThat(legs.getFirst().getHomeTeam().getId()).as("the new leg keeps its block order").isEqualTo(bravo.getId());
	}

	private CsvImportService.ImportPreview preview(Team first, Team second) throws Exception {
		Driver firstDriver = first.equals(alpha) ? alphaDriver : bravoDriver;
		Driver secondDriver = first.equals(alpha) ? bravoDriver : alphaDriver;
		String csv = """
				Team,PSN ID,Position,Quali,FL
				%s,%s,1,1,true
				%s,%s,2,2,false
				""".formatted(first.getShortName(), firstDriver.getPsnId(), second.getShortName(), secondDriver.getPsnId());
		return csvImportService.parseAndPreview(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
				new CsvImportService.ImportMetadata(season.getId(), null, null, null, null, matchday.getId()));
	}

	private Match singleMatch() {
		entityManager.flush();
		entityManager.clear();
		var matches = matchRepository.findByMatchdayId(matchday.getId());
		assertThat(matches).as("pairings on the matchday").hasSize(1);
		return matches.getFirst();
	}

	private List<Race> legsOf(Match match) {
		return raceRepository.findByMatchId(match.getId()).stream()
				.sorted(Comparator.comparing(Race::hasTeamOverrides))
				.toList();
	}
}
