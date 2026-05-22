package org.ctc.discord.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.discord.event.ChannelCreatedEvent;
import org.ctc.discord.event.MatchScheduleFieldsChangedEvent;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.domain.model.Match;
import org.ctc.domain.repository.MatchRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscordAutoPostListener {

	private static final String AUTO_POST_ERROR_ATTRIBUTE = "discord.autoPostError";
	private static final String AUTO_EDIT_ERROR_ATTRIBUTE = "discord.autoEditError";

	private final DiscordPostService discordPostService;
	private final MatchRepository matchRepository;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onChannelCreated(ChannelCreatedEvent event) {
		Match match = matchRepository.findById(event.matchId()).orElse(null);
		if (match == null) {
			log.warn("Auto-post TEAM_CARDS skipped — match {} not found post-commit", event.matchId());
			return;
		}
		try {
			discordPostService.postTeamCards(match);
		} catch (DiscordApiException e) {
			String category = e.category().name().toLowerCase().replace('_', '-');
			log.warn("Auto-post TEAM_CARDS failed for match {}: category={}", event.matchId(), category);
			recordRequestAttribute(AUTO_POST_ERROR_ATTRIBUTE, category);
		} catch (RuntimeException e) {
			log.warn("Auto-post TEAM_CARDS failed for match {}: category=transient", event.matchId(), e);
			recordRequestAttribute(AUTO_POST_ERROR_ATTRIBUTE, "transient");
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void onScheduleFieldsChanged(MatchScheduleFieldsChangedEvent event) {
		Match match = matchRepository.findById(event.matchId()).orElse(null);
		if (match == null) {
			log.warn("Auto-edit SCHEDULE skipped — match {} not found post-commit", event.matchId());
			return;
		}
		try {
			discordPostService.autoEditScheduleIfNeeded(match);
		} catch (DiscordApiException e) {
			log.warn("Auto-edit SCHEDULE failed for match {}: category={}",
					event.matchId(), e.category().name());
			recordRequestAttribute(AUTO_EDIT_ERROR_ATTRIBUTE, e.category().name().toLowerCase().replace('_', '-'));
		} catch (RuntimeException e) {
			log.warn("Auto-edit SCHEDULE failed for match {}: {}", event.matchId(), e.toString());
			recordRequestAttribute(AUTO_EDIT_ERROR_ATTRIBUTE, "transient");
		}
	}

	private static void recordRequestAttribute(String name, String value) {
		try {
			RequestContextHolder.currentRequestAttributes()
					.setAttribute(name, value, RequestAttributes.SCOPE_REQUEST);
		} catch (IllegalStateException ignoredNoRequestBound) {
			// listener may fire outside an HTTP request (scheduled job, programmatic call) — log line already emitted
		}
	}
}
