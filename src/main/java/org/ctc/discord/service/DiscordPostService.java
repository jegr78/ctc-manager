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
import org.ctc.admin.service.LineupGraphicService;
import org.ctc.admin.service.MatchResultsGraphicService;
import org.ctc.admin.service.ProvisionalScoresGraphicService;
import org.ctc.admin.service.ResultsGraphicService;
import org.ctc.admin.service.SettingsGraphicService;
import org.ctc.admin.service.TeamCardService;
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
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
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
	private final Path uploadDir;

	@SuppressFBWarnings(
			value = "EI_EXPOSE_REP2",
			justification = "Spring-managed singleton beans (DiscordWebhookClient, DiscordRestClient, DiscordPostRepository, "
					+ "DiscordHostValidator, DiscordGlobalConfigService, TeamCardService, SeasonTeamRepository, "
					+ "SettingsGraphicService, LineupGraphicService, RaceLineupRepository, MatchResultsGraphicService, "
					+ "ResultsGraphicService, ProvisionalScoresGraphicService, DiscordTimestamps) are intentionally shared "
					+ "by-reference — defensive copying would break framework wiring. Matches the implicit suppression "
					+ "that lombok.config adds to @RequiredArgsConstructor (see CLAUDE.md SpotBugs section + "
					+ "lombok.config invariant).")
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

	private WebhookPayload buildSchedulePayload(Match match, LocalDateTime firstRaceTime) {
		String dateField = discordTimestamps.longDateTime(firstRaceTime)
				+ " (" + discordTimestamps.relative(firstRaceTime) + ")";
		List<EmbedField> fields = List.of(
				new EmbedField("Date", dateField, true),
				new EmbedField("Lobby Host", orTbd(match.getLobbyHost()), true),
				new EmbedField("Race Director", orTbd(match.getRaceDirector()), true),
				new EmbedField("Streamer", streamerField(match), true));
		Embed embed = new Embed("Match Schedule", null, fields);
		return new WebhookPayload(null, List.of(embed));
	}

	private static String streamerField(Match match) {
		String name = match.getStreamer();
		String link = match.getStreamLink();
		boolean hasName = name != null && !name.isBlank();
		boolean hasLink = link != null && !link.isBlank();
		if (hasName && hasLink) {
			return "[" + name + "](" + link + ")";
		}
		if (hasLink) {
			return "[Watch Stream](" + link + ")";
		}
		if (hasName) {
			return name;
		}
		return "_TBD_";
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
			case DiscordPostRef.SeasonRef s ->
					discordPostRepository.findByChannelIdAndPostTypeAndSeasonId(channelId, type, s.id());
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
