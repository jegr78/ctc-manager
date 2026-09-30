package org.ctc.domain.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.Season;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class MatchdayEditPhaseIT {

	@Autowired private MatchdayService matchdayService;
	@Autowired private SeasonPhaseService seasonPhaseService;
	@Autowired private PlayoffService playoffService;
	@Autowired private TestHelper testHelper;
	@Autowired private MatchdayRepository matchdayRepository;
	@Autowired private EntityManager entityManager;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private Season season;
	private SeasonPhase regular;

	@BeforeEach
	void createSeason() {
		season = testHelper.createSeason("Test_MdEdit_" + id);
		regular = seasonPhaseService.findRegularPhase(season.getId());
	}

	@Test
	void givenRegularMatchday_whenRelabelled_thenItStaysInTheRegularPhase() {
		// given
		var matchday = testHelper.createMatchdayInRegularPhase(season, "Test_MdEdit R " + id, 1);

		// when
		matchdayService.saveMatchday("Test_MdEdit R2 " + id, 2, season.getId(), matchday.getId());

		// then
		var stored = stored(matchday);
		assertThat(stored.getLabel()).as("label").isEqualTo("Test_MdEdit R2 " + id);
		assertThat(stored.getSortIndex()).as("sort index").isEqualTo(2);
		assertThat(stored.getPhase().getId()).as("phase").isEqualTo(regular.getId());
	}

	@Test
	void givenPlayoffMatchday_whenRelabelled_thenItStaysInThePlayoffPhase() {
		// given
		var playoff = playoffService.createPlayoff(season.getId(), "Test MdEdit Playoff " + id, 2);
		var matchday = matchdayRepository.save(new Matchday(playoff.getPhase(), "Test_MdEdit P " + id, 100));

		// when
		matchdayService.saveMatchday("Test_MdEdit P2 " + id, 101, season.getId(), matchday.getId());

		// then
		assertThat(stored(matchday).getPhase().getId()).as("phase").isEqualTo(playoff.getPhase().getId());
	}

	@Test
	void givenGroupedPlacementMatchday_whenRelabelled_thenPhaseAndGroupStay() {
		// given
		var placement = seasonPhaseService.create(season.getId(), PhaseType.PLACEMENT, PhaseLayout.GROUPS, 5,
				"Placement", regular.getRaceScoring(), regular.getMatchScoring(), SeasonFormat.LEAGUE,
				null, null, null, 1, null);
		var group = seasonPhaseService.createGroup(placement.getId(), "Test_MdEdit G " + id, 1);
		var matchday = new Matchday(placement, "Test_MdEdit PL " + id, 50);
		matchday.setGroup(group);
		matchdayRepository.save(matchday);

		// when
		matchdayService.saveMatchday("Test_MdEdit PL2 " + id, 51, season.getId(), matchday.getId());

		// then
		var stored = stored(matchday);
		assertThat(stored.getPhase().getId()).as("phase").isEqualTo(placement.getId());
		assertThat(stored.getGroup().getId()).as("group").isEqualTo(group.getId());
	}

	@Test
	void givenMatchdayOfAnotherSeason_whenSavedUnderThisSeason_thenRejectedAndUnchanged() {
		// given
		var other = testHelper.createSeason("Test_MdEdit_Other_" + id);
		var matchday = testHelper.createMatchdayInRegularPhase(other, "Test_MdEdit O " + id, 1);

		// when / then
		assertThatThrownBy(() -> matchdayService.saveMatchday("Test_MdEdit O2 " + id, 2, season.getId(), matchday.getId()))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessage("A matchday cannot move to another season");
		var stored = stored(matchday);
		assertThat(stored.getLabel()).as("label").isEqualTo("Test_MdEdit O " + id);
		assertThat(stored.getSeason().getId()).as("season").isEqualTo(other.getId());
	}

	private Matchday stored(Matchday matchday) {
		entityManager.flush();
		entityManager.clear();
		return matchdayRepository.findById(matchday.getId()).orElseThrow();
	}
}
