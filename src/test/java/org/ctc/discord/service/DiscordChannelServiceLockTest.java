package org.ctc.discord.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.PessimisticLockException;
import java.sql.SQLException;
import java.util.UUID;
import org.ctc.discord.DiscordBotIdentityCache;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.exception.DiscordChannelBusyException;
import org.ctc.domain.exception.EntityNotFoundException;
import org.ctc.domain.model.Match;
import org.ctc.domain.repository.MatchRepository;
import org.hibernate.exception.LockAcquisitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class DiscordChannelServiceLockTest {

	private static final String BUSY =
			"Another request is changing this match's Discord channel. Reload the page and try again.";

	private final DiscordRestClient restClient = mock(DiscordRestClient.class);
	private final EntityManager entityManager = mock(EntityManager.class);
	private final DiscordChannelService service = new DiscordChannelService(restClient,
			mock(DiscordGlobalConfigService.class), mock(DiscordBotIdentityCache.class),
			mock(MatchRepository.class), mock(ApplicationEventPublisher.class), entityManager);
	private final Match match = new Match();

	@BeforeEach
	void setUp() {
		match.setId(UUID.randomUUID());
		when(entityManager.find(Match.class, match.getId())).thenReturn(match);
	}

	@Test
	void givenMatchLockedByAnotherRequest_whenChannelCreated_thenBusyMessageAndNoDiscordCall() {
		// given
		doThrow(new LockTimeoutException("timeout")).when(entityManager)
				.refresh(eq(match), eq(LockModeType.PESSIMISTIC_WRITE));

		// when / then
		assertThatThrownBy(() -> service.createMatchChannel(match))
				.isInstanceOf(DiscordChannelBusyException.class)
				.hasMessage(BUSY);
		verifyNoInteractions(restClient);
	}

	@Test
	void givenMatchLockedByAnotherRequest_whenChannelLinked_thenBusyMessageAndNoDiscordCall() {
		// given
		doThrow(new PessimisticLockException("locked")).when(entityManager)
				.refresh(eq(match), eq(LockModeType.PESSIMISTIC_WRITE));

		// when / then
		assertThatThrownBy(() -> service.linkExistingChannel(match, "c1"))
				.isInstanceOf(DiscordChannelBusyException.class)
				.hasMessage(BUSY);
		verifyNoInteractions(restClient);
	}

	@Test
	void givenDeadlockWhileLocking_whenChannelCreated_thenBusyMessageAndNoDiscordCall() {
		// given
		doThrow(new PersistenceException("deadlock",
				new LockAcquisitionException("deadlock", new SQLException("Deadlock found", "40001", 1213))))
				.when(entityManager).refresh(eq(match), eq(LockModeType.PESSIMISTIC_WRITE));

		// when / then
		assertThatThrownBy(() -> service.createMatchChannel(match))
				.isInstanceOf(DiscordChannelBusyException.class)
				.hasMessage(BUSY);
		verifyNoInteractions(restClient);
	}

	@Test
	void givenOtherPersistenceFailureWhileLocking_whenChannelCreated_thenItPropagates() {
		// given
		var failure = new PersistenceException("broken");
		doThrow(failure).when(entityManager).refresh(eq(match), eq(LockModeType.PESSIMISTIC_WRITE));

		// when / then
		assertThatThrownBy(() -> service.createMatchChannel(match)).isSameAs(failure);
	}

	@Test
	void givenMatchDeletedBeforeTheLock_whenChannelCreated_thenNotFound() {
		// given
		doThrow(new jakarta.persistence.EntityNotFoundException("gone")).when(entityManager)
				.refresh(any(Match.class), eq(LockModeType.PESSIMISTIC_WRITE));

		// when / then
		assertThatThrownBy(() -> service.createMatchChannel(match))
				.isInstanceOf(EntityNotFoundException.class)
				.hasMessage("Match not found with id: " + match.getId());
		verifyNoInteractions(restClient);
	}
}
