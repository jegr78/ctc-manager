package org.ctc.discord.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.ctc.discord.event.ChannelCreatedEvent;
import org.ctc.discord.event.MatchPreviewFieldsChangedEvent;
import org.ctc.discord.event.MatchScheduleFieldsChangedEvent;
import org.ctc.domain.model.Match;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.integration.OutboundPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;

class DiscordAutoPostListenerPolicyTest {

	@Test
	void givenDisabledOutbound_whenCallbacksRun_thenNoPublicationWorkIsStarted() {
		// given
		try (var context = listenerContext(false)) {
			var listener = context.getBean(DiscordAutoPostListener.class);
			UUID matchId = UUID.fromString("5467cb0f-a793-4f9b-a7e4-10296d5c9dbc");
			var matches = context.getBean(MatchRepository.class);
			when(matches.findById(matchId)).thenReturn(Optional.of(new Match()));
			// when
			listener.onChannelCreated(new ChannelCreatedEvent(matchId));
			listener.onScheduleFieldsChanged(new MatchScheduleFieldsChangedEvent(matchId));
			listener.onMatchPreviewFieldsChanged(new MatchPreviewFieldsChangedEvent(matchId));
			// then
			verifyNoInteractions(context.getBean(DiscordPostService.class));
		}
	}

	@Test
	void givenEnabledOutbound_whenCallbacksRun_thenMatchingPublicationWorkIsStarted() throws Exception {
		// given
		try (var context = listenerContext(true)) {
			var listener = context.getBean(DiscordAutoPostListener.class);
			UUID matchId = UUID.fromString("5467cb0f-a793-4f9b-a7e4-10296d5c9dbc");
			var match = new Match();
			when(context.getBean(MatchRepository.class).findById(matchId)).thenReturn(Optional.of(match));
			// when
			listener.onChannelCreated(new ChannelCreatedEvent(matchId));
			listener.onScheduleFieldsChanged(new MatchScheduleFieldsChangedEvent(matchId));
			listener.onMatchPreviewFieldsChanged(new MatchPreviewFieldsChangedEvent(matchId));
			// then
			var posts = context.getBean(DiscordPostService.class);
			verify(posts).postTeamCards(match);
			verify(posts).autoEditScheduleIfNeeded(match);
			verify(posts).autoEditMatchPreviewIfNeeded(match);
		}
	}

	private static AnnotationConfigApplicationContext listenerContext(boolean enabled) {
		var context = new AnnotationConfigApplicationContext();
		context.registerBean(OutboundPolicy.class, () -> new OutboundPolicy(enabled, new MockEnvironment()));
		context.registerBean(MatchRepository.class, () -> mock(MatchRepository.class));
		context.registerBean(DiscordPostService.class, () -> mock(DiscordPostService.class));
		context.registerBean(DiscordAutoPostListener.class);
		context.refresh();
		return context;
	}
}
