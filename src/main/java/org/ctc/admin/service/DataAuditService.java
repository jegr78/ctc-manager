package org.ctc.admin.service;

import static org.ctc.admin.dto.DataAuditFinding.Category.PAIRING;
import static org.ctc.admin.dto.DataAuditFinding.Category.PHASE_GROUP;
import static org.ctc.admin.dto.DataAuditFinding.Category.PLAYOFF;
import static org.ctc.admin.dto.DataAuditFinding.Category.PUBLIC_URL;
import static org.ctc.admin.dto.DataAuditFinding.Category.STALE_AGGREGATE;
import static org.ctc.admin.dto.DataAuditFinding.Category.SUCCESSION;
import static org.ctc.admin.dto.DataAuditFinding.Resolution.AMBIGUOUS;
import static org.ctc.admin.dto.DataAuditFinding.Resolution.RECONSTRUCTIBLE;
import static org.ctc.admin.dto.DataAuditFinding.Resolution.UNDETERMINABLE;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.DataAuditFinding;
import org.ctc.admin.dto.DataAuditFinding.Category;
import org.ctc.admin.dto.DataAuditReport;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PhaseTeam;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.PhaseType;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.SiteSlug;
import org.ctc.domain.model.SiteSlugKind;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.DriverRepository;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.PhaseTeamRepository;
import org.ctc.domain.repository.PlayoffMatchupRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.ctc.domain.repository.SiteSlugRepository;
import org.ctc.domain.repository.TeamRepository;
import org.ctc.domain.service.ScoringService;
import org.ctc.sitegen.SiteSlugger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inspects the persisted league data for historical anomalies and reports them with their evidence.
 * It never writes: every finding only proposes a correction for a later, reviewed action.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataAuditService {

	private final MatchRepository matchRepository;
	private final PlayoffMatchupRepository playoffMatchupRepository;
	private final RaceRepository raceRepository;
	private final RaceLineupRepository raceLineupRepository;
	private final MatchdayRepository matchdayRepository;
	private final PhaseTeamRepository phaseTeamRepository;
	private final SeasonTeamRepository seasonTeamRepository;
	private final TeamRepository teamRepository;
	private final DriverRepository driverRepository;
	private final SiteSlugRepository siteSlugRepository;
	private final ScoringService scoringService;
	private final SiteSlugger siteSlugger;

	@Transactional(readOnly = true)
	public DataAuditReport audit() {
		Map<Category, List<DataAuditFinding>> findings = new EnumMap<>(Category.class);
		for (Category category : Category.values()) {
			findings.put(category, new ArrayList<>());
		}
		List<Match> matches = matchRepository.findAllForBackup();
		List<PhaseTeam> phaseTeams = phaseTeamRepository.findAllForBackup();
		List<PlayoffMatchup> matchups = playoffMatchupRepository.findAllForBackup();
		List<Race> races = raceRepository.findAllForBackup();
		Legs legs = new Legs(races, raceLineupRepository.findAllForBackup());

		auditMatchAggregates(matches, legs, findings.get(STALE_AGGREGATE));
		auditPlayoffAggregates(matchups, legs, findings.get(STALE_AGGREGATE));
		auditPairings(matches, findings.get(PAIRING));
		auditLegs(races, findings);
		auditPhaseGroups(matches, phaseTeams, findings.get(PHASE_GROUP));
		auditSuccession(seasonTeamRepository.findAllForBackup(), phaseTeams, findings.get(SUCCESSION));
		auditPlayoffs(matchups, legs, findings.get(PLAYOFF));
		auditPublicUrls(findings.get(PUBLIC_URL));

		List<DataAuditReport.Section> sections = findings.entrySet().stream()
				.map(entry -> new DataAuditReport.Section(entry.getKey(), List.copyOf(entry.getValue())))
				.toList();
		DataAuditReport report = new DataAuditReport(Instant.now().truncatedTo(ChronoUnit.SECONDS), sections);
		log.info("Data audit found {} anomalies", report.total());
		return report;
	}

	/** Every leg grouped by its match and its playoff matchup, with the lineups of every leg. */
	private record Legs(Map<UUID, List<Race>> byMatch, Map<UUID, List<Race>> byMatchup,
			Map<UUID, Map<UUID, RaceLineup>> lineups) {

		Legs(List<Race> races, List<RaceLineup> lineups) {
			this(new HashMap<>(), new HashMap<>(), new HashMap<>());
			for (Race race : races) {
				if (race.getMatch() != null) {
					byMatch.computeIfAbsent(race.getMatch().getId(), ignored -> new ArrayList<>()).add(race);
				}
				if (race.getPlayoffMatchup() != null) {
					byMatchup.computeIfAbsent(race.getPlayoffMatchup().getId(), ignored -> new ArrayList<>()).add(race);
				}
			}
			for (RaceLineup lineup : lineups) {
				this.lineups.computeIfAbsent(lineup.getRace().getId(), ignored -> new HashMap<>())
						.put(lineup.getDriver().getId(), lineup);
			}
		}

		List<Race> of(Match match) {
			return byMatch.getOrDefault(match.getId(), List.of());
		}

		List<Race> of(PlayoffMatchup matchup) {
			return byMatchup.getOrDefault(matchup.getId(), List.of());
		}
	}

	private void auditMatchAggregates(List<Match> matches, Legs legs, List<DataAuditFinding> out) {
		for (Match match : matches) {
			if (match.isBye() || match.getWalkoverTeam() != null || match.getHomeTeam() == null) {
				continue;
			}
			int[] totals = scoringService.legTotals(legs.of(match), match.getHomeTeam().getId(), legs.lineups());
			auditAggregate(describe(match), totals, match.getHomeScore(), match.getAwayScore(), false, out);
		}
	}

	private void auditPlayoffAggregates(List<PlayoffMatchup> matchups, Legs legs, List<DataAuditFinding> out) {
		for (PlayoffMatchup matchup : matchups) {
			if (matchup.isBye() || matchup.getWalkoverTeam() != null || matchup.getTeam1() == null) {
				continue;
			}
			int[] totals = scoringService.legTotals(legs.of(matchup), matchup.getTeam1().getId(), legs.lineups());
			auditAggregate(describe(matchup), totals, matchup.getHomeScore(), matchup.getAwayScore(),
					matchup.getWinner() != null, out);
		}
	}

	private static void auditAggregate(String subject, int[] legs, Integer home, Integer away, boolean decided,
			List<DataAuditFinding> out) {
		if (legs != null && (!Objects.equals(home, legs[0]) || !Objects.equals(away, legs[1]))) {
			String evidence = "Stored %s, the scored legs give %d:%d".formatted(score(home, away), legs[0], legs[1]);
			if (decided) {
				out.add(new DataAuditFinding(STALE_AGGREGATE, AMBIGUOUS, subject, evidence + "; the matchup is decided",
						"Decide whether to reopen the matchup; recomputing it may change the winner"));
			} else {
				out.add(new DataAuditFinding(STALE_AGGREGATE, RECONSTRUCTIBLE, subject, evidence,
						"Recompute the stored totals from the legs"));
			}
		} else if (legs == null && (home != null || away != null)) {
			out.add(new DataAuditFinding(STALE_AGGREGATE, AMBIGUOUS, subject,
					"Stored %s without any scored leg (quick score or legacy result)".formatted(score(home, away)),
					"Confirm the result and enter its race results, or clear the stored totals"));
		}
	}

	private void auditPairings(List<Match> matches, List<DataAuditFinding> out) {
		Map<String, List<Match>> byPairing = new LinkedHashMap<>();
		for (Match match : matches) {
			if (match.getHomeTeam() == null || match.getAwayTeam() == null) {
				continue;
			}
			String key = match.getMatchday().getId() + "|"
					+ pairKey(match.getHomeTeam().getId(), match.getAwayTeam().getId());
			byPairing.computeIfAbsent(key, ignored -> new ArrayList<>()).add(match);
		}
		for (List<Match> pairing : byPairing.values()) {
			if (pairing.size() < 2) {
				continue;
			}
			Match first = pairing.getFirst();
			boolean reversed = pairing.stream()
					.anyMatch(match -> !match.getHomeTeam().getId().equals(first.getHomeTeam().getId()));
			out.add(new DataAuditFinding(PAIRING, AMBIGUOUS, describe(first),
					"%d matches for one pairing on this matchday%s: %s".formatted(pairing.size(),
							reversed ? ", in both orientations" : "", ids(pairing, Match::getId)),
					"Decide which match keeps the legs and results, and merge the others into it"));
		}
	}

	private static void auditLegs(List<Race> races, Map<Category, List<DataAuditFinding>> out) {
		for (Race race : races) {
			Match match = race.getMatch();
			if (match != null && race.getPlayoffMatchup() != null && !sameContest(match, race.getPlayoffMatchup())) {
				out.get(PHASE_GROUP).add(new DataAuditFinding(PHASE_GROUP, AMBIGUOUS, describe(race),
						"The leg belongs to match %s and to playoff matchup %s, which differ in phase or teams"
								.formatted(match.getId(), race.getPlayoffMatchup().getId()),
						"Decide which of the two the leg belongs to"));
			}
			if (match != null && !match.getMatchday().getId().equals(race.getMatchday().getId())) {
				out.get(PHASE_GROUP).add(new DataAuditFinding(PHASE_GROUP, RECONSTRUCTIBLE, describe(race),
						"The leg is on matchday '%s', its match on '%s'".formatted(race.getMatchday().getLabel(),
								match.getMatchday().getLabel()),
						"Move the leg to the matchday of its match"));
			}
			if (race.getPlayoffMatchup() != null && !race.getMatchday().getPhase().getId()
					.equals(race.getPlayoffMatchup().getRound().getPlayoff().getPhase().getId())) {
				out.get(PHASE_GROUP).add(new DataAuditFinding(PHASE_GROUP, AMBIGUOUS, describe(race),
						"The playoff leg is on matchday '%s' of phase %s, not in the playoff's phase".formatted(
								race.getMatchday().getLabel(), phaseName(race.getMatchday().getPhase())),
						"Decide which matchday of the playoff phase the leg belongs to"));
			}
			if (match != null && race.hasTeamOverrides() && !overridesSwapMatchTeams(race, match)) {
				out.get(PAIRING).add(new DataAuditFinding(PAIRING, AMBIGUOUS, describe(race),
						"Leg orientation %s vs %s, the match is %s vs %s".formatted(team(race.getHomeTeam()),
								team(race.getAwayTeam()), team(match.getHomeTeam()), team(match.getAwayTeam())),
						"Decide the leg's orientation; an overridden leg names the match teams swapped"));
			}
		}
	}

	/** An imported playoff leg carries both links; they agree when the match mirrors the matchup. */
	private static boolean sameContest(Match match, PlayoffMatchup matchup) {
		boolean samePhase = match.getMatchday().getPhase().getId()
				.equals(matchup.getRound().getPlayoff().getPhase().getId());
		boolean sameTeams = sameTeam(match.getHomeTeam(), matchup.getTeam1()) && sameTeam(match.getAwayTeam(), matchup.getTeam2())
				|| sameTeam(match.getHomeTeam(), matchup.getTeam2()) && sameTeam(match.getAwayTeam(), matchup.getTeam1());
		return samePhase && sameTeams;
	}

	private static boolean overridesSwapMatchTeams(Race race, Match match) {
		return sameTeam(race.getHomeTeam(), match.getAwayTeam())
				&& sameTeam(race.getAwayTeam(), match.getHomeTeam());
	}

	private void auditPhaseGroups(List<Match> matches, List<PhaseTeam> phaseTeams, List<DataAuditFinding> out) {
		for (Matchday matchday : matchdayRepository.findAllForBackup()) {
			if (matchday.getGroup() != null && !matchday.getGroup().getPhase().getId().equals(matchday.getPhase().getId())) {
				out.add(new DataAuditFinding(PHASE_GROUP, AMBIGUOUS, describe(matchday),
						"Group '%s' belongs to phase %s, the matchday to phase %s".formatted(matchday.getGroup().getName(),
								phaseName(matchday.getGroup().getPhase()), phaseName(matchday.getPhase())),
						"Assign the matchday to a group of its own phase"));
			}
		}
		Map<UUID, Map<UUID, PhaseTeam>> rosters = new HashMap<>();
		for (PhaseTeam phaseTeam : phaseTeams) {
			rosters.computeIfAbsent(phaseTeam.getPhase().getId(), ignored -> new HashMap<>())
					.put(phaseTeam.getTeam().getId(), phaseTeam);
			String subject = "Roster place of %s in %s".formatted(team(phaseTeam.getTeam()), phaseName(phaseTeam.getPhase()));
			if (phaseTeam.getGroup() != null && !phaseTeam.getGroup().getPhase().getId().equals(phaseTeam.getPhase().getId())) {
				out.add(new DataAuditFinding(PHASE_GROUP, AMBIGUOUS, subject,
						"Group '%s' belongs to phase %s".formatted(phaseTeam.getGroup().getName(),
								phaseName(phaseTeam.getGroup().getPhase())),
						"Assign the place to a group of its own phase"));
			}
			boolean inSeason = phaseTeam.getPhase().getSeason().getSeasonTeams().stream()
					.anyMatch(seasonTeam -> seasonTeam.getTeam().getId().equals(phaseTeam.getTeam().getId()));
			if (!inSeason) {
				out.add(new DataAuditFinding(PHASE_GROUP, AMBIGUOUS, subject,
						"The team does not take part in season '%s'".formatted(phaseTeam.getPhase().getSeason().getName()),
						"Add the team to the season or remove its roster place"));
			}
		}
		Set<UUID> phasesWithoutRoster = new HashSet<>();
		Map<UUID, Map<UUID, UUID>> successions = new HashMap<>();
		for (Match match : matches) {
			SeasonPhase phase = match.getMatchday().getPhase();
			if (phase.getPhaseType() == PhaseType.PLAYOFF) {
				continue;
			}
			Map<UUID, PhaseTeam> roster = rosters.get(phase.getId());
			if (roster == null) {
				if (phasesWithoutRoster.add(phase.getId())) {
					out.add(new DataAuditFinding(PHASE_GROUP, AMBIGUOUS, "Phase " + phaseName(phase),
							"The phase has matches but no team roster, so its standings are empty",
							"Decide which teams belong to the phase"));
				}
				continue;
			}
			Map<UUID, UUID> succession = successions.computeIfAbsent(phase.getSeason().getId(),
					ignored -> phase.getSeason().buildSuccessionMap());
			for (Team team : new Team[] {match.getHomeTeam(), match.getAwayTeam()}) {
				if (team == null) {
					continue;
				}
				UUID counted = succession.getOrDefault(team.getId(), team.getId());
				PhaseTeam place = roster.get(counted);
				if (place == null && !counted.equals(team.getId()) && roster.containsKey(team.getId())) {
					continue;
				}
				if (place == null) {
					String effect = team == match.getHomeTeam() ? "so the standings skip the match"
							: "so the standings count the match only for " + team(match.getHomeTeam());
					out.add(new DataAuditFinding(PHASE_GROUP, AMBIGUOUS, describe(match),
							"%s is not on the roster of phase %s, %s".formatted(team(team), phaseName(phase), effect),
							"Add the team to the phase roster, or move the match to the phase it belongs to"));
				} else if (match.getMatchday().getGroup() != null && (place.getGroup() == null
						|| !place.getGroup().getId().equals(match.getMatchday().getGroup().getId()))) {
					out.add(new DataAuditFinding(PHASE_GROUP, AMBIGUOUS, describe(match),
							"%s plays in group '%s', the matchday is in group '%s'".formatted(team(team),
									place.getGroup() == null ? "none" : place.getGroup().getName(),
									match.getMatchday().getGroup().getName()),
							"Decide the team's group, or move the match to a matchday of that group"));
				}
			}
		}
	}

	private static void auditSuccession(List<SeasonTeam> seasonTeams, List<PhaseTeam> phaseTeams,
			List<DataAuditFinding> out) {
		Map<UUID, SeasonTeam> byId = seasonTeams.stream().collect(Collectors.toMap(SeasonTeam::getId, Function.identity()));
		Set<UUID> inCycle = new HashSet<>();
		Set<UUID> reachesCycle = new HashSet<>();
		for (SeasonTeam start : seasonTeams) {
			List<UUID> path = new ArrayList<>();
			SeasonTeam current = start;
			while (current != null && !path.contains(current.getId()) && !inCycle.contains(current.getId())
					&& !reachesCycle.contains(current.getId())) {
				path.add(current.getId());
				current = current.getSuccessor() == null ? null : byId.get(current.getSuccessor().getId());
			}
			if (current != null) {
				reachesCycle.addAll(path);
			}
			if (current != null && path.contains(current.getId())) {
				List<UUID> cycle = path.subList(path.indexOf(current.getId()), path.size());
				inCycle.addAll(cycle);
				out.add(new DataAuditFinding(SUCCESSION, AMBIGUOUS, describe(current),
						"Succession cycle: " + cycle.stream().map(id -> team(byId.get(id).getTeam()))
								.collect(Collectors.joining(" → ")) + " → " + team(current.getTeam()),
						"Decide which team of the cycle is the active one"));
			}
		}
		Map<UUID, List<SeasonTeam>> predecessors = new LinkedHashMap<>();
		for (SeasonTeam seasonTeam : seasonTeams) {
			SeasonTeam successor = seasonTeam.getSuccessor();
			if (successor == null) {
				continue;
			}
			predecessors.computeIfAbsent(successor.getId(), ignored -> new ArrayList<>()).add(seasonTeam);
			if (!successor.getSeason().getId().equals(seasonTeam.getSeason().getId())) {
				out.add(new DataAuditFinding(SUCCESSION, AMBIGUOUS, describe(seasonTeam),
						"The successor %s belongs to season '%s'".formatted(team(successor.getTeam()),
								successor.getSeason().getName()),
						"Decide the successor within the same season"));
			}
			if (!reachesCycle.contains(seasonTeam.getId())) {
				auditReplacedPlaces(seasonTeam, phaseTeams, out);
			}
		}
		predecessors.forEach((successorId, replaced) -> {
			if (replaced.size() > 1) {
				out.add(new DataAuditFinding(SUCCESSION, AMBIGUOUS, describe(byId.get(successorId)),
						"%d teams are replaced by this team: %s".formatted(replaced.size(),
								replaced.stream().map(seasonTeam -> team(seasonTeam.getTeam())).collect(Collectors.joining(", "))),
						"Decide which predecessor the team replaces"));
			}
		});
	}

	private static void auditReplacedPlaces(SeasonTeam replaced, List<PhaseTeam> phaseTeams, List<DataAuditFinding> out) {
		Team active = replaced.getActiveSeasonTeam().getTeam();
		for (PhaseTeam place : phaseTeams) {
			if (!place.getTeam().getId().equals(replaced.getTeam().getId())
					|| !place.getPhase().getSeason().getId().equals(replaced.getSeason().getId())) {
				continue;
			}
			boolean activeHoldsPlace = phaseTeams.stream().anyMatch(other -> other.getPhase().getId()
					.equals(place.getPhase().getId()) && other.getTeam().getId().equals(active.getId()));
			if (activeHoldsPlace) {
				out.add(new DataAuditFinding(SUCCESSION, AMBIGUOUS, describe(replaced),
						"Both the replaced team and its successor %s hold a place in phase %s".formatted(team(active),
								phaseName(place.getPhase())),
						"Decide which place stays; the replaced team's matches count for the successor"));
			} else {
				out.add(new DataAuditFinding(SUCCESSION, RECONSTRUCTIBLE, describe(replaced),
						"The replaced team still holds the place in phase %s".formatted(phaseName(place.getPhase())),
						"Move the place to the successor " + team(active)));
			}
		}
	}

	private static void auditPlayoffs(List<PlayoffMatchup> matchups, Legs legs, List<DataAuditFinding> out) {
		Map<String, List<PlayoffMatchup>> byPosition = new LinkedHashMap<>();
		for (PlayoffMatchup matchup : matchups) {
			byPosition.computeIfAbsent(matchup.getRound().getId() + "|" + matchup.getBracketPosition(),
					ignored -> new ArrayList<>()).add(matchup);
			auditDecision(matchup, legs, out);
			auditAdvancement(matchup, out);
		}
		byPosition.values().stream().filter(same -> same.size() > 1).forEach(same -> out.add(new DataAuditFinding(
				PLAYOFF, AMBIGUOUS, describe(same.getFirst()),
				"%d matchups share this bracket position: %s".formatted(same.size(), ids(same, PlayoffMatchup::getId)),
				"Decide which matchup holds the position")));
	}

	private static void auditDecision(PlayoffMatchup matchup, Legs legs, List<DataAuditFinding> out) {
		Team winner = matchup.getWinner();
		String subject = describe(matchup);
		if (matchup.isBye() && (matchup.getTeam1() != null && matchup.getTeam2() != null || !legs.of(matchup).isEmpty())) {
			out.add(new DataAuditFinding(PLAYOFF, AMBIGUOUS, subject,
					"Marked as a bye but has two teams or scheduled legs",
					"Decide whether the matchup is a bye or a played matchup"));
		}
		if (matchup.getWalkoverTeam() != null && !isParticipant(matchup, matchup.getWalkoverTeam())) {
			out.add(new DataAuditFinding(PLAYOFF, AMBIGUOUS, subject,
					"The forfeiting team %s is not a participant".formatted(team(matchup.getWalkoverTeam())),
					"Decide which participant forfeited"));
		}
		if (winner == null) {
			return;
		}
		if (!isParticipant(matchup, winner)) {
			out.add(new DataAuditFinding(PLAYOFF, AMBIGUOUS, subject,
					"The winner %s is neither %s nor %s".formatted(team(winner), team(matchup.getTeam1()),
							team(matchup.getTeam2())),
					"Decide the winner among the participants"));
			return;
		}
		if (matchup.getDecisionHistory() == null) {
			out.add(new DataAuditFinding(PLAYOFF, UNDETERMINABLE, subject,
					"Decided for %s at %s before decisions were recorded; outcome type and reason are unknown".formatted(
							team(winner), score(matchup.getHomeScore(), matchup.getAwayScore())),
					"Keep the recorded winner; add a reason only from league records"));
			return;
		}
		boolean regular = !matchup.isBye() && matchup.getWalkoverTeam() == null;
		boolean noReason = matchup.getDecisionReason() == null || matchup.getDecisionReason().isBlank();
		if (regular && noReason && matchup.getHomeScore() != null && matchup.getAwayScore() != null
				&& !sameTeam(winner, leader(matchup))) {
			out.add(new DataAuditFinding(PLAYOFF, AMBIGUOUS, subject,
					"The winner %s does not lead %s and no reason is recorded".formatted(team(winner),
							score(matchup.getHomeScore(), matchup.getAwayScore())),
					"Record the reason for the decision, or reopen the matchup"));
		}
	}

	private static void auditAdvancement(PlayoffMatchup matchup, List<DataAuditFinding> out) {
		PlayoffMatchup next = matchup.getNextMatchup();
		if (next == null) {
			return;
		}
		String subject = describe(matchup);
		if (next.getRound().getRoundIndex() != matchup.getRound().getRoundIndex() + 1) {
			out.add(new DataAuditFinding(PLAYOFF, AMBIGUOUS, subject,
					"It feeds %s, which is not in the following round".formatted(describe(next)),
					"Decide which matchup of the following round it feeds"));
		}
		Team slot = matchup.getBracketPosition() % 2 == 0 ? next.getTeam1() : next.getTeam2();
		Team winner = matchup.getWinner();
		if (winner != null && slot == null) {
			out.add(new DataAuditFinding(PLAYOFF, RECONSTRUCTIBLE, subject,
					"The winner %s has not advanced to %s".formatted(team(winner), describe(next)),
					"Place %s in the slot of %s that this matchup feeds".formatted(team(winner), describe(next))));
		} else if (winner != null && !sameTeam(slot, winner)) {
			out.add(new DataAuditFinding(PLAYOFF, AMBIGUOUS, subject,
					"The winner is %s, but %s holds the slot of %s that this matchup feeds".formatted(team(winner),
							team(slot), describe(next)),
					"Decide which team advances"));
		} else if (winner == null && slot != null && (matchup.getTeam1() != null || matchup.getTeam2() != null)) {
			out.add(new DataAuditFinding(PLAYOFF, AMBIGUOUS, subject,
					"%s holds the slot of %s that this undecided matchup feeds".formatted(team(slot), describe(next)),
					"Decide this matchup first, or clear the slot"));
		}
		if (winner == null && next.getWinner() != null) {
			out.add(new DataAuditFinding(PLAYOFF, AMBIGUOUS, subject,
					"The following matchup %s is decided while this one is not".formatted(describe(next)),
					"Decide this matchup, or reopen the following one"));
		}
	}

	private void auditPublicUrls(List<DataAuditFinding> out) {
		Map<UUID, String> teams = new LinkedHashMap<>();
		teamRepository.findAll().forEach(team -> teams.put(team.getId(), team.getShortName()));
		Map<UUID, String> drivers = new LinkedHashMap<>();
		driverRepository.findAll().forEach(driver -> drivers.put(driver.getId(), driver.getPsnId()));
		auditPublicUrls(SiteSlugKind.TEAM, teams, out);
		auditPublicUrls(SiteSlugKind.DRIVER, drivers, out);
	}

	private void auditPublicUrls(SiteSlugKind kind, Map<UUID, String> profiles, List<DataAuditFinding> out) {
		List<SiteSlug> stored = siteSlugRepository.findByKind(kind);
		Set<String> taken = new HashSet<>();
		Set<UUID> withSlug = new HashSet<>();
		for (SiteSlug slug : stored) {
			taken.add(slug.getSlug());
			String subject = "%s URL '%s'".formatted(kind.name().toLowerCase(), slug.getSlug());
			if (slug.getEntityId() == null) {
				out.add(new DataAuditFinding(PUBLIC_URL, AMBIGUOUS, subject,
						"Several profiles share this base slug, so the URL lists them instead of leading to one profile",
						"Keep the listing page, or decide which profile the old URL leads to"));
			} else if (!profiles.containsKey(slug.getEntityId())) {
				out.add(new DataAuditFinding(PUBLIC_URL, AMBIGUOUS, subject,
						"The slug belongs to the deleted profile " + slug.getEntityId(),
						"Decide whether the URL stays reserved or is released"));
			} else {
				withSlug.add(slug.getEntityId());
			}
		}
		Map<String, List<String>> unstored = new LinkedHashMap<>();
		profiles.forEach((id, name) -> {
			if (!withSlug.contains(id) && name != null) {
				unstored.computeIfAbsent(siteSlugger.slugify(name), ignored -> new ArrayList<>()).add(name);
			}
		});
		unstored.forEach((base, names) -> {
			if (base.isEmpty()) {
				out.add(new DataAuditFinding(PUBLIC_URL, AMBIGUOUS, "%s profiles without a usable URL".formatted(kind.name().toLowerCase()),
						"The names give an empty slug: " + String.join(", ", names.stream().sorted().toList()),
						"Decide a name with letters or digits for each profile"));
			} else if (names.size() > 1 || taken.contains(base)) {
				out.add(new DataAuditFinding(PUBLIC_URL, RECONSTRUCTIBLE,
						"%s URL '%s'".formatted(kind.name().toLowerCase(), base),
						"Profiles without a stored slug collide on it: " + String.join(", ", names.stream().sorted().toList()),
						"The next site generation numbers them by creation order"));
			}
		});
	}

	private static Team leader(PlayoffMatchup matchup) {
		int compare = Integer.compare(matchup.getHomeScore(), matchup.getAwayScore());
		return compare > 0 ? matchup.getTeam1() : compare < 0 ? matchup.getTeam2() : null;
	}

	private static boolean isParticipant(PlayoffMatchup matchup, Team team) {
		return sameTeam(team, matchup.getTeam1()) || sameTeam(team, matchup.getTeam2());
	}

	private static boolean sameTeam(Team a, Team b) {
		return a != null && b != null && a.getId().equals(b.getId());
	}

	private static String pairKey(UUID a, UUID b) {
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	private static <T> String ids(List<T> entities, Function<T, UUID> id) {
		return entities.stream().map(id).map(String::valueOf).collect(Collectors.joining(", "));
	}

	private static String score(Integer home, Integer away) {
		return (home == null ? "–" : home) + ":" + (away == null ? "–" : away);
	}

	private static String team(Team team) {
		return team == null ? "no team" : team.getShortName();
	}

	private static String phaseName(SeasonPhase phase) {
		String name = phase.getLabel() != null ? phase.getLabel() : phase.getPhaseType().name();
		return "'%s' (%s)".formatted(name, phase.getSeason().getName());
	}

	private static String describe(Match match) {
		return "Match %s vs %s, %s · %s".formatted(team(match.getHomeTeam()), team(match.getAwayTeam()),
				describe(match.getMatchday()), match.getId());
	}

	private static String describe(Matchday matchday) {
		return "matchday '%s' of %s".formatted(matchday.getLabel(), phaseName(matchday.getPhase()));
	}

	private static String describe(Race race) {
		return "Leg %s on %s".formatted(race.getId(), describe(race.getMatchday()));
	}

	private static String describe(PlayoffMatchup matchup) {
		return "Playoff '%s', %s, position %d · %s".formatted(matchup.getRound().getPlayoff().getName(),
				matchup.getRound().getLabel(), matchup.getBracketPosition(), matchup.getId());
	}

	private static String describe(SeasonTeam seasonTeam) {
		return "%s in season '%s'".formatted(team(seasonTeam.getTeam()), seasonTeam.getSeason().getName());
	}
}
