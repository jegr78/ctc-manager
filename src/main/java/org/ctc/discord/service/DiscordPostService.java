package org.ctc.discord.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.dto.MatchPreviewPreFlightResult;
import org.ctc.admin.service.LineupGraphicService;
import org.ctc.admin.service.MatchResultsGraphicService;
import org.ctc.admin.service.MatchdayPairingsGraphicService;
import org.ctc.admin.service.MatchdayResultsGraphicService;
import org.ctc.admin.service.MatchdayScheduleGraphicService;
import org.ctc.admin.service.PowerRankingsGraphicService;
import org.ctc.admin.service.ProvisionalScoresGraphicService;
import org.ctc.admin.service.ResultsGraphicService;
import org.ctc.admin.service.SettingsGraphicService;
import org.ctc.admin.service.StandingsGraphicService;
import org.ctc.admin.service.TeamCardService;
import org.ctc.discord.DiscordEmojiCache;
import org.ctc.discord.DiscordHostValidator;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.DiscordTimestamps;
import org.ctc.discord.DiscordWebhookClient;
import org.ctc.discord.dto.Channel;
import org.ctc.discord.dto.ChannelModifyRequest;
import org.ctc.discord.dto.DiscordPostRef;
import org.ctc.discord.dto.Embed;
import org.ctc.discord.dto.EmbedField;
import org.ctc.discord.dto.NamedAttachment;
import org.ctc.discord.dto.ThreadMetadata;
import org.ctc.discord.dto.WebhookMessage;
import org.ctc.discord.dto.WebhookPayload;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PhaseLayout;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonPhase;
import org.ctc.domain.model.SeasonPhaseGroup;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.repository.RaceLineupRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class DiscordPostService {

	private static final Pattern WEBHOOK_URL_PATTERN =
			Pattern.compile("^https?://[^/]+(?:/api)?(?:/v\\d+)?/webhooks/(\\d+)/([^/?]+)(?:\\?.*)?$");

	private static final String UPLOADS_PREFIX = "/uploads/";

	private final DiscordWebhookClient webhookClient;
	private final DiscordRestClient restClient;
	private final DiscordPostRepository discordPostRepository;
	private final DiscordHostValidator hostValidator;
	private final DiscordGlobalConfigService globalConfigService;
	private final Clock clock;
	private final TeamCardService teamCardService;
	private final SeasonTeamRepository seasonTeamRepository;
	private final SettingsGraphicService settingsGraphicService;
	private final LineupGraphicService lineupGraphicService;
	private final RaceLineupRepository raceLineupRepository;
	private final MatchResultsGraphicService matchResultsGraphicService;
	private final ResultsGraphicService resultsGraphicService;
	private final ProvisionalScoresGraphicService provisionalScoresGraphicService;
	private final DiscordTimestamps discordTimestamps;
	private final DiscordEmojiCache emojiCache;
	private final MatchdayResultsGraphicService matchdayResultsGraphicService;
	private final PowerRankingsGraphicService powerRankingsGraphicService;
	private final StandingsGraphicService standingsGraphicService;
	private final MatchdayPairingsGraphicService matchdayPairingsGraphicService;
	private final MatchdayScheduleGraphicService matchdayScheduleGraphicService;
	private final Path uploadDir;

	public static final String DEFAULT_MATCHDAY_PAIRINGS_TEMPLATE =
			"# {{matchdayNumber}} Pairings\n\n"
					+ "- Home Teams are on the left hand side.\n"
					+ "- Deadline for the picks: {{deadline}} (use the pinned form in your private team chat channel)\n"
					+ "- Scheduled weekend for the races: {{weekend}}\n\n"
					+ "Game On! {{ctcEmoji}}";

	@SuppressFBWarnings(
			value = "EI_EXPOSE_REP2",
			justification = "Spring-managed singleton beans (DiscordWebhookClient, DiscordRestClient, DiscordPostRepository, "
					+ "DiscordHostValidator, DiscordGlobalConfigService, TeamCardService, SeasonTeamRepository, "
					+ "SettingsGraphicService, LineupGraphicService, RaceLineupRepository, MatchResultsGraphicService, "
					+ "ResultsGraphicService, ProvisionalScoresGraphicService, DiscordTimestamps, DiscordEmojiCache, "
					+ "MatchdayResultsGraphicService, PowerRankingsGraphicService, StandingsGraphicService, "
					+ "MatchdayPairingsGraphicService, MatchdayScheduleGraphicService) "
					+ "are intentionally shared by-reference — defensive copying would break framework wiring. "
					+ "Matches the implicit suppression that lombok.config adds to @RequiredArgsConstructor "
					+ "(see CLAUDE.md SpotBugs section + lombok.config invariant).")
	public DiscordPostService(
			DiscordWebhookClient webhookClient,
			DiscordRestClient restClient,
			DiscordPostRepository discordPostRepository,
			DiscordHostValidator hostValidator,
			DiscordGlobalConfigService globalConfigService,
			Clock clock,
			TeamCardService teamCardService,
			SeasonTeamRepository seasonTeamRepository,
			SettingsGraphicService settingsGraphicService,
			LineupGraphicService lineupGraphicService,
			RaceLineupRepository raceLineupRepository,
			MatchResultsGraphicService matchResultsGraphicService,
			ResultsGraphicService resultsGraphicService,
			ProvisionalScoresGraphicService provisionalScoresGraphicService,
			DiscordTimestamps discordTimestamps,
			DiscordEmojiCache emojiCache,
			MatchdayResultsGraphicService matchdayResultsGraphicService,
			PowerRankingsGraphicService powerRankingsGraphicService,
			StandingsGraphicService standingsGraphicService,
			MatchdayPairingsGraphicService matchdayPairingsGraphicService,
			MatchdayScheduleGraphicService matchdayScheduleGraphicService,
			@Value("${app.upload-dir:uploads}") String uploadDir) {
		this.webhookClient = webhookClient;
		this.restClient = restClient;
		this.discordPostRepository = discordPostRepository;
		this.hostValidator = hostValidator;
		this.globalConfigService = globalConfigService;
		this.clock = clock;
		this.teamCardService = teamCardService;
		this.seasonTeamRepository = seasonTeamRepository;
		this.settingsGraphicService = settingsGraphicService;
		this.lineupGraphicService = lineupGraphicService;
		this.raceLineupRepository = raceLineupRepository;
		this.matchResultsGraphicService = matchResultsGraphicService;
		this.resultsGraphicService = resultsGraphicService;
		this.provisionalScoresGraphicService = provisionalScoresGraphicService;
		this.discordTimestamps = discordTimestamps;
		this.emojiCache = emojiCache;
		this.matchdayResultsGraphicService = matchdayResultsGraphicService;
		this.powerRankingsGraphicService = powerRankingsGraphicService;
		this.standingsGraphicService = standingsGraphicService;
		this.matchdayPairingsGraphicService = matchdayPairingsGraphicService;
		this.matchdayScheduleGraphicService = matchdayScheduleGraphicService;
		this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
	}

	public boolean matchCanRenderResults(Match match) {
		List<Race> races = match.getRaces();
		return !races.isEmpty() && races.stream().allMatch(r -> !r.getResults().isEmpty());
	}

	@Transactional
	public DiscordPost postMatchResults(Match match) throws DiscordApiException {
		if (!matchCanRenderResults(match)) {
			throw new BusinessRuleException("Match results require at least one race result.");
		}
		List<Race> races = match.getRaces();
		List<NamedAttachment> attachments = new ArrayList<>(races.size() + 1);
		try {
			byte[] overviewPng = matchResultsGraphicService.generateMatchResults(match);
			attachments.add(new NamedAttachment("match-results.png", overviewPng));
			for (int i = 0; i < races.size(); i++) {
				byte[] racePng = readPng(resultsGraphicService.generateResults(races.get(i)));
				attachments.add(new NamedAttachment("race-" + (i + 1) + "-results.png", racePng));
			}
		} catch (IOException e) {
			throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
		}
		return postOrEdit(
				match.getDiscordChannelId(),
				match.getDiscordChannelWebhookUrl(),
				DiscordPostType.MATCH_RESULTS,
				WebhookPayload.empty(),
				attachments,
				DiscordPostRef.match(match));
	}

	@Transactional
	public DiscordPost postSchedule(Match match) throws DiscordApiException {
		LocalDateTime firstRaceTime = firstRaceTime(match)
				.orElseThrow(() -> new BusinessRuleException(
						"Schedule requires at least one race with a scheduled time."));
		WebhookPayload payload = buildSchedulePayload(match, firstRaceTime);
		return postOrEdit(
				match.getDiscordChannelId(),
				match.getDiscordChannelWebhookUrl(),
				DiscordPostType.SCHEDULE,
				payload,
				List.of(),
				DiscordPostRef.match(match));
	}

	@Transactional
	public void autoEditScheduleIfNeeded(Match match) throws DiscordApiException {
		Optional<DiscordPost> existing = discordPostRepository
				.findByChannelIdAndPostTypeAndMatchId(
						match.getDiscordChannelId(), DiscordPostType.SCHEDULE, match.getId());
		if (existing.isEmpty()) {
			return;
		}
		Optional<LocalDateTime> firstRaceTime = firstRaceTime(match);
		if (firstRaceTime.isEmpty()) {
			return;
		}
		WebhookPayload payload = buildSchedulePayload(match, firstRaceTime.get());
		postOrEdit(
				match.getDiscordChannelId(),
				match.getDiscordChannelWebhookUrl(),
				DiscordPostType.SCHEDULE,
				payload,
				List.of(),
				DiscordPostRef.match(match));
	}

	private static Optional<LocalDateTime> firstRaceTime(Match match) {
		return match.getRaces().stream()
				.map(Race::getDateTime)
				.filter(Objects::nonNull)
				.min(Comparator.naturalOrder());
	}

	public String resolveAnnouncementChannelId(String webhookUrl) {
		return parseWebhookUrl(webhookUrl).id();
	}

	public MatchPreviewPreFlightResult canPostMatchPreview(Match match) {
		DiscordGlobalConfig config = globalConfigService.getOrInitialize();
		if (match.getDiscordTeaser() == null || match.getDiscordTeaser().isBlank()) {
			return new MatchPreviewPreFlightResult(false, "Add a teaser text on Match-Edit first");
		}
		if (!matchHasCompleteSettings(match)) {
			return new MatchPreviewPreFlightResult(false, "Configure Race Settings for all races first");
		}
		if (!matchHasCompleteLineups(match)) {
			return new MatchPreviewPreFlightResult(false, "Configure Race Lineups for all races first");
		}
		if (firstRaceTime(match).isEmpty()) {
			return new MatchPreviewPreFlightResult(false, "Set Race date+time first");
		}
		String webhookUrl = config.getAnnouncementWebhookUrl();
		if (webhookUrl == null || webhookUrl.isBlank()) {
			return new MatchPreviewPreFlightResult(false, "Configure announcement-webhook in Discord settings");
		}
		return new MatchPreviewPreFlightResult(true, null);
	}

	@Transactional
	public DiscordPost postMatchPreview(Match match) throws DiscordApiException {
		DiscordGlobalConfig config = globalConfigService.getOrInitialize();
		MatchPreviewPreFlightResult pre = canPostMatchPreview(match);
		if (!pre.canPost()) {
			throw new BusinessRuleException("Cannot post Match Preview: " + pre.disabledReason());
		}
		String webhookUrl = config.getAnnouncementWebhookUrl();
		String channelId = parseWebhookUrl(webhookUrl).id();
		LocalDateTime firstRaceTime = firstRaceTime(match).orElseThrow();
		String content = buildMatchPreviewMarkdown(match, config, firstRaceTime);
		List<NamedAttachment> attachments = buildMatchPreviewAttachments(match);
		return postOrEdit(
				channelId,
				webhookUrl,
				DiscordPostType.MATCH_PREVIEW,
				new WebhookPayload(content, List.of()),
				attachments,
				DiscordPostRef.match(match));
	}

	@Transactional
	public void autoEditMatchPreviewIfNeeded(Match match) throws DiscordApiException {
		DiscordGlobalConfig config = globalConfigService.getOrInitialize();
		String webhookUrl = config.getAnnouncementWebhookUrl();
		if (webhookUrl == null || webhookUrl.isBlank()) {
			return;
		}
		String channelId = parseWebhookUrl(webhookUrl).id();
		Optional<DiscordPost> existing = discordPostRepository
				.findByChannelIdAndPostTypeAndMatchId(channelId, DiscordPostType.MATCH_PREVIEW, match.getId());
		if (existing.isEmpty()) {
			return;
		}
		Optional<LocalDateTime> firstRaceTime = firstRaceTime(match);
		if (firstRaceTime.isEmpty()) {
			return;
		}
		String content = buildMatchPreviewMarkdown(match, config, firstRaceTime.get());
		List<NamedAttachment> attachments = buildMatchPreviewAttachments(match);
		postOrEdit(
				channelId,
				webhookUrl,
				DiscordPostType.MATCH_PREVIEW,
				new WebhookPayload(content, List.of()),
				attachments,
				DiscordPostRef.match(match));
	}

	private String buildMatchPreviewMarkdown(Match match, DiscordGlobalConfig config, LocalDateTime firstRaceTime) {
		String seasonName = match.getMatchday().getSeason().getName();
		String matchdayLabel = match.getMatchday().getLabel();
		String homeShort = match.getHomeTeam().getShortName();
		String awayShort = match.getAwayTeam().getShortName();
		String teaser = match.getDiscordTeaser();
		String dateLine = discordTimestamps.longDateTime(firstRaceTime);
		String streamLine = (match.getStreamLink() != null && !match.getStreamLink().isBlank())
				? match.getStreamLink()
				: "TBA";
		String homeEmoji = emojiCache.emojiFor(match.getHomeTeam().getParentOrSelf().getShortName());
		String vsEmoji = emojiCache.emojiFor(config.getVsEmojiName());
		String awayEmoji = emojiCache.emojiFor(match.getAwayTeam().getParentOrSelf().getShortName());
		return "# " + seasonName + "\n"
				+ "## " + matchdayLabel + "\n"
				+ "### " + homeShort + " vs. " + awayShort + "\n\n"
				+ teaser + "\n\n"
				+ "- Date: " + dateLine + "\n"
				+ "- Stream: " + streamLine + "\n\n"
				+ "Game On! " + homeEmoji + " " + vsEmoji + " " + awayEmoji;
	}

	public MatchPreviewPreFlightResult canPostMatchdayResults(Matchday matchday, DiscordGlobalConfig config) {
		if (!allNonByeMatchesFinal(matchday)) {
			return new MatchPreviewPreFlightResult(false, "Mark all matches as final first");
		}
		String threadId = matchday.getSeason().getDiscordRaceResultsThreadId();
		if (threadId == null || threadId.isBlank()) {
			return new MatchPreviewPreFlightResult(false, "Link a race-results thread on the Season page first");
		}
		String webhookUrl = config.getRaceResultsForumWebhookUrl();
		if (webhookUrl == null || webhookUrl.isBlank()) {
			return new MatchPreviewPreFlightResult(false, "Configure race-results forum-webhook in Discord settings");
		}
		return new MatchPreviewPreFlightResult(true, null);
	}

	public MatchPreviewPreFlightResult canPostPowerRankings(Matchday matchday, DiscordGlobalConfig config) {
		String threadId = matchday.getSeason().getDiscordRaceResultsThreadId();
		if (threadId == null || threadId.isBlank()) {
			return new MatchPreviewPreFlightResult(false, "Link a race-results thread on the Season page first");
		}
		String webhookUrl = config.getRaceResultsForumWebhookUrl();
		if (webhookUrl == null || webhookUrl.isBlank()) {
			return new MatchPreviewPreFlightResult(false, "Configure race-results forum-webhook in Discord settings");
		}
		return new MatchPreviewPreFlightResult(true, null);
	}

	@Transactional
	public DiscordPost postMatchdayResults(Matchday matchday) throws DiscordApiException {
		DiscordGlobalConfig config = globalConfigService.getOrInitialize();
		MatchPreviewPreFlightResult pre = canPostMatchdayResults(matchday, config);
		if (!pre.canPost()) {
			throw new BusinessRuleException("Cannot post Match Day Results: " + pre.disabledReason());
		}
		String threadId = matchday.getSeason().getDiscordRaceResultsThreadId();
		String webhookUrl = config.getRaceResultsForumWebhookUrl();
		String channelId = parseWebhookUrl(webhookUrl).id();
		byte[] png;
		try {
			png = matchdayResultsGraphicService.generateResults(matchday);
		} catch (DiscordApiException e) {
			throw e;
		} catch (IOException e) {
			throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
		}
		String filename = "matchday-results-" + slug(matchday.getLabel()) + ".png";
		NamedAttachment attachment = new NamedAttachment(filename, png);
		return postOrEdit(
				channelId,
				webhookUrl,
				DiscordPostType.MATCHDAY_OVERVIEW,
				WebhookPayload.empty(),
				List.of(attachment),
				DiscordPostRef.matchday(matchday),
				threadId);
	}

	public MatchPreviewPreFlightResult canPostMatchdayPairings(Matchday matchday, DiscordGlobalConfig config) {
		if (matchday.getPickDeadline() == null) {
			return new MatchPreviewPreFlightResult(false, "Set pick deadline first");
		}
		if (matchday.getScheduledWeekend() == null || matchday.getScheduledWeekend().isBlank()) {
			return new MatchPreviewPreFlightResult(false, "Set scheduled weekend first");
		}
		List<Match> nonByeMatches = matchday.getMatches().stream()
				.filter(m -> !m.isBye())
				.toList();
		if (nonByeMatches.isEmpty()) {
			return new MatchPreviewPreFlightResult(false, "Add at least one non-bye match to the matchday first");
		}
		boolean allMatchesHaveTeams = nonByeMatches.stream()
				.allMatch(m -> m.getHomeTeam() != null && m.getAwayTeam() != null);
		if (!allMatchesHaveTeams) {
			return new MatchPreviewPreFlightResult(false, "Assign teams to all matches first");
		}
		String webhookUrl = config.getAnnouncementWebhookUrl();
		if (webhookUrl == null || webhookUrl.isBlank()) {
			return new MatchPreviewPreFlightResult(false, "Configure announcement-webhook in Discord settings");
		}
		return new MatchPreviewPreFlightResult(true, null);
	}

	@Transactional
	public DiscordPost postMatchdayPairings(Matchday matchday) throws DiscordApiException {
		DiscordGlobalConfig config = globalConfigService.getOrInitialize();
		MatchPreviewPreFlightResult pre = canPostMatchdayPairings(matchday, config);
		if (!pre.canPost()) {
			throw new BusinessRuleException("Cannot post Matchday Pairings: " + pre.disabledReason());
		}
		String webhookUrl = config.getAnnouncementWebhookUrl();
		String channelId = parseWebhookUrl(webhookUrl).id();
		String content = buildMatchdayPairingsMarkdown(matchday, config);
		byte[] png;
		try {
			png = matchdayPairingsGraphicService.generatePairings(matchday);
		} catch (IOException e) {
			throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
		}
		String filename = "matchday-pairings-" + slug(matchday.getLabel()) + ".png";
		NamedAttachment attachment = new NamedAttachment(filename, png);
		return postOrEdit(
				channelId,
				webhookUrl,
				DiscordPostType.MATCHDAY_PAIRINGS,
				new WebhookPayload(content, List.of()),
				List.of(attachment),
				DiscordPostRef.matchday(matchday));
	}

	public MatchPreviewPreFlightResult canPostMatchdaySchedule(Matchday matchday, DiscordGlobalConfig config) {
		List<Match> nonByeMatches = matchday.getMatches().stream()
				.filter(m -> !m.isBye())
				.toList();
		if (nonByeMatches.isEmpty()) {
			return new MatchPreviewPreFlightResult(false, "Add at least one non-bye match to the matchday first");
		}
		boolean allMatchesHaveRaceTime = nonByeMatches.stream()
				.allMatch(m -> firstRaceTime(m).isPresent());
		if (!allMatchesHaveRaceTime) {
			return new MatchPreviewPreFlightResult(false, "Set Race date+time for all matches first");
		}
		String webhookUrl = config.getAnnouncementWebhookUrl();
		if (webhookUrl == null || webhookUrl.isBlank()) {
			return new MatchPreviewPreFlightResult(false, "Configure announcement-webhook in Discord settings");
		}
		return new MatchPreviewPreFlightResult(true, null);
	}

	@Transactional
	public DiscordPost postMatchdaySchedule(Matchday matchday) throws DiscordApiException {
		DiscordGlobalConfig config = globalConfigService.getOrInitialize();
		MatchPreviewPreFlightResult pre = canPostMatchdaySchedule(matchday, config);
		if (!pre.canPost()) {
			throw new BusinessRuleException("Cannot post Matchday Schedule: " + pre.disabledReason());
		}
		String webhookUrl = config.getAnnouncementWebhookUrl();
		String channelId = parseWebhookUrl(webhookUrl).id();
		byte[] png;
		try {
			png = matchdayScheduleGraphicService.generateSchedule(matchday);
		} catch (IOException e) {
			throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
		}
		String filename = "matchday-schedule-" + slug(matchday.getLabel()) + ".png";
		NamedAttachment attachment = new NamedAttachment(filename, png);
		return postOrEdit(
				channelId,
				webhookUrl,
				DiscordPostType.MATCHDAY_SCHEDULE,
				WebhookPayload.empty(),
				List.of(attachment),
				DiscordPostRef.matchday(matchday));
	}

	private String buildMatchdayPairingsMarkdown(Matchday matchday, DiscordGlobalConfig config) {
		String template = (config.getMatchdayPairingsTemplate() != null
				&& !config.getMatchdayPairingsTemplate().isBlank())
				? config.getMatchdayPairingsTemplate()
				: DEFAULT_MATCHDAY_PAIRINGS_TEMPLATE;
		String matchdayNumber = matchday.getLabel() != null ? matchday.getLabel() : "?";
		String deadline = matchday.getPickDeadline() != null
				? discordTimestamps.longDateTime(matchday.getPickDeadline())
				: "_TBD_";
		String weekend = (matchday.getScheduledWeekend() != null
				&& !matchday.getScheduledWeekend().isBlank())
				? matchday.getScheduledWeekend()
				: "_TBD_";
		String ctcEmoji = emojiCache.emojiFor(config.getVsEmojiName());
		return template
				.replace("{{matchdayNumber}}", matchdayNumber)
				.replace("{{deadline}}", deadline)
				.replace("{{weekend}}", weekend)
				.replace("{{ctcEmoji}}", ctcEmoji);
	}

	@Transactional
	public DiscordPost postPowerRankings(Matchday matchday) throws DiscordApiException {
		DiscordGlobalConfig config = globalConfigService.getOrInitialize();
		MatchPreviewPreFlightResult pre = canPostPowerRankings(matchday, config);
		if (!pre.canPost()) {
			throw new BusinessRuleException("Cannot post Power Rankings: " + pre.disabledReason());
		}
		String threadId = matchday.getSeason().getDiscordRaceResultsThreadId();
		String webhookUrl = config.getRaceResultsForumWebhookUrl();
		String channelId = parseWebhookUrl(webhookUrl).id();
		Season season = matchday.getSeason();
		int year = season.getYear();
		int number = season.getNumber();
		List<java.util.UUID> teamIds = powerRankingsGraphicService.loadTeamsForSeasonGroup(year, number).stream()
				.map(org.ctc.admin.dto.RankedTeamData::teamId)
				.toList();
		String subtitle = matchday.getLabel();
		byte[] png;
		try {
			png = powerRankingsGraphicService.generateRankings(year, number, subtitle, teamIds);
		} catch (DiscordApiException e) {
			throw e;
		} catch (IOException e) {
			throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
		}
		String filename = "power-rankings-" + slug(matchday.getLabel()) + ".png";
		NamedAttachment attachment = new NamedAttachment(filename, png);
		return postOrEdit(
				channelId,
				webhookUrl,
				DiscordPostType.POWER_RANKINGS,
				WebhookPayload.empty(),
				List.of(attachment),
				DiscordPostRef.matchday(matchday),
				threadId);
	}

	public MatchPreviewPreFlightResult canPostStandings(Season season, DiscordGlobalConfig config) {
		String threadId = season.getDiscordStandingsThreadId();
		if (threadId == null || threadId.isBlank()) {
			return new MatchPreviewPreFlightResult(false, "Link a standings forum-thread above first");
		}
		String webhookUrl = config.getStandingsForumWebhookUrl();
		if (webhookUrl == null || webhookUrl.isBlank()) {
			return new MatchPreviewPreFlightResult(false, "Configure standings forum-webhook in Discord settings");
		}
		return new MatchPreviewPreFlightResult(true, null);
	}

	@Transactional
	public DiscordPost postStandings(Season season, SeasonPhase phase) throws DiscordApiException {
		DiscordGlobalConfig config = globalConfigService.getOrInitialize();
		MatchPreviewPreFlightResult pre = canPostStandings(season, config);
		if (!pre.canPost()) {
			throw new BusinessRuleException("Cannot post Standings: " + pre.disabledReason());
		}
		String threadId = season.getDiscordStandingsThreadId();
		String webhookUrl = config.getStandingsForumWebhookUrl();
		String channelId = parseWebhookUrl(webhookUrl).id();
		List<byte[]> pngs;
		try {
			pngs = standingsGraphicService.generateStandingsBytes(season, phase);
		} catch (DiscordApiException e) {
			throw e;
		} catch (IOException e) {
			throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
		}
		List<NamedAttachment> attachments = buildStandingsAttachments(phase, pngs);
		return postOrEdit(
				channelId,
				webhookUrl,
				DiscordPostType.STANDINGS,
				WebhookPayload.empty(),
				attachments,
				DiscordPostRef.seasonPhase(season, phase),
				threadId);
	}

	private static List<NamedAttachment> buildStandingsAttachments(SeasonPhase phase, List<byte[]> pngs) {
		String typeSlug = phase.getPhaseType().name().toLowerCase(java.util.Locale.ROOT);
		if (phase.getLayout() != PhaseLayout.GROUPS || pngs.size() == 1) {
			return List.of(new NamedAttachment("standings-" + typeSlug + ".png", pngs.get(0)));
		}
		List<SeasonPhaseGroup> groups = phase.getGroups().stream()
				.sorted(Comparator.comparingInt(SeasonPhaseGroup::getSortIndex))
				.toList();
		List<NamedAttachment> out = new ArrayList<>(pngs.size());
		for (int i = 0; i < pngs.size(); i++) {
			String groupSlug = i < groups.size() ? slug(groups.get(i).getName()) : "group-" + (i + 1);
			out.add(new NamedAttachment("standings-" + typeSlug + "-" + groupSlug + ".png", pngs.get(i)));
		}
		return out;
	}

	private static boolean allNonByeMatchesFinal(Matchday matchday) {
		List<Match> matches = matchday.getMatches();
		if (matches.isEmpty()) {
			return false;
		}
		List<Match> contested = matches.stream().filter(m -> !m.isBye()).toList();
		if (contested.isEmpty()) {
			return false;
		}
		return contested.stream().allMatch(m -> m.getHomeScore() != null && m.getAwayScore() != null);
	}

	private static String slug(String label) {
		return label.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
	}

	private List<NamedAttachment> buildMatchPreviewAttachments(Match match) throws DiscordApiException {
		List<Race> races = match.getRaces();
		List<NamedAttachment> attachments = new ArrayList<>(races.size() * 2);
		try {
			for (int i = 0; i < races.size(); i++) {
				Race race = races.get(i);
				int n = i + 1;
				attachments.add(new NamedAttachment(
						"settings-md" + n + ".png",
						readPng(settingsGraphicService.generateSettings(race))));
				attachments.add(new NamedAttachment(
						"lineups-md" + n + ".png",
						readPng(lineupGraphicService.generateLineup(race))));
			}
		} catch (IOException e) {
			throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
		}
		return attachments;
	}

	private WebhookPayload buildSchedulePayload(Match match, LocalDateTime firstRaceTime) {
		String dateField = discordTimestamps.longDateTime(firstRaceTime)
				+ " (" + discordTimestamps.relative(firstRaceTime) + ")";
		List<EmbedField> fields = List.of(
				new EmbedField("Date", dateField, false),
				new EmbedField("Lobby Host", orTbd(match.getLobbyHost()), false),
				new EmbedField("Race Director", orTbd(match.getRaceDirector()), false),
				new EmbedField("Streamer", streamerField(match), false));
		Embed embed = new Embed("Match Schedule", null, fields);
		return new WebhookPayload(null, List.of(embed));
	}

	private static String streamerField(Match match) {
		String name = match.getStreamer();
		String link = match.getStreamLink();
		boolean hasName = name != null && !name.isBlank();
		boolean hasLink = link != null && !link.isBlank();
		if (hasName && hasLink) {
			return "[" + name + "](" + escapeMarkdownLinkUrl(link) + ")";
		}
		if (hasLink) {
			return "[Watch Stream](" + escapeMarkdownLinkUrl(link) + ")";
		}
		if (hasName) {
			return name;
		}
		return "_TBD_";
	}

	private static String escapeMarkdownLinkUrl(String url) {
		return url.replace(")", "\\)");
	}

	private static String orTbd(String value) {
		return (value == null || value.isBlank()) ? "_TBD_" : value;
	}

	public boolean matchHasCompleteSettings(Match match) {
		List<Race> races = match.getRaces();
		return !races.isEmpty() && races.stream().allMatch(r -> r.getSettings() != null);
	}

	public boolean matchHasCompleteLineups(Match match) {
		List<Race> races = match.getRaces();
		return !races.isEmpty()
				&& races.stream().allMatch(r -> !raceLineupRepository.findByRaceId(r.getId()).isEmpty());
	}

	public boolean matchHasProvisionalData(Match match) {
		List<Race> races = match.getRaces();
		return !races.isEmpty() && races.stream().anyMatch(r -> !r.getResults().isEmpty());
	}

	@Transactional
	public DiscordPost postProvisionalScores(Match match) throws DiscordApiException {
		if (!matchHasProvisionalData(match)) {
			throw new BusinessRuleException("Provisional needs at least one completed race");
		}
		List<NamedAttachment> attachments = new ArrayList<>();
		try {
			int raceNumber = 0;
			for (Race race : match.getRaces()) {
				if (race.getResults().isEmpty()) {
					continue;
				}
				raceNumber++;
				byte[] png = provisionalScoresGraphicService.generateProvisional(race, raceNumber);
				attachments.add(new NamedAttachment("provisional-race-" + raceNumber + ".png", png));
			}
		} catch (IOException e) {
			throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
		}
		return postOrEdit(
				match.getDiscordChannelId(),
				match.getDiscordChannelWebhookUrl(),
				DiscordPostType.PROVISIONAL_SCORES,
				WebhookPayload.empty(),
				attachments,
				DiscordPostRef.match(match));
	}

	@Transactional
	public DiscordPost postSettings(Match match) throws DiscordApiException {
		if (!matchHasCompleteSettings(match)) {
			throw new BusinessRuleException("Configure settings for all races first");
		}
		return postRaceBundle(match, DiscordPostType.SETTINGS, "settings-race-",
				race -> readRaceGraphic(settingsGraphicService.generateSettings(race)));
	}

	@Transactional
	public DiscordPost postLineups(Match match) throws DiscordApiException {
		if (!matchHasCompleteLineups(match)) {
			throw new BusinessRuleException("Configure lineups for all races first");
		}
		return postRaceBundle(match, DiscordPostType.LINEUPS, "lineups-race-",
				race -> readRaceGraphic(lineupGraphicService.generateLineup(race)));
	}

	private DiscordPost postRaceBundle(
			Match match, DiscordPostType type, String filenamePrefix, RaceGraphicLoader loader)
			throws DiscordApiException {
		List<Race> races = match.getRaces();
		List<NamedAttachment> attachments = new ArrayList<>(races.size());
		try {
			for (int i = 0; i < races.size(); i++) {
				byte[] bytes = loader.load(races.get(i));
				attachments.add(new NamedAttachment(filenamePrefix + (i + 1) + ".png", bytes));
			}
		} catch (IOException e) {
			throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
		}
		return postOrEdit(
				match.getDiscordChannelId(),
				match.getDiscordChannelWebhookUrl(),
				type,
				WebhookPayload.empty(),
				attachments,
				DiscordPostRef.match(match));
	}

	private byte[] readRaceGraphic(String uploadsUrl) throws IOException {
		return readPng(uploadsUrl);
	}

	@FunctionalInterface
	private interface RaceGraphicLoader {
		byte[] load(Race race) throws IOException;
	}

	@Transactional
	public DiscordPost postOrEdit(
			String channelId,
			String webhookUrl,
			DiscordPostType type,
			WebhookPayload payload,
			List<NamedAttachment> attachments,
			DiscordPostRef ref)
			throws DiscordApiException {
		return postOrEdit(channelId, webhookUrl, type, payload, attachments, ref, null);
	}

	@Transactional
	public DiscordPost postOrEdit(
			String channelId,
			String webhookUrl,
			DiscordPostType type,
			WebhookPayload payload,
			List<NamedAttachment> attachments,
			DiscordPostRef ref,
			@Nullable String threadId)
			throws DiscordApiException {
		hostValidator.requireAllowed(webhookUrl);
		if (threadId != null) {
			unarchiveIfArchived(threadId);
		}
		WebhookCredentials creds = parseWebhookUrl(webhookUrl);
		Optional<DiscordPost> existing = switch (ref) {
			case DiscordPostRef.MatchRef m ->
					discordPostRepository.findByChannelIdAndPostTypeAndMatchId(channelId, type, m.id());
			case DiscordPostRef.RaceRef r ->
					discordPostRepository.findByChannelIdAndPostTypeAndRaceId(channelId, type, r.id());
			case DiscordPostRef.SeasonRef s -> s.phaseId() != null
					? discordPostRepository.findByChannelIdAndPostTypeAndSeasonIdAndPhaseId(
							channelId, type, s.seasonId(), s.phaseId())
					: discordPostRepository.findByChannelIdAndPostTypeAndSeasonId(channelId, type, s.seasonId());
			case DiscordPostRef.MatchdayRef d ->
					discordPostRepository.findByChannelIdAndPostTypeAndMatchdayId(channelId, type, d.id());
		};
		LocalDateTime now = LocalDateTime.now(clock);

		if (existing.isPresent()) {
			DiscordPost row = existing.get();
			if (attachments.isEmpty()) {
				webhookClient.editMessage(webhookUrl, row.getMessageId(), payload, threadId);
			} else {
				webhookClient.editMessageWithAttachments(
						webhookUrl, row.getMessageId(), payload, attachments, threadId);
				row.setAttachmentsReplacedAt(now);
			}
			DiscordPost saved = discordPostRepository.save(row);
			log.info("Edited {} for ref {} (messageId={})", type, ref, saved.getMessageId());
			return saved;
		}

		WebhookMessage msg = attachments.isEmpty()
				? webhookClient.execute(webhookUrl, payload, threadId)
				: webhookClient.executeMultipart(webhookUrl, payload, attachments, threadId);
		DiscordPost row = new DiscordPost();
		row.setChannelId(channelId);
		row.setMessageId(msg.id());
		row.setWebhookId(creds.id());
		row.setWebhookToken(creds.token());
		row.setPostType(type);
		row.setPostedAt(now);
		if (!attachments.isEmpty()) {
			row.setAttachmentsReplacedAt(now);
		}
		ref.applyTo(row);
		DiscordPost saved = discordPostRepository.save(row);
		log.info("Posted {} for ref {} (messageId={})", type, ref, saved.getMessageId());
		return saved;
	}

	private void unarchiveIfArchived(String threadId) throws DiscordApiException {
		Channel thread = restClient.fetchChannel(threadId);
		ThreadMetadata md = thread.threadMetadata();
		if (md != null && md.isArchived()) {
			log.info("Unarchiving forum thread {} before post", threadId);
			restClient.modifyChannel(threadId, ChannelModifyRequest.unarchive());
		}
	}

	public boolean canPostRaceResultToForum(Race race, DiscordGlobalConfig config) {
		if (race.getResults().isEmpty()) {
			return false;
		}
		Season season = race.getMatchday().getSeason();
		String threadId = season.getDiscordRaceResultsThreadId();
		if (threadId == null || threadId.isBlank()) {
			return false;
		}
		String webhookUrl = config.getRaceResultsForumWebhookUrl();
		return webhookUrl != null && !webhookUrl.isBlank();
	}

	@Transactional
	public DiscordPost postRaceResultToForumThread(Race race) throws DiscordApiException {
		DiscordGlobalConfig config = globalConfigService.getOrInitialize();
		if (!canPostRaceResultToForum(race, config)) {
			throw new BusinessRuleException(
					"Race result cannot be posted to forum-thread — check race results, linked thread, and webhook configuration.");
		}
		Season season = race.getMatchday().getSeason();
		String threadId = season.getDiscordRaceResultsThreadId();
		String webhookUrl = config.getRaceResultsForumWebhookUrl();
		WebhookCredentials creds = parseWebhookUrl(webhookUrl);
		try {
			byte[] png = resultsGraphicService.generateResultsBytes(race);
			int raceNumber = race.getMatchday().getRaces().indexOf(race) + 1;
			String filename = "race-result-" + race.getMatchday().getLabel() + "-race-" + raceNumber + ".png";
			NamedAttachment attachment = new NamedAttachment(filename, png);
			return postOrEdit(
					creds.id(),
					webhookUrl,
					DiscordPostType.RACE_RESULTS,
					WebhookPayload.empty(),
					List.of(attachment),
					DiscordPostRef.race(race),
					threadId);
		} catch (IOException e) {
			throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
		}
	}

	@Transactional
	public DiscordPost postTeamCards(Match match) throws DiscordApiException {
		SeasonTeam home = resolveSeasonTeam(match, match.getHomeTeam().getId());
		SeasonTeam away = resolveSeasonTeam(match, match.getAwayTeam().getId());
		ensureCardOnDisk(home);
		ensureCardOnDisk(away);
		try {
			NamedAttachment homeAtt = new NamedAttachment(
					"team-card-home.png", readPng(teamCardService.getCardPath(home)));
			NamedAttachment awayAtt = new NamedAttachment(
					"team-card-away.png", readPng(teamCardService.getCardPath(away)));
			return postOrEdit(
					match.getDiscordChannelId(),
					match.getDiscordChannelWebhookUrl(),
					DiscordPostType.TEAM_CARDS,
					WebhookPayload.empty(),
					List.of(homeAtt, awayAtt),
					DiscordPostRef.match(match));
		} catch (IOException e) {
			throw new DiscordTransientException(DiscordApiExceptionMapper.TRANSIENT_MESSAGE, e);
		}
	}

	private SeasonTeam resolveSeasonTeam(Match match, java.util.UUID teamId) {
		return seasonTeamRepository
				.findBySeasonIdAndTeamId(match.getMatchday().getSeason().getId(), teamId)
				.orElseThrow(() -> new IllegalStateException(
						"SeasonTeam missing for season " + match.getMatchday().getSeason().getId()
								+ " and team " + teamId));
	}

	private void ensureCardOnDisk(SeasonTeam seasonTeam) {
		if (teamCardService.cardExists(seasonTeam)) {
			return;
		}
		try {
			teamCardService.generateCard(seasonTeam);
		} catch (IOException e) {
			throw new IllegalStateException(
					"Failed to generate team card for SeasonTeam " + seasonTeam.getId(), e);
		}
	}

	private byte[] readPng(String uploadsUrl) throws IOException {
		if (uploadsUrl == null || !uploadsUrl.startsWith(UPLOADS_PREFIX)) {
			throw new SecurityException("Team card URL must start with /uploads/: " + uploadsUrl);
		}
		Path file = uploadDir.resolve(uploadsUrl.substring(UPLOADS_PREFIX.length())).normalize();
		if (!file.startsWith(uploadDir)) {
			throw new SecurityException("Path traversal attempt in team card URL: " + uploadsUrl);
		}
		return Files.readAllBytes(file);
	}

	static WebhookCredentials parseWebhookUrl(String webhookUrl) {
		Matcher matcher = WEBHOOK_URL_PATTERN.matcher(webhookUrl);
		if (!matcher.matches()) {
			throw new IllegalArgumentException(
					"Discord webhook URL does not match expected shape: " + webhookUrl);
		}
		return new WebhookCredentials(matcher.group(1), matcher.group(2));
	}

	record WebhookCredentials(String id, String token) {
	}
}
