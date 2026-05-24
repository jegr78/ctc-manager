package org.ctc.domain.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.ctc.admin.dto.MatchForm;
import org.ctc.discord.event.MatchPreviewFieldsChangedEvent;
import org.ctc.discord.event.MatchScheduleFieldsChangedEvent;
import org.ctc.domain.model.Match;
import org.ctc.domain.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class MatchServicePreviewDiffPublishTest {

	@Mock
	private MatchRepository matchRepository;
	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private MatchService service;

	private UUID matchId;
	private Match existing;

	@BeforeEach
	void seed() {
		matchId = UUID.randomUUID();
		existing = new Match();
		existing.setId(matchId);
		existing.setDiscordTeaser("old teaser");
		existing.setStreamLink("https://twitch.tv/old");
		existing.setLobbyHost("Host1");
		existing.setRaceDirector("RD1");
		existing.setStreamer("Streamer1");

		when(matchRepository.findById(matchId)).thenReturn(Optional.of(existing));
		when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));
	}

	@Test
	void givenTeaserChangeOnly_whenUpdateDiscordFields_thenPreviewEventPublishedOnce() {
		// given
		MatchForm form = formCopy(existing);
		form.setDiscordTeaser("new teaser");

		// when
		service.updateDiscordFields(matchId, form);

		// then
		verify(eventPublisher, times(1)).publishEvent(any(MatchPreviewFieldsChangedEvent.class));
		verify(eventPublisher, never()).publishEvent(any(MatchScheduleFieldsChangedEvent.class));
	}

	@Test
	void givenStreamLinkChangeOnly_whenUpdateDiscordFields_thenPreviewEventPublishedOnce() {
		// given
		MatchForm form = formCopy(existing);
		form.setStreamLink("https://twitch.tv/new");

		// when
		service.updateDiscordFields(matchId, form);

		// then
		verify(eventPublisher, times(1)).publishEvent(any(MatchPreviewFieldsChangedEvent.class));
		verify(eventPublisher, never()).publishEvent(any(MatchScheduleFieldsChangedEvent.class));
	}

	@Test
	void givenTeaserAndStreamLinkChange_whenUpdateDiscordFields_thenPreviewEventPublishedExactlyOnce() {
		// given
		MatchForm form = formCopy(existing);
		form.setDiscordTeaser("new teaser");
		form.setStreamLink("https://twitch.tv/new");

		// when
		service.updateDiscordFields(matchId, form);

		// then
		verify(eventPublisher, times(1)).publishEvent(any(MatchPreviewFieldsChangedEvent.class));
	}

	@Test
	void givenIdenticalPreviewFields_whenUpdateDiscordFields_thenPreviewEventNeverPublished() {
		// given
		MatchForm form = formCopy(existing);

		// when
		service.updateDiscordFields(matchId, form);

		// then
		verify(eventPublisher, never()).publishEvent(any(MatchPreviewFieldsChangedEvent.class));
		verify(eventPublisher, never()).publishEvent(any(MatchScheduleFieldsChangedEvent.class));
	}

	@Test
	void givenTeaserAndLobbyHostChange_whenUpdateDiscordFields_thenBothEventsPublishedOnce() {
		// given
		MatchForm form = formCopy(existing);
		form.setDiscordTeaser("new teaser");
		form.setLobbyHost("Host2");

		// when
		service.updateDiscordFields(matchId, form);

		// then
		verify(eventPublisher, times(1)).publishEvent(any(MatchPreviewFieldsChangedEvent.class));
		verify(eventPublisher, times(1)).publishEvent(any(MatchScheduleFieldsChangedEvent.class));
	}

	@Test
	void givenBothFieldsNullToNull_whenUpdateDiscordFields_thenPreviewEventNeverPublished() {
		// given
		existing.setDiscordTeaser(null);
		existing.setStreamLink(null);
		MatchForm form = formCopy(existing);

		// when
		service.updateDiscordFields(matchId, form);

		// then
		verify(eventPublisher, never()).publishEvent(any(MatchPreviewFieldsChangedEvent.class));
	}

	private static MatchForm formCopy(Match match) {
		MatchForm form = new MatchForm();
		form.setId(match.getId());
		form.setDiscordTeaser(match.getDiscordTeaser());
		form.setStreamLink(match.getStreamLink());
		form.setLobbyHost(match.getLobbyHost());
		form.setRaceDirector(match.getRaceDirector());
		form.setStreamer(match.getStreamer());
		return form;
	}
}
