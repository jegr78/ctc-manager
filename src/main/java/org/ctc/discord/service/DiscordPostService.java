package org.ctc.discord.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.ctc.admin.service.TeamCardService;
import org.ctc.discord.DiscordHostValidator;
import org.ctc.discord.DiscordWebhookClient;
import org.ctc.discord.dto.DiscordPostRef;
import org.ctc.discord.dto.NamedAttachment;
import org.ctc.discord.dto.WebhookMessage;
import org.ctc.discord.dto.WebhookPayload;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.repository.SeasonTeamRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class DiscordPostService {

	private static final Pattern WEBHOOK_URL_PATTERN =
			Pattern.compile("^https?://[^/]+(?:/api/v\\d+)?/webhooks/(\\d+)/([^/?]+)(?:\\?.*)?$");

	private static final String UPLOADS_PREFIX = "/uploads/";

	private final DiscordWebhookClient webhookClient;
	private final DiscordPostRepository discordPostRepository;
	private final DiscordHostValidator hostValidator;
	private final Clock clock;
	private final TeamCardService teamCardService;
	private final SeasonTeamRepository seasonTeamRepository;
	private final Path uploadDir;

	@SuppressFBWarnings(
			value = "EI_EXPOSE_REP2",
			justification = "Spring-managed singleton beans (DiscordWebhookClient, DiscordPostRepository, DiscordHostValidator, "
					+ "TeamCardService, SeasonTeamRepository) are intentionally shared by-reference — defensive copying "
					+ "would break framework wiring. Matches the implicit suppression that lombok.config adds to "
					+ "@RequiredArgsConstructor (see CLAUDE.md SpotBugs section + lombok.config invariant).")
	public DiscordPostService(
			DiscordWebhookClient webhookClient,
			DiscordPostRepository discordPostRepository,
			DiscordHostValidator hostValidator,
			Clock clock,
			TeamCardService teamCardService,
			SeasonTeamRepository seasonTeamRepository,
			@Value("${app.upload-dir:uploads}") String uploadDir) {
		this.webhookClient = webhookClient;
		this.discordPostRepository = discordPostRepository;
		this.hostValidator = hostValidator;
		this.clock = clock;
		this.teamCardService = teamCardService;
		this.seasonTeamRepository = seasonTeamRepository;
		this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
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
		hostValidator.requireAllowed(webhookUrl);
		if (!(ref instanceof DiscordPostRef.MatchRef)) {
			throw new UnsupportedOperationException(
					"DiscordPostService.postOrEdit currently supports MatchRef only (Phase 96/97 wires the other permits); got "
							+ ref.getClass().getSimpleName());
		}
		WebhookCredentials creds = parseWebhookUrl(webhookUrl);
		Optional<DiscordPost> existing = discordPostRepository
				.findByChannelIdAndPostTypeAndMatchId(channelId, type, ref.matchId());
		LocalDateTime now = LocalDateTime.now(clock);

		if (existing.isPresent()) {
			DiscordPost row = existing.get();
			if (attachments.isEmpty()) {
				webhookClient.editMessage(webhookUrl, row.getMessageId(), payload);
			} else {
				webhookClient.editMessageWithAttachments(webhookUrl, row.getMessageId(), payload, attachments);
				row.setAttachmentsReplacedAt(now);
			}
			DiscordPost saved = discordPostRepository.save(row);
			log.info("Edited {} for match {} (messageId={})", type, ref.matchId(), saved.getMessageId());
			return saved;
		}

		WebhookMessage msg = attachments.isEmpty()
				? webhookClient.execute(webhookUrl, payload)
				: webhookClient.executeMultipart(webhookUrl, payload, attachments);
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
		log.info("Posted {} for match {} (messageId={})", type, ref.matchId(), saved.getMessageId());
		return saved;
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

	private static WebhookCredentials parseWebhookUrl(String webhookUrl) {
		Matcher matcher = WEBHOOK_URL_PATTERN.matcher(webhookUrl);
		if (!matcher.matches()) {
			throw new IllegalArgumentException(
					"Discord webhook URL does not match expected shape: " + webhookUrl);
		}
		return new WebhookCredentials(matcher.group(1), matcher.group(2));
	}

	private record WebhookCredentials(String id, String token) {
	}
}
