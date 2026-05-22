package org.ctc.domain.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.MatchForm;
import org.ctc.discord.dto.ArchiveCategory;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.service.DiscordCategoryResolver;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

	private final MatchRepository matchRepository;
	private final MatchdayRepository matchdayRepository;
	private final TeamRepository teamRepository;
	private final RaceRepository raceRepository;
	private final DiscordCategoryResolver discordCategoryResolver;
	private final Clock clock;

	public Match getMatch(UUID matchId) {
		return matchRepository.findById(matchId)
				.orElseThrow(() -> new EntityNotFoundException("Match", matchId));
	}

	public Match findById(UUID id) {
		return matchRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Match", id));
	}

	public MatchDetailData getDetailData(UUID id) {
		Match match = findById(id);
		try {
			int year = LocalDate.now(clock).getYear();
			List<ArchiveCategory> categories = discordCategoryResolver.resolveArchiveCategoriesFor(year);
			String defaultSelectionId = discordCategoryResolver.defaultSelection(categories)
					.map(ArchiveCategory::id)
					.orElse(null);
			return new MatchDetailData(match, categories, defaultSelectionId);
		} catch (DiscordApiException e) {
			log.warn("Failed to resolve archive categories for match {}: {}", id, e.toString());
			return new MatchDetailData(match, List.of(), null);
		}
	}

	@Transactional
	public void updateDiscordFields(UUID id, MatchForm form) {
		Match match = findById(id);
		match.setDiscordTeaser(form.getDiscordTeaser());
		match.setStreamLink(form.getStreamLink());
		match.setLobbyHost(form.getLobbyHost());
		match.setRaceDirector(form.getRaceDirector());
		match.setStreamer(form.getStreamer());
		matchRepository.save(match);
	}

	public CreateFormData getCreateFormData(UUID matchdayId) {
		var matchday = matchdayRepository.findById(matchdayId)
				.orElseThrow(() -> new EntityNotFoundException("Matchday", matchdayId));
		return new CreateFormData(matchday, matchday.getSeason().getTeams());
	}

	/**
	 * Creates a match with automatic first leg (Race) creation.
	 * Throws IllegalStateException if a duplicate match already exists.
	 */
	@Transactional
	public Match createMatch(UUID matchdayId, UUID homeTeamId, UUID awayTeamId, boolean bye) {
		var matchday = matchdayRepository.findById(matchdayId)
				.orElseThrow(() -> new EntityNotFoundException("Matchday", matchdayId));
		var homeTeam = teamRepository.findById(homeTeamId)
				.orElseThrow(() -> new EntityNotFoundException("Team", homeTeamId));
		var awayTeam = bye ? null : (awayTeamId != null ? teamRepository.findById(awayTeamId).orElse(null) : null);

		// Duplicate check
		if (!bye && awayTeam != null &&
				matchRepository.existsByMatchdayIdAndHomeTeamIdAndAwayTeamId(
						matchdayId, homeTeam.getId(), awayTeam.getId())) {
			throw new IllegalStateException(
					"Match already exists: " + homeTeam.getShortName() + " vs " + awayTeam.getShortName());
		}

		var match = createMatchWithLegs(matchday, homeTeam, awayTeam, bye);

		log.info("Created match: {} {} on {}",
				homeTeam.getShortName(),
				bye ? "bye" : "vs " + (awayTeam != null ? awayTeam.getShortName() : "?"),
				matchday.getLabel());

		return match;
	}

	/**
	 * Creates a match and one Race per leg configured on the season. The match itself keeps
	 * the caller-supplied home/away teams (so {@code match.homeTeam} corresponds to leg 1);
	 * only even-numbered legs (2nd, 4th, ...) get swapped home/away via Race overrides.
	 * Caller is responsible for duplicate checks and logging.
	 */
	@Transactional
	public Match createMatchWithLegs(Matchday matchday, Team homeTeam, Team awayTeam, boolean bye) {
		var match = new Match(matchday, homeTeam, awayTeam);
		match.setBye(bye);
		match = matchRepository.save(match);

		int legs = matchday.getPhase().getLegs();
		for (int leg = 0; leg < legs; leg++) {
			var race = new Race();
			race.setMatchday(matchday);
			race.setMatch(match);
			if (leg % 2 != 0 && !bye) {
				race.setHomeTeamOverride(awayTeam);
				race.setAwayTeamOverride(homeTeam);
			}
			raceRepository.save(race);
		}
		return match;
	}

	/**
	 * Adds an additional leg (Race) to an existing match.
	 * Returns the match. Throws IllegalStateException (with matchday ID prefix) if max legs reached.
	 */
	@Transactional
	public Match addLeg(UUID matchId) {
		var match = matchRepository.findById(matchId)
				.orElseThrow(() -> new EntityNotFoundException("Match", matchId));
		var matchday = match.getMatchday();
		int maxLegs = matchday.getPhase().getLegs();

		if (match.getRaces().size() >= maxLegs) {
			throw new IllegalStateException("Maximum legs reached (" + maxLegs + ")");
		}

		var race = new Race();
		race.setMatchday(matchday);
		race.setMatch(match);
		int legNumber = match.getRaces().size() + 1;
		if (legNumber % 2 == 0 && !match.isBye()) { // even legs (2nd, 4th, ...) get swapped home/away
			race.setHomeTeamOverride(match.getAwayTeam());
			race.setAwayTeamOverride(match.getHomeTeam());
		}
		match.getRaces().add(race);
		raceRepository.save(race);

		log.info("Added leg {} for match {} vs {}",
				match.getRaces().size(), match.getHomeTeam().getShortName(),
				match.getAwayTeam() != null ? match.getAwayTeam().getShortName() : "bye");

		return match;
	}

	/**
	 * Returns the matchday ID for a given match (useful for redirects).
	 */
	public UUID getMatchdayId(UUID matchId) {
		return matchRepository.findById(matchId)
				.orElseThrow(() -> new EntityNotFoundException("Match", matchId))
				.getMatchday().getId();
	}

	/**
	 * Deletes a match and returns the matchday ID for redirect purposes.
	 */
	@Transactional
	public UUID deleteMatch(UUID matchId) {
		var match = matchRepository.findById(matchId)
				.orElseThrow(() -> new EntityNotFoundException("Match", matchId));
		var matchdayId = match.getMatchday().getId();
		matchRepository.delete(match);
		log.info("Deleted match: {} vs {}", match.getHomeTeam().getShortName(),
				match.getAwayTeam() != null ? match.getAwayTeam().getShortName() : "bye");
		return matchdayId;
	}

	/**
	 * Returns the matchday and its season's teams for the match creation form.
	 */
	public record CreateFormData(Matchday matchday, List<Team> teams) {
	}

	public record MatchDetailData(
			Match match,
			List<ArchiveCategory> archiveCategories,
			String defaultSelectionId) {
	}
}
