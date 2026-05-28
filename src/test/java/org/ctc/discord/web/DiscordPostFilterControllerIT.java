package org.ctc.discord.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class DiscordPostFilterControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DiscordPostRepository discordPostRepository;

	@Autowired
	private TestHelper helper;

	private UUID seasonAId;

	@BeforeEach
	void seedRows() {
		discordPostRepository.deleteAll();

		Season seasonA = helper.createSeason("Filter Season A");
		seasonAId = seasonA.getId();
		Matchday mdA = helper.createMatchdayInRegularPhase(seasonA, "MD-A-1", 0);
		Team homeA = helper.createTeam("Home FA", "fa-h");
		Team awayA = helper.createTeam("Away FA", "fa-a");
		Match matchA = helper.createMatch(mdA, homeA, awayA);

		Season seasonB = helper.createSeason("Filter Season B");
		Matchday mdB = helper.createMatchdayInRegularPhase(seasonB, "MD-B-1", 0);
		Team homeB = helper.createTeam("Home FB", "fb-h");
		Team awayB = helper.createTeam("Away FB", "fb-a");
		Match matchB = helper.createMatch(mdB, homeB, awayB);

		discordPostRepository.save(buildPost("chan-1", "msg-1", DiscordPostType.TEAM_CARDS,
				matchA.getId(), seasonA.getId()));
		discordPostRepository.save(buildPost("chan-1", "msg-2", DiscordPostType.SCHEDULE,
				matchA.getId(), seasonA.getId()));
		discordPostRepository.save(buildPost("chan-2", "msg-3", DiscordPostType.TEAM_CARDS,
				matchB.getId(), seasonB.getId()));
	}

	@AfterEach
	void cleanup() {
		discordPostRepository.deleteAll();
	}

	private static DiscordPost buildPost(
			String channelId, String messageId, DiscordPostType type, UUID matchId, UUID seasonId) {
		DiscordPost p = new DiscordPost();
		p.setChannelId(channelId);
		p.setMessageId(messageId);
		p.setWebhookId("100");
		p.setWebhookToken("tok-" + messageId);
		p.setPostType(type);
		p.setMatchId(matchId);
		p.setSeasonId(seasonId);
		p.setPostedAt(LocalDateTime.now());
		return p;
	}

	@Test
	void givenSeededRows_whenGetListWithoutFilter_thenAllPostsAndModelAttributesPresent() throws Exception {
		mockMvc.perform(get("/admin/discord/posts"))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/discord-posts"))
				.andExpect(model().attributeExists("posts"))
				.andExpect(model().attributeExists("seasons"))
				.andExpect(model().attributeExists("postTypes"))
				.andExpect(model().attributeExists("filter"))
				.andExpect(model().attribute("activeRoute", "discord-posts"))
				.andExpect(model().attribute("posts",
						Matchers.hasProperty("totalElements", Matchers.greaterThanOrEqualTo(3L))));
	}

	@Test
	void givenSeededRows_whenFilterByPostType_thenOnlyMatchingRowsReturned() throws Exception {
		mockMvc.perform(get("/admin/discord/posts").param("postType", "SCHEDULE"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("posts",
						Matchers.hasProperty("totalElements", Matchers.equalTo(1L))));
	}

	@Test
	void givenSeededRows_whenFilterBySeasonAndPostType_thenAndPredicateApplied() throws Exception {
		mockMvc.perform(get("/admin/discord/posts")
						.param("seasonId", seasonAId.toString())
						.param("postType", "TEAM_CARDS"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("posts",
						Matchers.hasProperty("totalElements", Matchers.equalTo(1L))));
	}

	@Test
	void givenEmptyTable_whenGetList_thenPostsPageIsEmpty() throws Exception {
		discordPostRepository.deleteAll();

		mockMvc.perform(get("/admin/discord/posts"))
				.andExpect(status().isOk())
				.andExpect(model().attribute("posts",
						Matchers.hasProperty("totalElements", Matchers.equalTo(0L))));
	}
}
