package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.TestHelper;
import org.ctc.discord.exception.DiscordApiExceptionMapper;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class DiscordChannelArchiveServiceWireMockIT {

	@RegisterExtension
	static WireMockExtension wm = WireMockExtension.newInstance()
			.options(options().dynamicPort())
			.build();

	@DynamicPropertySource
	static void overrideDiscordConfig(DynamicPropertyRegistry registry) {
		registry.add("app.discord.base-url", () -> wm.baseUrl() + "/api/v10");
		registry.add("app.discord.bot-token", () -> "test-bot-token");
		registry.add("app.discord.allowed-hosts", () -> "discord.com,localhost,127.0.0.1");
		registry.add("app.discord.rate-limit.jitter-ms", () -> "0");
		registry.add("app.discord.rate-limit.fivexx-backoff-ms", () -> "10,10,10");
	}

	@Autowired
	MockMvc mockMvc;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	TestHelper helper;

	@BeforeEach
	void resetWireMock() {
		wm.resetAll();
	}

	private Match seedMatchWithChannel(String suffix) {
		Season season = helper.createSeason("Archive Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-1-" + suffix, 0);
		Team home = helper.createTeam("Home " + suffix, "h" + suffix);
		Team away = helper.createTeam("Away " + suffix, "a" + suffix);
		Match match = helper.createMatch(md, home, away);
		match.setDiscordChannelId("c1");
		return matchRepository.save(match);
	}

	@Test
	void givenChannelExistsAndCategoryHasRoom_whenMoveToArchive_thenPatchInvokedWithParentId() throws Exception {
		// given
		Match match = seedMatchWithChannel("H");
		wm.stubFor(patch(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(okJson("{\"id\":\"c1\",\"name\":\"md1-rs-h-vs-a\",\"type\":0,\"parent_id\":\"cat-archive-1\"}")));

		// when
		mockMvc.perform(post("/admin/matches/" + match.getId() + "/move-to-archive")
						.with(csrf())
						.param("categoryId", "cat-archive-1"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "Channel moved to archive."));

		// then
		wm.verify(patchRequestedFor(urlPathEqualTo("/api/v10/channels/c1"))
				.withRequestBody(matchingJsonPath("$.parent_id", equalTo("cat-archive-1")))
				.withRequestBody(notMatching(".*\"name\".*")));
		assertThat(matchRepository.findById(match.getId()).orElseThrow()
				.getDiscordChannelArchivedAt()).isNotNull();
	}

	@Test
	void givenChannelAlreadyArchived_whenRenderMatchDetail_thenMoveToArchiveButtonIsHidden() throws Exception {
		// given
		Match match = seedMatchWithChannel("A");
		wm.stubFor(patch(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(okJson("{\"id\":\"c1\",\"name\":\"md1-rs-h-vs-a\",\"type\":0,\"parent_id\":\"cat-archive-1\"}")));
		mockMvc.perform(post("/admin/matches/" + match.getId() + "/move-to-archive")
						.with(csrf())
						.param("categoryId", "cat-archive-1"))
				.andExpect(status().is3xxRedirection());

		// when / then — re-render the page; archive trigger is gone, archive badge is present
		mockMvc.perform(get("/admin/matches/" + match.getId()))
				.andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("data-testid=\"open-archive-modal\""))))
				.andExpect(content().string(
						org.hamcrest.Matchers.containsString("data-testid=\"discord-channel-archived\"")));
	}

	@Test
	void givenChannelDoesNotExist_whenMoveToArchive_thenDiscordNotFoundExceptionMapped() throws Exception {
		// given
		Match match = seedMatchWithChannel("N");
		wm.stubFor(patch(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(aResponse().withStatus(404)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"message\":\"Unknown Channel\",\"code\":10003}")));

		// when / then
		mockMvc.perform(post("/admin/matches/" + match.getId() + "/move-to-archive")
						.with(csrf())
						.param("categoryId", "cat-archive-1"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "not-found"))
				.andExpect(flash().attribute("errorMessage", DiscordApiExceptionMapper.NOT_FOUND_MESSAGE));
	}

	@Test
	void givenCategoryFullResponseFromDiscord_whenMoveToArchive_thenDiscordCategoryFullExceptionMapped() throws Exception {
		// given — Discord sentinel code 30013 = max channels in category reached
		Match match = seedMatchWithChannel("F");
		wm.stubFor(patch(urlPathEqualTo("/api/v10/channels/c1"))
				.willReturn(aResponse().withStatus(400)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"code\":30013,\"message\":\"Max channels reached\"}")));

		// when / then
		mockMvc.perform(post("/admin/matches/" + match.getId() + "/move-to-archive")
						.with(csrf())
						.param("categoryId", "cat-archive-full"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("errorCategory", "category-full"))
				.andExpect(flash().attribute("errorMessage", DiscordApiExceptionMapper.CATEGORY_FULL_MESSAGE));
	}
}
