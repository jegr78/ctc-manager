package org.ctc.discord.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.DiscordHostValidator;
import org.ctc.discord.DiscordWebhookClient;
import org.ctc.discord.dto.DiscordPostRef;
import org.ctc.discord.dto.NamedAttachment;
import org.ctc.discord.dto.WebhookMessage;
import org.ctc.discord.dto.WebhookPayload;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
@Slf4j
public class DiscordPostService {

	private static final Pattern WEBHOOK_URL_PATTERN =
			Pattern.compile("^https://discord\\.com/api/webhooks/(\\d+)/([^/]+)$");

	private final DiscordWebhookClient webhookClient;
	private final DiscordPostRepository discordPostRepository;
	private final DiscordHostValidator hostValidator;
	private final Clock clock;

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
