package org.ctc;

import lombok.RequiredArgsConstructor;
import org.ctc.domain.model.*;
import org.ctc.domain.repository.*;
import org.ctc.domain.service.PlayoffService;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TestHelper {

	private final RaceScoringRepository raceScoringRepository;
	private final MatchScoringRepository matchScoringRepository;
	private final SeasonRepository seasonRepository;
	private final SeasonPhaseRepository seasonPhaseRepository;
	private final MatchdayRepository matchdayRepository;
	private final TeamRepository teamRepository;
	private final MatchRepository matchRepository;
	private final RaceRepository raceRepository;
	private final DriverRepository driverRepository;
	private final SeasonDriverRepository seasonDriverRepository;
	private final PlayoffService playoffService;

	public Season createSeason(String name) {
		return createSeason(name, 2026, 1);
	}

	/**
	 * Creates a Season with a bootstrapped REGULAR phase (LEAGUE layout, sortIndex=0).
	 * Mirrors the bootstrap performed by SeasonManagementService.save — ensures tests that
	 * call seasonPhaseRepository.findBySeasonIdAndPhaseType(id, REGULAR) find a result.
	 *
	 * <p>Phase 61 MIGR-06: scoring lives on the REGULAR SeasonPhase, not on the Season itself.
	 */
	public Season createSeason(String name, int year, int number) {
		var suffix = UUID.randomUUID().toString().substring(0, 4);
		var rs = raceScoringRepository.save(
				new RaceScoring("RS " + suffix, "20,17,14,12,10,8,7,6,5,4,3,2", "3,2,1", 2));
		var ms = matchScoringRepository.save(
				new MatchScoring("MS " + suffix, 3, 1, 0));
		var season = new Season(name, year, number);
		var saved = seasonRepository.save(season);
		// Bootstrap REGULAR phase carrying scoring + format (Phase 61 MIGR-06).
		var regular = new SeasonPhase(saved, PhaseType.REGULAR, PhaseLayout.LEAGUE, 0);
		regular.setFormat(SeasonFormat.LEAGUE);
		regular.setLegs(1);
		regular = seasonPhaseRepository.save(regular);
		// Phase 61 MIGR-06: keep the Java-side season.phases collection in sync so that
		// downstream PhaseTestFixtures.matchdayInRegularPhase(saved, ...) can find it
		// without relying on lazy-load (some unit-style IT tests run outside an OSIV session).
		saved.getPhases().add(regular);
		return saved;
	}

	/**
	 * Creates a Matchday wired to the season's REGULAR phase. Replaces the legacy
	 * {@code createMatchday(season, ...)} factory post-MIGR-06.
	 */
	public Matchday createMatchdayInRegularPhase(Season season, String label, int sortIndex) {
		var regular = seasonPhaseRepository
				.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR)
				.orElseThrow(() -> new IllegalStateException("No REGULAR phase for season " + season.getId()));
		return matchdayRepository.save(new Matchday(regular, label, sortIndex));
	}

	/**
	 * Creates a Playoff for a season. Delegates to PlayoffService.createPlayoff which
	 * atomically writes a PLAYOFF SeasonPhase + Playoff (Phase 58 D-19).
	 */
	public Playoff createPlayoffInPhase(Season season, String name, int teamCount) {
		return playoffService.createPlayoff(season.getId(), name, teamCount);
	}

	/**
	 * @deprecated Phase 61 MIGR-06: prefer {@link #createMatchdayInRegularPhase(Season, String, int)}.
	 * Kept as a thin alias so existing callers keep compiling — internally now binds via the REGULAR phase.
	 */
	public Matchday createMatchday(Season season, String label, int sortIndex) {
		return createMatchdayInRegularPhase(season, label, sortIndex);
	}

	/**
	 * Phase 61 MIGR-06 helper: returns the RaceScoring assigned to the season's REGULAR phase.
	 * Replaces the legacy {@code season.getRaceScoring()} call in tests.
	 */
	public RaceScoring getRaceScoring(Season season) {
		return seasonPhaseRepository
				.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR)
				.orElseThrow(() -> new IllegalStateException("No REGULAR phase for season " + season.getId()))
				.getRaceScoring();
	}

	/**
	 * Phase 61 MIGR-06 helper: returns the MatchScoring assigned to the season's REGULAR phase.
	 * Replaces the legacy {@code season.getMatchScoring()} call in tests.
	 */
	public MatchScoring getMatchScoring(Season season) {
		return seasonPhaseRepository
				.findBySeasonIdAndPhaseType(season.getId(), PhaseType.REGULAR)
				.orElseThrow(() -> new IllegalStateException("No REGULAR phase for season " + season.getId()))
				.getMatchScoring();
	}

	public Team createTeam(String name, String shortName) {
		return teamRepository.save(new Team(name, shortName));
	}

	public Match createMatch(Matchday matchday, Team homeTeam, Team awayTeam) {
		return matchRepository.save(new Match(matchday, homeTeam, awayTeam));
	}

	public Race createRace(Matchday matchday, Match match) {
		var race = new Race();
		race.setMatchday(matchday);
		race.setMatch(match);
		return raceRepository.save(race);
	}

	public Driver createDriver(String psnId, String nickname) {
		return driverRepository.save(new Driver(psnId, nickname));
	}

	public SeasonDriver createSeasonDriver(Season season, Driver driver, Team team) {
		return seasonDriverRepository.save(new SeasonDriver(season, driver, team));
	}

	/**
	 * Creates a full setup: Season with two teams added, a matchday, a match, and a race.
	 * Returns the race for further use.
	 */
	public SeasonFixture createFullSeasonFixture(String prefix) {
		var season = createSeason(prefix + "_Season");
		var homeTeam = createTeam(prefix + " Home", prefix + "_HOM");
		var awayTeam = createTeam(prefix + " Away", prefix + "_AWY");
		season.addTeam(homeTeam);
		season.addTeam(awayTeam);
		season = seasonRepository.save(season);
		var matchday = createMatchday(season, prefix + " MD1", 1);
		var match = createMatch(matchday, homeTeam, awayTeam);
		var race = createRace(matchday, match);
		return new SeasonFixture(season, matchday, match, race, homeTeam, awayTeam);
	}

	public record SeasonFixture(Season season, Matchday matchday, Match match, Race race,
	                            Team homeTeam, Team awayTeam) {
	}
}
