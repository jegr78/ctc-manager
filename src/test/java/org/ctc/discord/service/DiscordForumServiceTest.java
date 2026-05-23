package org.ctc.discord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.dto.Thread;
import org.ctc.discord.dto.ThreadMetadata;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DiscordForumServiceTest {

	private static final String FORUM_ID = "forum-1";
	private static final String GUILD_ID = "111111111111111111";

	private DiscordRestClient restClient;
	private DiscordGlobalConfigService configService;
	private DiscordForumService service;

	@BeforeEach
	void setUp() {
		restClient = mock(DiscordRestClient.class);
		configService = mock(DiscordGlobalConfigService.class);
		DiscordGlobalConfig config = new DiscordGlobalConfig();
		config.setGuildId(GUILD_ID);
		when(configService.getOrInitialize()).thenReturn(config);
		service = new DiscordForumService(restClient, configService);
	}

	@Test
	void givenPinnedFlag_whenCallingPinnedHelper_thenReturnsTrue() {
		Thread pinned = new Thread("1", "name", FORUM_ID, Thread.FLAG_PINNED, null, "100");
		Thread notPinned = new Thread("2", "name", FORUM_ID, 0, null, "100");

		assertThat(pinned.pinned()).isTrue();
		assertThat(notPinned.pinned()).isFalse();
	}

	@Test
	void givenArchivedMetadata_whenCallingArchivedHelper_thenReflectsState() {
		Thread archived = new Thread("1", "n", FORUM_ID, 0, new ThreadMetadata(true), "1");
		Thread notArchived = new Thread("2", "n", FORUM_ID, 0, new ThreadMetadata(false), "1");
		Thread noMetadata = new Thread("3", "n", FORUM_ID, 0, null, "1");

		assertThat(archived.archived()).isTrue();
		assertThat(notArchived.archived()).isFalse();
		assertThat(noMetadata.archived()).isFalse();
	}

	@Test
	void givenMixedThreads_whenListThreads_thenSortsPinnedFirstThenActiveByLastMessageThenArchived() throws DiscordApiException {
		Thread pinned = thread("1", "pinned", FORUM_ID, Thread.FLAG_PINNED, null, "500");
		Thread active1 = thread("2", "active-newest", FORUM_ID, 0, null, "400");
		Thread active2 = thread("3", "active-oldest", FORUM_ID, 0, null, "200");
		Thread archived1 = thread("4", "archived-newest", FORUM_ID, 0, new ThreadMetadata(true), "300");
		Thread archived2 = thread("5", "archived-oldest", FORUM_ID, 0, new ThreadMetadata(true), "100");

		when(restClient.listActiveThreads(GUILD_ID)).thenReturn(List.of(pinned, active1, active2));
		when(restClient.listArchivedThreads(FORUM_ID)).thenReturn(List.of(archived1, archived2));

		List<Thread> result = service.listThreads(FORUM_ID);

		assertThat(result).extracting(Thread::id).containsExactly("1", "2", "3", "4", "5");
	}

	@Test
	void givenActiveThreadsFromDifferentForums_whenListThreads_thenFiltersByParentId() throws DiscordApiException {
		Thread target1 = thread("t1", "n", FORUM_ID, 0, null, "300");
		Thread target2 = thread("t2", "n", FORUM_ID, 0, null, "200");
		Thread other = thread("o1", "n", "other-forum", 0, null, "999");

		when(restClient.listActiveThreads(GUILD_ID)).thenReturn(List.of(target1, target2, other));
		when(restClient.listArchivedThreads(FORUM_ID)).thenReturn(List.of());

		List<Thread> result = service.listThreads(FORUM_ID);

		assertThat(result).extracting(Thread::id).containsExactlyInAnyOrder("t1", "t2");
	}

	@Test
	void givenEmptyForum_whenListThreads_thenReturnsEmptyList() throws DiscordApiException {
		when(restClient.listActiveThreads(GUILD_ID)).thenReturn(List.of());
		when(restClient.listArchivedThreads(FORUM_ID)).thenReturn(List.of());

		List<Thread> result = service.listThreads(FORUM_ID);

		assertThat(result).isEmpty();
	}

	@Test
	void givenAllFlagsZeroAndNullLastMessageIds_whenListThreads_thenSortsWithoutNpe() throws DiscordApiException {
		Thread active = thread("a", "n", FORUM_ID, 0, null, null);
		Thread archived = thread("b", "n", FORUM_ID, 0, new ThreadMetadata(true), null);

		when(restClient.listActiveThreads(GUILD_ID)).thenReturn(List.of(active));
		when(restClient.listArchivedThreads(FORUM_ID)).thenReturn(List.of(archived));

		List<Thread> result = service.listThreads(FORUM_ID);

		assertThat(result).extracting(Thread::id).containsExactly("a", "b");
	}

	@Test
	void givenBlankGuildId_whenListThreads_thenReturnsEmptyWithoutCallingRestClient() throws DiscordApiException {
		DiscordGlobalConfig config = new DiscordGlobalConfig();
		config.setGuildId("");
		when(configService.getOrInitialize()).thenReturn(config);

		List<Thread> result = service.listThreads(FORUM_ID);

		assertThat(result).isEmpty();
	}

	private static Thread thread(String id, String name, String parentId, int flags, ThreadMetadata md, String lastMsg) {
		return new Thread(id, name, parentId, flags, md, lastMsg);
	}
}
