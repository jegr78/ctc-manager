package org.ctc.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.ctc.discord.DiscordRestClient;
import org.ctc.discord.dto.ChannelModifyRequest;
import org.ctc.discord.exception.DiscordApiException.Category;
import org.ctc.discord.service.DiscordChannelService;
import org.ctc.domain.model.Match;
import org.ctc.domain.service.MatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

class MatchControllerMoveToArchiveErrorCategoryTest {

	@ParameterizedTest
	@CsvSource({
			"TRANSIENT,transient",
			"AUTH,auth",
			"NOT_FOUND,not-found",
			"CATEGORY_FULL,category-full"
	})
	void givenCategoryEnum_whenLowercaseAndHyphenated_thenMatchesBemClassSuffix(
			Category category, String expected) {
		String actual = category.name().toLowerCase().replace('_', '-');
		assertThat(actual).isEqualTo(expected);
	}

	@Test
	void givenBlankCategoryId_whenMoveToArchive_thenDataIncompleteFlashedAndNoOutbound() throws Exception {
		// given
		MatchService matchService = mock(MatchService.class);
		DiscordChannelService channelService = mock(DiscordChannelService.class);
		DiscordRestClient restClient = mock(DiscordRestClient.class);
		RedirectAttributes ra = mock(RedirectAttributes.class);
		UUID id = UUID.randomUUID();

		MatchController controller = new MatchController(matchService, channelService, restClient,
				mock(org.ctc.discord.service.DiscordPostService.class),
				mock(org.ctc.admin.service.TeamCardService.class),
				mock(org.ctc.domain.repository.SeasonTeamRepository.class));

		// when
		String view = invokeMoveToArchive(controller, id, "  ", ra);

		// then — distinct data-incomplete flash, no outbound PATCH, no MatchService lookup
		assertThat(view).isEqualTo("redirect:/admin/matches/" + id);
		verify(ra).addFlashAttribute("errorMessage",
				"Select an archive category before confirming the move.");
		verify(ra).addFlashAttribute("errorCategory", "data-incomplete");
		verify(restClient, never()).modifyChannel(anyString(), any(ChannelModifyRequest.class));
	}

	private static String invokeMoveToArchive(MatchController controller, UUID id,
			String categoryId, RedirectAttributes ra) {
		try {
			return controller.moveToArchive(id, categoryId, ra);
		} catch (Exception e) {
			throw new AssertionError("Controller should handle exceptions internally", e);
		}
	}

	@Test
	void givenMatchWithoutChannel_whenMoveToArchive_thenNotFoundFlash() throws Exception {
		// given
		MatchService matchService = mock(MatchService.class);
		DiscordChannelService channelService = mock(DiscordChannelService.class);
		DiscordRestClient restClient = mock(DiscordRestClient.class);
		RedirectAttributes ra = mock(RedirectAttributes.class);
		UUID id = UUID.randomUUID();
		when(matchService.findById(id)).thenReturn(new Match());

		MatchController controller = new MatchController(matchService, channelService, restClient,
				mock(org.ctc.discord.service.DiscordPostService.class),
				mock(org.ctc.admin.service.TeamCardService.class),
				mock(org.ctc.domain.repository.SeasonTeamRepository.class));

		// when
		String view = invokeMoveToArchive(controller, id, "cat-1", ra);

		// then
		assertThat(view).isEqualTo("redirect:/admin/matches/" + id);
		verify(ra).addFlashAttribute("errorMessage", "Match has no Discord channel to archive.");
		verify(ra).addFlashAttribute("errorCategory", "not-found");
		verify(restClient, never()).modifyChannel(anyString(), any(ChannelModifyRequest.class));
	}
}
