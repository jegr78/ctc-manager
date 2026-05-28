package org.ctc.discord.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.Map;
import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.junit.jupiter.api.AfterEach;
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
class DiscordPostControllerIT {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private DiscordPostRepository discordPostRepository;

	@Autowired
	private MatchRepository matchRepository;

	@Autowired
	private TestHelper helper;

	@AfterEach
	void cleanup() {
		discordPostRepository.deleteAll();
	}

	@Test
	void givenByeMatchInDataSet_whenListDiscordPosts_thenRendersWithByeLabel() throws Exception {
		// given
		Season season = helper.createSeason("Bye Render Season");
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-Bye-1", 0);
		Team homeTeam = helper.createTeam("Home Team", "BR-H");
		Match byeMatch = helper.createMatch(md, homeTeam, null);
		byeMatch.setBye(true);
		matchRepository.save(byeMatch);

		// when
		var result = mockMvc.perform(get("/admin/discord/posts"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/discord-posts"))
				.andExpect(model().attributeExists("matchLabels"))
				.andReturn();

		@SuppressWarnings("unchecked")
		Map<UUID, String> matchLabels = (Map<UUID, String>) result.getModelAndView().getModel().get("matchLabels");
		assertThat(matchLabels).containsKey(byeMatch.getId());
		assertThat(matchLabels.get(byeMatch.getId())).contains("BR-H vs. Bye");
	}

	@Test
	void givenRegularMatch_whenListDiscordPosts_thenRendersWithBothShortNames() throws Exception {
		// given
		Season season = helper.createSeason("Regular Render Season");
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-Reg-1", 0);
		Team homeTeam = helper.createTeam("Home Team", "RR-H");
		Team awayTeam = helper.createTeam("Away Team", "RR-A");
		Match regularMatch = helper.createMatch(md, homeTeam, awayTeam);

		// when
		var result = mockMvc.perform(get("/admin/discord/posts"))
				// then
				.andExpect(status().isOk())
				.andExpect(view().name("admin/discord-posts"))
				.andExpect(model().attributeExists("matchLabels"))
				.andReturn();

		@SuppressWarnings("unchecked")
		Map<UUID, String> matchLabels = (Map<UUID, String>) result.getModelAndView().getModel().get("matchLabels");
		assertThat(matchLabels).containsKey(regularMatch.getId());
		assertThat(matchLabels.get(regularMatch.getId())).contains("RR-H vs. RR-A");
	}
}
