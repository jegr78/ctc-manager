package org.ctc.discord.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDateTime;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.discord.dto.DiscordPostRef;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
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
	private UUID seasonBId;
	private UUID matchAId;

	@BeforeEach
	void seedRows() {
		discordPostRepository.deleteAll();

		Season seasonA = helper.createSeason("Test_Filter Season A");
		seasonAId = seasonA.getId();
		Matchday mdA = helper.createMatchdayInRegularPhase(seasonA, "Test_MD-A-1", 0);
		Team homeA = helper.createTeam("Test Home FA", "Test_FAH");
		Team awayA = helper.createTeam("Test Away FA", "Test_FAA");
		Match matchA = helper.createMatch(mdA, homeA, awayA);
		matchAId = matchA.getId();
		Race raceA = helper.createRace(mdA, matchA);

		Season seasonB = helper.createSeason("Test_Filter Season B");
		seasonBId = seasonB.getId();
		Matchday mdB = helper.createMatchdayInRegularPhase(seasonB, "Test_MD-B-1", 0);
		Team homeB = helper.createTeam("Test Home FB", "Test_FBH");
		Team awayB = helper.createTeam("Test Away FB", "Test_FBA");
		Match matchB = helper.createMatch(mdB, homeB, awayB);

		discordPostRepository.save(buildPost("msg-1", DiscordPostType.TEAM_CARDS, DiscordPostRef.match(matchA)));
		discordPostRepository.save(buildPost("msg-2", DiscordPostType.MATCHDAY_PAIRINGS, DiscordPostRef.matchday(mdA)));
		discordPostRepository.save(buildPost("msg-3", DiscordPostType.RACE_RESULTS, DiscordPostRef.race(raceA)));
		discordPostRepository.save(buildPost("msg-4", DiscordPostType.POWER_RANKINGS, DiscordPostRef.season(seasonA)));
		discordPostRepository.save(buildPost("msg-5", DiscordPostType.TEAM_CARDS, DiscordPostRef.match(matchB)));
	}

	@AfterEach
	void cleanup() {
		discordPostRepository.deleteAll();
	}

	private static DiscordPost buildPost(String messageId, DiscordPostType type, DiscordPostRef ref) {
		DiscordPost p = new DiscordPost();
		p.setChannelId("chan-1");
		p.setMessageId(messageId);
		p.setWebhookId("100");
		p.setWebhookToken("tok-" + messageId);
		p.setPostType(type);
		p.setPostedAt(LocalDateTime.now());
		ref.applyTo(p);
		return p;
	}

	@Test
	void givenMatchMatchdayRaceAndSeasonPosts_whenFilteredBySeason_thenAllPostsOfThatSeasonAreListed() throws Exception {
		mockMvc.perform(get("/admin/discord/posts").param("seasonId", seasonAId.toString()))
				.andExpect(status().isOk())
				.andExpect(model().attribute("posts",
						Matchers.hasProperty("totalElements", Matchers.equalTo(4L))));
		mockMvc.perform(get("/admin/discord/posts").param("seasonId", seasonBId.toString()))
				.andExpect(model().attribute("posts",
						Matchers.hasProperty("totalElements", Matchers.equalTo(1L))));
	}

	@Test
	void givenSeededRows_whenFilteredBySeasonAndMatch_thenOnlyThatMatchsPost() throws Exception {
		mockMvc.perform(get("/admin/discord/posts")
						.param("seasonId", seasonAId.toString())
						.param("matchId", matchAId.toString()))
				.andExpect(status().isOk())
				.andExpect(model().attribute("posts",
						Matchers.hasProperty("totalElements", Matchers.equalTo(1L))));
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
						Matchers.hasProperty("totalElements", Matchers.greaterThanOrEqualTo(5L))));
	}

	@Test
	void givenSeededRows_whenFilterByPostType_thenOnlyMatchingRowsReturned() throws Exception {
		mockMvc.perform(get("/admin/discord/posts").param("postType", "MATCHDAY_PAIRINGS"))
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
