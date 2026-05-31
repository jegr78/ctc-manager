package org.ctc.admin.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.ctc.TestHelper;
import org.ctc.TestHelper.SeasonFixture;
import org.ctc.discord.exception.DiscordNotFoundException;
import org.ctc.discord.service.DiscordChannelService;
import org.ctc.domain.model.Match;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class MatchControllerLinkChannelTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TestHelper testHelper;

	@MockitoBean
	private DiscordChannelService discordChannelService;

	private SeasonFixture fixture;

	@BeforeEach
	void setUp() {
		fixture = testHelper.createFullSeasonFixture("Test_Link");
	}

	@Test
	void givenBlankChannelId_whenLinkDiscordChannel_thenErrorFlashAndServiceNotCalled() throws Exception {
		// given
		var matchId = fixture.match().getId();

		// when
		mockMvc.perform(post("/admin/matches/" + matchId + "/link-discord-channel")
						.param("channelId", ""))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/matches/" + matchId))
				.andExpect(flash().attributeExists("errorMessage"));

		verify(discordChannelService, never()).linkExistingChannel(any(), any());
	}

	@Test
	void givenValidChannelId_whenLinkDiscordChannel_thenDelegatesTrimmedAndSuccessFlash() throws Exception {
		// given
		var matchId = fixture.match().getId();

		// when
		mockMvc.perform(post("/admin/matches/" + matchId + "/link-discord-channel")
						.param("channelId", " c123 "))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/matches/" + matchId))
				.andExpect(flash().attributeExists("successMessage"));

		verify(discordChannelService).linkExistingChannel(any(Match.class), eq("c123"));
	}

	@Test
	void givenChannelNotFound_whenLinkDiscordChannel_thenErrorCategoryNotFound() throws Exception {
		// given
		var matchId = fixture.match().getId();
		doThrow(new DiscordNotFoundException("nope", null))
				.when(discordChannelService).linkExistingChannel(any(), any());

		// when
		mockMvc.perform(post("/admin/matches/" + matchId + "/link-discord-channel")
						.param("channelId", "c404"))
				// then
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/admin/matches/" + matchId))
				.andExpect(flash().attributeExists("errorMessage"))
				.andExpect(flash().attribute("errorCategory", "not-found"));
	}
}
