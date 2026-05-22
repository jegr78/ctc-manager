package org.ctc.discord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.dto.ArchiveCategory;
import org.ctc.discord.dto.Channel;
import org.ctc.discord.exception.DiscordApiException;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DiscordCategoryResolverTest {

	@Mock
	private DiscordRestClient restClient;

	@Mock
	private DiscordGlobalConfigService configService;

	@InjectMocks
	private DiscordCategoryResolver resolver;

	private DiscordGlobalConfig cfg;

	@BeforeEach
	void setUp() {
		cfg = new DiscordGlobalConfig();
		cfg.setGuildId("g1");
	}

	private static Channel category(String id, String name) {
		return new Channel(id, name, 4, null, null);
	}

	@Test
	void givenCategoryNameWithoutSuffix_whenMatched_thenNumDefaultsToOne() throws DiscordApiException {
		// given
		when(configService.getOrInitialize()).thenReturn(cfg);
		when(restClient.listChannels(anyString())).thenReturn(List.of(
				category("cat-1", "Match Days Archive 2026")));

		// when
		List<ArchiveCategory> result = resolver.resolveArchiveCategoriesFor(2026);

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).num()).isEqualTo(1);
		assertThat(result.get(0).currentChannelCount()).isZero();
	}

	@Test
	void givenCategoryNameWithSuffix_whenMatched_thenNumExtracted() throws DiscordApiException {
		// given
		when(configService.getOrInitialize()).thenReturn(cfg);
		when(restClient.listChannels(anyString())).thenReturn(List.of(
				category("cat-2", "Match Days Archive 2026 (7)")));

		// when
		List<ArchiveCategory> result = resolver.resolveArchiveCategoriesFor(2026);

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).num()).isEqualTo(7);
	}

	@Test
	void givenYearMismatch_whenFiltered_thenExcluded() throws DiscordApiException {
		// given
		when(configService.getOrInitialize()).thenReturn(cfg);
		when(restClient.listChannels(anyString())).thenReturn(List.of(
				category("cat-2025", "Match Days Archive 2025"),
				category("cat-2026", "Match Days Archive 2026")));

		// when
		List<ArchiveCategory> result = resolver.resolveArchiveCategoriesFor(2026);

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).id()).isEqualTo("cat-2026");
	}

	@Test
	void givenMixedNums_whenResolverInvoked_thenSortedAscendingByNum() throws DiscordApiException {
		// given
		when(configService.getOrInitialize()).thenReturn(cfg);
		when(restClient.listChannels(anyString())).thenReturn(List.of(
				category("cat-c", "Match Days Archive 2026 (3)"),
				category("cat-a", "Match Days Archive 2026"),
				category("cat-b", "Match Days Archive 2026 (2)")));

		// when
		List<ArchiveCategory> result = resolver.resolveArchiveCategoriesFor(2026);

		// then
		assertThat(result).extracting(ArchiveCategory::num).containsExactly(1, 2, 3);
	}

	@Test
	void givenAllCategoriesFull_whenDefaultSelection_thenEmpty() {
		List<ArchiveCategory> all = List.of(
				new ArchiveCategory("a", "Match Days Archive 2026", 1, 50),
				new ArchiveCategory("b", "Match Days Archive 2026 (2)", 2, 50));

		Optional<ArchiveCategory> result = resolver.defaultSelection(all);

		assertThat(result).isEmpty();
	}

	@Test
	void givenSomeWithRoom_whenDefaultSelection_thenHighestNumWithRoom() {
		List<ArchiveCategory> all = List.of(
				new ArchiveCategory("a", "Match Days Archive 2026", 1, 49),
				new ArchiveCategory("b", "Match Days Archive 2026 (2)", 2, 30),
				new ArchiveCategory("c", "Match Days Archive 2026 (3)", 3, 50));

		Optional<ArchiveCategory> result = resolver.defaultSelection(all);

		assertThat(result).isPresent();
		assertThat(result.get().id()).isEqualTo("b");
	}
}
