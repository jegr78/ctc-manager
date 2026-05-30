package org.ctc.domain.service;

import static org.springframework.util.StringUtils.hasText;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.MatchForm;
import org.ctc.admin.dto.MatchPreviewPreFlightResult;
import org.ctc.discord.dto.ArchiveCategory;
import org.ctc.discord.event.MatchPreviewFieldsChangedEvent;
import org.ctc.discord.event.MatchScheduleFieldsChangedEvent;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.discord.service.DiscordCategoryResolver;
import org.ctc.discord.service.DiscordGlobalConfigService;
import org.ctc.discord.service.DiscordPostService;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.MatchdayRepository;
import org.ctc.domain.repository.RaceRepository;
import org.ctc.domain.repository.TeamRepository;
import org.springframework.context.ApplicationEventPublisher;
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
	private final ScoringService scoringService;
	private final DiscordCategoryResolver discordCategoryResolver;
	private final DiscordPostService discordPostService;
	private final DiscordPostRepository discordPostRepository;
	private final DiscordGlobalConfigService discordGlobalConfigService;
	private final ApplicationEventPublisher eventPublisher;
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

	public Map<String, Object> buildMatchDetailModel(UUID id) {
		MatchDetailData data = getDetailData(id);
		Match match = data.match();
		String awayShort = match.getAwayTeam() != null ? match.getAwayTeam().getShortName() : "Bye";
		boolean channelLinked = match.getDiscordChannelId() != null;

		Map<String, Object> model = new HashMap<>();
		model.put("match", match);
		model.put("archiveCategories", data.archiveCategories());
		model.put("defaultSelectionId", data.defaultSelectionId());
		model.put("pageTitle", "Match: " + match.getHomeTeam().getShortName() + " vs " + awayShort);

		model.put("teamCardsPost", findMatchPost(match, DiscordPostType.TEAM_CARDS));
		model.put("settingsPost", findMatchPost(match, DiscordPostType.SETTINGS));
		model.put("lineupsPost", findMatchPost(match, DiscordPostType.LINEUPS));
		model.put("provisionalPost", findMatchPost(match, DiscordPostType.PROVISIONAL_SCORES));
		model.put("matchHasCompleteSettings",
				channelLinked && discordPostService.matchHasCompleteSettings(match));
		model.put("matchHasCompleteLineups",
				channelLinked && discordPostService.matchHasCompleteLineups(match));
		model.put("matchHasProvisionalData",
				channelLinked && discordPostService.matchHasProvisionalData(match));

		DiscordPost matchResultsPost = findMatchPost(match, DiscordPostType.MATCH_RESULTS);
		model.put("matchResultsPost", matchResultsPost);
		model.put("matchResultsStale", isStale(matchResultsPost, latestRaceResultUpdate(match)));
		model.put("schedulePost", findMatchPost(match, DiscordPostType.SCHEDULE));
		model.put("matchCanRenderResults", discordPostService.matchCanRenderResults(match));
		model.put("scheduleVisible",
				match.getRaces().stream().map(Race::getDateTime).anyMatch(Objects::nonNull));

		DiscordGlobalConfig config = discordGlobalConfigService.getOrInitialize();
		String announcementWebhookUrl = config.getAnnouncementWebhookUrl();
		boolean discordAnnouncementsConfigured = hasText(announcementWebhookUrl);
		DiscordPost matchPreviewPost = discordAnnouncementsConfigured
				? discordPostRepository.findByChannelIdAndPostTypeAndMatchId(
						discordPostService.resolveAnnouncementChannelId(announcementWebhookUrl),
						DiscordPostType.MATCH_PREVIEW, match.getId()).orElse(null)
				: null;
		MatchPreviewPreFlightResult matchPreviewPreFlight = discordPostService.canPostMatchPreview(match);
		model.put("discordAnnouncementsConfigured", discordAnnouncementsConfigured);
		model.put("matchPreviewPost", matchPreviewPost);
		model.put("matchPreviewPreFlight", matchPreviewPreFlight);
		return model;
	}

	private DiscordPost findMatchPost(Match match, DiscordPostType type) {
		if (match.getDiscordChannelId() == null) {
			return null;
		}
		return discordPostRepository
				.findByChannelIdAndPostTypeAndMatchId(match.getDiscordChannelId(), type, match.getId())
				.orElse(null);
	}

	private static boolean isStale(DiscordPost post, LocalDateTime latestRaceResultUpdate) {
		if (post == null || post.getUpdatedAt() == null || latestRaceResultUpdate == null) {
			return false;
		}
		return post.getUpdatedAt().isBefore(latestRaceResultUpdate);
	}

	private static LocalDateTime latestRaceResultUpdate(Match match) {
		return match.getRaces().stream()
				.flatMap(r -> r.getResults().stream())
				.map(r -> r.getUpdatedAt())
				.filter(Objects::nonNull)
				.max(LocalDateTime::compareTo)
				.orElse(null);
	}

	@Transactional
	public void updateDiscordFields(UUID id, MatchForm form) {
		Match match = findById(id);
		String beforeTeaser = match.getDiscordTeaser();
		String beforeStreamLink = match.getStreamLink();
		String beforeLobbyHost = match.getLobbyHost();
		String beforeRaceDirector = match.getRaceDirector();
		String beforeStreamer = match.getStreamer();

		match.setDiscordTeaser(form.getDiscordTeaser());
		match.setStreamLink(form.getStreamLink());
		match.setLobbyHost(form.getLobbyHost());
		match.setRaceDirector(form.getRaceDirector());
		match.setStreamer(form.getStreamer());
		Match saved = matchRepository.save(match);

		boolean scheduleFieldsChanged = !Objects.equals(beforeLobbyHost, form.getLobbyHost())
				|| !Objects.equals(beforeRaceDirector, form.getRaceDirector())
				|| !Objects.equals(beforeStreamer, form.getStreamer());
		if (scheduleFieldsChanged) {
			eventPublisher.publishEvent(new MatchScheduleFieldsChangedEvent(saved.getId()));
		}

		boolean previewFieldsChanged = !Objects.equals(beforeTeaser, form.getDiscordTeaser())
				|| !Objects.equals(beforeStreamLink, form.getStreamLink());
		if (previewFieldsChanged) {
			eventPublisher.publishEvent(new MatchPreviewFieldsChangedEvent(saved.getId()));
		}
	}

	@Transactional
	public void updateMatchEdit(UUID id, MatchForm form) {
		updateWalkover(id, form.getWalkoverTeamId());
		updateDiscordFields(id, form);
	}

	@Transactional
	public void updateWalkover(UUID matchId, UUID walkoverTeamId) {
		Match match = findById(matchId);
		if (walkoverTeamId == null) {
			match.setWalkoverTeam(null);
			List<Race> legs = raceRepository.findByMatchId(matchId);
			boolean hasResults = legs.stream().anyMatch(race -> !race.getResults().isEmpty());
			if (hasResults) {
				matchRepository.save(match);
				scoringService.recomputeMatchScoresFromAllLegs(legs.getFirst());
			} else {
				match.setHomeScore(null);
				match.setAwayScore(null);
				matchRepository.save(match);
			}
			log.info("Cleared walkover for match {}", matchId);
			return;
		}
		if (match.isBye()) {
			throw new BusinessRuleException("A bye match cannot be marked as a walkover.");
		}
		if (match.getAwayTeam() == null) {
			throw new BusinessRuleException("Match has no away team — cannot be a walkover.");
		}
		boolean isHome = walkoverTeamId.equals(match.getHomeTeam().getId());
		boolean isAway = walkoverTeamId.equals(match.getAwayTeam().getId());
		if (!isHome && !isAway) {
			throw new BusinessRuleException("Walkover team must be one of the match's two teams.");
		}
		Team team = teamRepository.findById(walkoverTeamId)
				.orElseThrow(() -> new EntityNotFoundException("Team", walkoverTeamId));
		match.setWalkoverTeam(team);
		match.setHomeScore(null);
		match.setAwayScore(null);
		matchRepository.save(match);
		log.info("Set walkover for match {} — team {}", matchId, walkoverTeamId);
	}

	@Transactional
	public void markChannelArchived(UUID id) {
		Match match = findById(id);
		match.setDiscordChannelArchivedAt(LocalDateTime.now(clock));
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
