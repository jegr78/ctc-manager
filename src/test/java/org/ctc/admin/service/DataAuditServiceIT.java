package org.ctc.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ctc.admin.dto.DataAuditFinding.Category.PAIRING;
import static org.ctc.admin.dto.DataAuditFinding.Category.PHASE_GROUP;
import static org.ctc.admin.dto.DataAuditFinding.Category.PLAYOFF;
import static org.ctc.admin.dto.DataAuditFinding.Category.PUBLIC_URL;
import static org.ctc.admin.dto.DataAuditFinding.Category.STALE_AGGREGATE;
import static org.ctc.admin.dto.DataAuditFinding.Category.SUCCESSION;
import static org.ctc.admin.dto.DataAuditFinding.Resolution.AMBIGUOUS;
import static org.ctc.admin.dto.DataAuditFinding.Resolution.RECONSTRUCTIBLE;
import static org.ctc.admin.dto.DataAuditFinding.Resolution.UNDETERMINABLE;

import jakarta.persistence.EntityManager;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.admin.dto.DataAuditFinding;
import org.ctc.admin.dto.DataAuditFinding.Category;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonFormat;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.SiteSlug;
import org.ctc.domain.model.SiteSlugKind;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonPhaseGroupRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SiteSlugRepository;
import org.ctc.domain.service.PlayoffService;
import org.ctc.domain.service.RaceService;
import org.ctc.domain.service.SeasonPhaseService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds synthetic inconsistent records directly through the repositories, past every service
 * check, and asserts that the audit reports each one with its resolution and changes nothing.
 */
@CtcDevSpringBootContext
@Tag("integration")
@Transactional
class DataAuditServiceIT {

	@Autowired private DataAuditService dataAuditService;
	@Autowired private TestHelper testHelper;
	@Autowired private RaceService raceService;
	@Autowired private PlayoffService playoffService;
	@Autowired private SeasonPhaseService seasonPhaseService;
	@Autowired private SeasonRepository seasonRepository;
	@Autowired private MatchRepository matchRepository;
	@Autowired private MatchdayRepository matchdayRepository;
	@Autowired private RaceRepository raceRepository;
	@Autowired private RaceLineupRepository raceLineupRepository;
	@Autowired private PhaseTeamRepository phaseTeamRepository;
	@Autowired private SeasonPhaseGroupRepository seasonPhaseGroupRepository;
	@Autowired private PlayoffMatchupRepository playoffMatchupRepository;
	@Autowired private SiteSlugRepository siteSlugRepository;
	@Autowired private EntityManager entityManager;
	@Autowired private JdbcTemplate jdbcTemplate;

	private final String id = UUID.randomUUID().toString().substring(0, 8);
	private Season season;
	private SeasonPhase regular;
	private Matchday matchday;
	private Team alpha;
	private Team bravo;
	private Team charlie;

	@BeforeEach
	void createSeasonWithRoster() {
		season = testHelper.createSeason("Test_Audit_" + id);
		alpha = testHelper.createTeam("Test Audit Alpha " + id, "Test_AUA_" + id);
		bravo = testHelper.createTeam("Test Audit Bravo " + id, "Test_AUB_" + id);
		charlie = testHelper.createTeam("Test Audit Charlie " + id, "Test_AUC_" + id);
		List.of(alpha, bravo, charlie).forEach(season::addTeam);
		season = seasonRepository.save(season);
		regular = seasonPhaseService.findRegularPhase(season.getId());
		List.of(alpha, bravo, charlie).forEach(team -> phaseTeamRepository.save(new PhaseTeam(regular, team)));
		matchday = testHelper.createMatchdayInRegularPhase(season, "Test_Audit MD " + id, 1);
	}

	@Test
	void givenMatchWhoseStoredTotalsDifferFromItsScoredLeg_whenAudited_thenReconstructibleWithBothValues() {
		// given
		Match match = scoredMatch(alpha, bravo);
		Match scored = matchRepository.findById(match.getId()).orElseThrow();
		String legs = scored.getHomeScore() + ":" + scored.getAwayScore();
		scored.setHomeScore(99);
		scored.setAwayScore(0);
		flushAndClear();

		// when
		var findings = findings(STALE_AGGREGATE, match.getId());

		// then
		assertThat(findings).as("stale match aggregate").singleElement().satisfies(finding -> {
			assertThat(finding.resolution()).as("resolution").isEqualTo(RECONSTRUCTIBLE);
			assertThat(finding.evidence()).as("evidence").isEqualTo("Stored 99:0, the scored legs give " + legs);
		});
	}

	@Test
	void givenMatchWithStoredTotalsButNoScoredLeg_whenAudited_thenAmbiguous() {
		// given
		Match match = testHelper.createMatch(matchday, alpha, bravo);
		testHelper.createRace(matchday, match);
		match.setHomeScore(30);
		match.setAwayScore(10);
		flushAndClear();

		// when
		var findings = findings(STALE_AGGREGATE, match.getId());

		// then
		assertThat(findings).as("quick-scored match").singleElement().satisfies(finding -> {
			assertThat(finding.resolution()).as("resolution").isEqualTo(AMBIGUOUS);
			assertThat(finding.evidence()).as("evidence").startsWith("Stored 30:10 without any scored leg");
		});
	}

	@Test
	void givenConsistentScoredMatch_whenAudited_thenNoAggregateFinding() {
		// given
		Match match = scoredMatch(alpha, bravo);

		// when
		var findings = findings(STALE_AGGREGATE, match.getId());

		// then
		assertThat(findings).as("findings for a consistent match").isEmpty();
	}

	@Test
	void givenReversedPairingOnOneMatchday_whenAudited_thenAmbiguousNamingBothMatches() {
		// given
		Match first = testHelper.createMatch(matchday, alpha, bravo);
		Match reversed = testHelper.createMatch(matchday, bravo, alpha);
		flushAndClear();

		// when
		var findings = findings(PAIRING, first.getId());

		// then
		assertThat(findings).as("reversed pairing").singleElement().satisfies(finding -> {
			assertThat(finding.resolution()).as("resolution").isEqualTo(AMBIGUOUS);
			assertThat(finding.evidence()).as("evidence").contains("2 matches for one pairing on this matchday, in both orientations")
					.contains(reversed.getId().toString());
		});
	}

	@Test
	void givenLegOverridesThatDoNotSwapTheMatchTeams_whenAudited_thenAmbiguous() {
		// given
		Match match = testHelper.createMatch(matchday, alpha, bravo);
		Race leg = testHelper.createRace(matchday, match);
		leg.setHomeTeamOverride(charlie);
		leg.setAwayTeamOverride(alpha);
		flushAndClear();

		// when
		var findings = findings(PAIRING, leg.getId());

		// then
		assertThat(findings).as("leg orientation").singleElement().satisfies(finding -> {
			assertThat(finding.resolution()).as("resolution").isEqualTo(AMBIGUOUS);
			assertThat(finding.evidence()).as("evidence").isEqualTo("Leg orientation %s vs %s, the match is %s vs %s"
					.formatted(charlie.getShortName(), alpha.getShortName(), alpha.getShortName(), bravo.getShortName()));
		});
	}

	@Test
	void givenSwappedLegOverrides_whenAudited_thenNoPairingFinding() {
		// given
		Match match = testHelper.createMatch(matchday, alpha, bravo);
		Race leg = testHelper.createRace(matchday, match);
		leg.setHomeTeamOverride(bravo);
		leg.setAwayTeamOverride(alpha);
		flushAndClear();

		// when
		var findings = findings(PAIRING, leg.getId());

		// then
		assertThat(findings).as("findings for a correctly swapped leg").isEmpty();
	}

	@Test
	void givenMatchOfATeamOffThePhaseRoster_whenAudited_thenAmbiguous() {
		// given
		Team outsider = testHelper.createTeam("Test Audit Outsider " + id, "Test_AUO_" + id);
		season.addTeam(outsider);
		seasonRepository.save(season);
		Match match = testHelper.createMatch(matchday, alpha, outsider);
		flushAndClear();

		// when
		var findings = findings(PHASE_GROUP, match.getId());

		// then
		assertThat(findings).as("match of a team off the roster").singleElement().satisfies(finding -> {
			assertThat(finding.resolution()).as("resolution").isEqualTo(AMBIGUOUS);
			assertThat(finding.evidence()).as("evidence").startsWith(outsider.getShortName() + " is not on the roster")
					.endsWith("so the standings count the match only for " + alpha.getShortName());
		});
	}

	@Test
	void givenImportedPlayoffLegLinkedToItsMatchAndMatchup_whenAudited_thenNoPhaseFinding() {
		// given
		PlayoffMatchup semi = bracket().get(0);
		Race leg = playoffLeg(semi, bravo, alpha);
		flushAndClear();

		// when
		var findings = dataAuditService.audit().findings(PHASE_GROUP).stream()
				.filter(finding -> finding.subject().contains("Test_Audit_" + id))
				.toList();

		// then
		assertThat(findings).as("phase findings of a consistently imported playoff leg %s", leg.getId()).isEmpty();
	}

	@Test
	void givenLegLinkedToAMatchOfOtherTeamsThanItsMatchup_whenAudited_thenAmbiguous() {
		// given
		PlayoffMatchup semi = bracket().get(0);
		Race leg = playoffLeg(semi, alpha, charlie);
		flushAndClear();

		// when
		var findings = findings(PHASE_GROUP, leg.getId());

		// then
		assertThat(findings).as("leg with disagreeing links").singleElement().satisfies(finding -> {
			assertThat(finding.resolution()).as("resolution").isEqualTo(AMBIGUOUS);
			assertThat(finding.evidence()).as("evidence").endsWith("which differ in phase or teams");
		});
	}

	@Test
	void givenMatchdayInAGroupOfAnotherPhase_whenAudited_thenAmbiguous() {
		// given
		SeasonPhase placement = seasonPhaseService.create(season.getId(), PhaseType.PLACEMENT, PhaseLayout.LEAGUE, 5,
				"Test_Audit Placement", regular.getRaceScoring(), regular.getMatchScoring(), SeasonFormat.LEAGUE,
				null, null, null, 1, null);
		SeasonPhaseGroup foreign = seasonPhaseGroupRepository.save(new SeasonPhaseGroup(placement, "Test_Audit G " + id, 1));
		Matchday grouped = matchdayRepository.save(new Matchday(regular, "Test_Audit Grouped " + id, 2));
		grouped.setGroup(foreign);
		flushAndClear();

		// when
		var findings = findings(PHASE_GROUP, "Test_Audit Grouped " + id);

		// then
		assertThat(findings).as("matchday group of another phase").singleElement().satisfies(finding -> {
			assertThat(finding.resolution()).as("resolution").isEqualTo(AMBIGUOUS);
			assertThat(finding.evidence()).as("evidence").startsWith("Group 'Test_Audit G " + id + "' belongs to phase 'Test_Audit Placement'");
		});
	}

	@Test
	void givenSuccessionCycle_whenAudited_thenAmbiguousNamingTheCycle() {
		// given
		SeasonTeam alphaEntry = seasonTeam(alpha);
		SeasonTeam bravoEntry = seasonTeam(bravo);
		alphaEntry.setSuccessor(bravoEntry);
		bravoEntry.setSuccessor(alphaEntry);
		flushAndClear();

		// when
		var findings = findings(SUCCESSION, "Test_Audit_" + id);

		// then
		assertThat(findings).as("succession cycle").filteredOn(finding -> finding.evidence().startsWith("Succession cycle"))
				.singleElement().satisfies(finding -> {
					assertThat(finding.resolution()).as("resolution").isEqualTo(AMBIGUOUS);
					assertThat(finding.evidence()).as("evidence").contains(alpha.getShortName(), bravo.getShortName());
				});
	}

	@Test
	void givenReplacedTeamWhoseChainRunsIntoACycle_whenAudited_thenOnlyTheCycleIsReported() {
		// given
		seasonTeam(alpha).setSuccessor(seasonTeam(bravo));
		seasonTeam(bravo).setSuccessor(seasonTeam(charlie));
		seasonTeam(charlie).setSuccessor(seasonTeam(bravo));
		flushAndClear();

		// when
		var findings = findings(SUCCESSION, "Test_Audit_" + id);

		// then
		assertThat(findings).as("cycle finding").filteredOn(finding -> finding.evidence().startsWith("Succession cycle"))
				.singleElement().extracting(DataAuditFinding::evidence).asString()
				.doesNotContain(alpha.getShortName());
		assertThat(findings).as("findings about the team whose chain runs into the cycle")
				.noneMatch(finding -> finding.subject().startsWith(alpha.getShortName() + " in season"));
	}

	@Test
	void givenReplacedTeamThatStillHoldsItsPlace_whenAudited_thenReconstructibleMoveToTheSuccessor() {
		// given
		Team successor = testHelper.createTeam("Test Audit Successor " + id, "Test_AUS_" + id);
		season.addTeam(successor);
		seasonRepository.save(season);
		seasonTeam(charlie).setSuccessor(seasonTeam(successor));
		Match earlier = testHelper.createMatch(matchday, alpha, charlie);
		flushAndClear();

		// when
		var findings = findings(SUCCESSION, charlie.getShortName() + " in season");
		var rosterFindings = findings(PHASE_GROUP, earlier.getId());

		// then
		assertThat(findings).as("place of a replaced team").singleElement().satisfies(finding -> {
			assertThat(finding.resolution()).as("resolution").isEqualTo(RECONSTRUCTIBLE);
			assertThat(finding.correction()).as("correction").isEqualTo("Move the place to the successor " + successor.getShortName());
		});
		assertThat(rosterFindings).as("roster findings already covered by the succession finding").isEmpty();
	}

	@Test
	void givenLegacyDecisionWithoutHistory_whenAudited_thenUndeterminableAndTheMissingAdvancementReconstructible() {
		// given
		List<PlayoffMatchup> bracket = bracket();
		PlayoffMatchup semi = bracket.get(0);
		semi.setWinner(alpha);
		semi.setHomeScore(30);
		semi.setAwayScore(20);
		flushAndClear();

		// when
		var findings = findings(PLAYOFF, semi.getId());

		// then
		assertThat(findings).as("legacy decision").extracting(DataAuditFinding::resolution)
				.containsExactlyInAnyOrder(UNDETERMINABLE, RECONSTRUCTIBLE);
		assertThat(findings).as("legacy decision evidence").filteredOn(finding -> finding.resolution() == UNDETERMINABLE)
				.singleElement().extracting(DataAuditFinding::evidence).asString()
				.contains("outcome type and reason are unknown");
	}

	@Test
	void givenRecordedWinnerThatTrailsWithoutReason_whenAudited_thenAmbiguous() {
		// given
		List<PlayoffMatchup> bracket = bracket();
		PlayoffMatchup semi = bracket.get(0);
		decide(semi, alpha, 10, 20);
		bracket.get(2).setTeam1(alpha);
		flushAndClear();

		// when
		var findings = findings(PLAYOFF, semi.getId());

		// then
		assertThat(findings).as("winner without a lead or reason").singleElement().satisfies(finding -> {
			assertThat(finding.resolution()).as("resolution").isEqualTo(AMBIGUOUS);
			assertThat(finding.evidence()).as("evidence")
					.isEqualTo("The winner %s does not lead 10:20 and no reason is recorded".formatted(alpha.getShortName()));
		});
	}

	@Test
	void givenNextSlotHoldingAnotherTeam_whenAudited_thenAmbiguous() {
		// given
		List<PlayoffMatchup> bracket = bracket();
		PlayoffMatchup semi = bracket.get(0);
		decide(semi, alpha, 30, 20);
		bracket.get(2).setTeam1(charlie);
		flushAndClear();

		// when
		var findings = findings(PLAYOFF, semi.getId());

		// then
		assertThat(findings).as("advancement of another team").singleElement().satisfies(finding -> {
			assertThat(finding.resolution()).as("resolution").isEqualTo(AMBIGUOUS);
			assertThat(finding.evidence()).as("evidence").startsWith(
					"The winner is %s, but %s holds the slot".formatted(alpha.getShortName(), charlie.getShortName()));
		});
	}

	@Test
	void givenSlotFilledBeforeItsFeederHasTeams_whenAudited_thenNoPlayoffFinding() {
		// given
		List<PlayoffMatchup> bracket = bracket();
		PlayoffMatchup otherSemi = bracket.get(1);
		otherSemi.setTeam1(null);
		otherSemi.setTeam2(null);
		bracket.get(2).setTeam2(charlie);
		flushAndClear();

		// when
		var findings = findings(PLAYOFF, otherSemi.getId());

		// then
		assertThat(findings).as("findings for a pre-seeded slot").isEmpty();
	}

	@Test
	void givenConsistentDecidedMatchup_whenAudited_thenNoPlayoffFinding() {
		// given
		List<PlayoffMatchup> bracket = bracket();
		PlayoffMatchup semi = bracket.get(0);
		decide(semi, alpha, 30, 20);
		bracket.get(2).setTeam1(alpha);
		flushAndClear();

		// when
		var findings = findings(PLAYOFF, semi.getId());

		// then
		assertThat(findings).as("findings for a consistent decided matchup").isEmpty();
	}

	@Test
	void givenReservedSharedSlug_whenAudited_thenAmbiguous() {
		// given
		String slug = "test-audit-shared-" + id;
		siteSlugRepository.save(new SiteSlug(SiteSlugKind.TEAM, slug, slug, null));
		flushAndClear();

		// when
		var findings = findings(PUBLIC_URL, "'" + slug + "'");

		// then
		assertThat(findings).as("reserved shared slug").singleElement().satisfies(finding -> {
			assertThat(finding.resolution()).as("resolution").isEqualTo(AMBIGUOUS);
			assertThat(finding.evidence()).as("evidence").startsWith("Several profiles share this base slug");
		});
	}

	@Test
	void givenTwoTeamsWithoutStoredSlugOnOneBaseSlug_whenAudited_thenReconstructible() {
		// given
		testHelper.createTeam("Test Audit Slug One " + id, "Test_Slug_" + id);
		testHelper.createTeam("Test Audit Slug Two " + id, "Test-Slug-" + id);
		flushAndClear();

		// when
		var findings = findings(PUBLIC_URL, "'test-slug-" + id + "'");

		// then
		assertThat(findings).as("colliding unstored slugs").singleElement().satisfies(finding -> {
			assertThat(finding.resolution()).as("resolution").isEqualTo(RECONSTRUCTIBLE);
			assertThat(finding.evidence()).as("evidence").contains("Test-Slug-" + id, "Test_Slug_" + id);
		});
	}

	@Test
	void givenTeamWhoseNameGivesNoSlug_whenAudited_thenAmbiguous() {
		// given
		String name = id.chars().mapToObj(c -> String.valueOf((char) ('α' + Character.digit(c, 16))))
				.reduce("", String::concat);
		testHelper.createTeam("Test Audit No Slug " + id, name);
		flushAndClear();

		// when
		var findings = findings(PUBLIC_URL, "team profiles without a usable URL");

		// then
		assertThat(findings).as("profiles without a usable URL").singleElement().satisfies(finding -> {
			assertThat(finding.resolution()).as("resolution").isEqualTo(AMBIGUOUS);
			assertThat(finding.evidence()).as("evidence").contains(name);
		});
	}

	@Test
	void givenInconsistentRecords_whenAudited_thenNothingIsChangedOrWritten() {
		// given
		Match stale = scoredMatch(alpha, bravo);
		matchRepository.findById(stale.getId()).orElseThrow().setHomeScore(99);
		testHelper.createMatch(matchday, bravo, alpha);
		List<PlayoffMatchup> bracket = bracket();
		bracket.get(0).setWinner(alpha);
		testHelper.createTeam("Test Audit Slug Three " + id, "Test_Slug3_" + id);
		testHelper.createTeam("Test Audit Slug Four " + id, "Test-Slug3-" + id);
		flushAndClear();
		var before = snapshot();

		// when
		var report = dataAuditService.audit();

		// then
		assertThat(report.total()).as("findings of the inconsistent records").isPositive();
		assertThat(entityManager.unwrap(Session.class).isDirty()).as("pending changes after the audit").isFalse();
		entityManager.flush();
		assertThat(snapshot()).as("stored rows after the audit").isEqualTo(before);
	}

	private Map<String, List<Map<String, Object>>> snapshot() {
		return Map.of(
				"matches", jdbcTemplate.queryForList("SELECT id, home_team_id, away_team_id, home_score, away_score, matchday_id FROM matches ORDER BY id"),
				"races", jdbcTemplate.queryForList("SELECT id, matchday_id, match_id, home_team_id, away_team_id FROM races ORDER BY id"),
				"playoff_matchups", jdbcTemplate.queryForList("SELECT id, team1_id, team2_id, winner_id, home_score, away_score, decision_history FROM playoff_matchups ORDER BY id"),
				"season_teams", jdbcTemplate.queryForList("SELECT id, successor_season_team_id FROM season_teams ORDER BY id"),
				"phase_teams", jdbcTemplate.queryForList("SELECT id, phase_id, team_id, group_id FROM phase_teams ORDER BY id"),
				"site_slugs", jdbcTemplate.queryForList("SELECT id, kind, slug, entity_id FROM site_slugs ORDER BY id"));
	}

	private List<DataAuditFinding> findings(Category category, Object subjectPart) {
		return dataAuditService.audit().findings(category).stream()
				.filter(finding -> finding.subject().contains(subjectPart.toString()))
				.toList();
	}

	private Match scoredMatch(Team home, Team away) {
		Match match = testHelper.createMatch(matchday, home, away);
		Race leg = testHelper.createRace(matchday, match);
		Driver homeDriver = testHelper.createDriver("Test_Audit_" + id + "_" + home.getShortName(), "Test Audit Home Driver");
		Driver awayDriver = testHelper.createDriver("Test_Audit_" + id + "_" + away.getShortName(), "Test Audit Away Driver");
		testHelper.createSeasonDriver(season, homeDriver, home);
		testHelper.createSeasonDriver(season, awayDriver, away);
		raceLineupRepository.save(new RaceLineup(leg, homeDriver, home));
		raceLineupRepository.save(new RaceLineup(leg, awayDriver, away));
		flushAndClear();
		raceService.saveResults(leg.getId(), List.of(
				new RaceService.RaceResultData(homeDriver.getId(), homeDriver.getPsnId(), null, 1, 1, true),
				new RaceService.RaceResultData(awayDriver.getId(), awayDriver.getPsnId(), null, 2, 2, false)));
		flushAndClear();
		return match;
	}

	private Race playoffLeg(PlayoffMatchup matchup, Team home, Team away) {
		Matchday playoffDay = matchdayRepository.save(new Matchday(matchup.getRound().getPlayoff().getPhase(),
				"Test_Audit PO " + id, 10));
		Match match = testHelper.createMatch(playoffDay, home, away);
		Race leg = testHelper.createRace(playoffDay, match);
		leg.setPlayoffMatchup(matchup);
		return leg;
	}

	private List<PlayoffMatchup> bracket() {
		Team delta = testHelper.createTeam("Test Audit Delta " + id, "Test_AUD_" + id);
		season.addTeam(delta);
		seasonRepository.save(season);
		var playoff = playoffService.createPlayoff(season.getId(), "Test_Audit Playoff " + id, 4);
		var matchups = playoffMatchupRepository.findByRoundPlayoffId(playoff.getId()).stream()
				.sorted(Comparator.comparing((PlayoffMatchup m) -> m.getRound().getRoundIndex())
						.thenComparing(PlayoffMatchup::getBracketPosition))
				.toList();
		matchups.get(0).setTeam1(alpha);
		matchups.get(0).setTeam2(bravo);
		matchups.get(1).setTeam1(charlie);
		matchups.get(1).setTeam2(delta);
		return matchups;
	}

	private static void decide(PlayoffMatchup matchup, Team winner, int team1Total, int team2Total) {
		matchup.setWinner(winner);
		matchup.setHomeScore(team1Total);
		matchup.setAwayScore(team2Total);
		matchup.setDecisionHistory("2026-09-01T00:00:00Z DECIDED " + winner.getShortName());
	}

	private SeasonTeam seasonTeam(Team team) {
		return seasonRepository.findById(season.getId()).orElseThrow().getSeasonTeams().stream()
				.filter(entry -> entry.getTeam().getId().equals(team.getId()))
				.findFirst().orElseThrow();
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}
}
