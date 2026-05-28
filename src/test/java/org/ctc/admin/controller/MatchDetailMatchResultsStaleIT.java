package org.ctc.admin.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.model.DiscordPostType;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceResultRepository;
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
class MatchDetailMatchResultsStaleIT {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestHelper helper;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	RaceResultRepository raceResultRepository;

	@Autowired
	DiscordPostRepository discordPostRepository;

	@BeforeEach
	void resetState() {
		discordPostRepository.deleteAll();
	}

	@AfterEach
	void cleanup() {
		discordPostRepository.deleteAll();
	}

	private Match seedMatchWithResult(String suffix) {
		Season season = helper.createSeason("Stale Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-St-" + suffix, 0);
		Team home = helper.createTeam("St Home " + suffix, "st-h" + suffix);
		Team away = helper.createTeam("St Away " + suffix, "st-a" + suffix);
		Match match = helper.createMatch(md, home, away);
		Race race = helper.createRace(md, match);
		Driver driver = helper.createDriver("PSN-St-" + suffix, "Driver St " + suffix);
		RaceResult result = new RaceResult(race, driver, 1, 1, false);
		raceResultRepository.save(result);
		race.getResults().add(result);
		match.getRaces().add(race);
		match.setDiscordChannelId("chan-st-" + suffix);
		match.setDiscordChannelWebhookUrl("https://discord.com/api/webhooks/100/tok-st-" + suffix);
		return matchRepository.save(match);
	}

	private DiscordPost seedMatchResultsPost(Match match, LocalDateTime postedAt) {
		DiscordPost p = new DiscordPost();
		p.setChannelId(match.getDiscordChannelId());
		p.setMessageId("msg-st-" + match.getId().toString().substring(0, 8));
		p.setWebhookId("100");
		p.setWebhookToken("tok");
		p.setPostType(DiscordPostType.MATCH_RESULTS);
		p.setMatchId(match.getId());
		p.setPostedAt(postedAt);
		return discordPostRepository.save(p);
	}

	@Test
	void givenNoMatchResultsRow_whenDetail_thenPostLabelVisibleAndNotStale() throws Exception {
		Match match = seedMatchWithResult("S1");

		mockMvc.perform(get("/admin/matches/" + match.getId()))
				.andExpect(status().isOk())
				.andExpect(model().attribute("matchResultsPost", Matchers.nullValue()))
				.andExpect(model().attribute("matchResultsStale", false))
				.andExpect(content().string(Matchers.containsString("Post Match Results")));
	}

	@Test
	void givenFreshMatchResultsRow_whenDetail_thenRePostLabelAndNotStale() throws Exception {
		Match match = seedMatchWithResult("S2");
		Thread.sleep(10);
		seedMatchResultsPost(match, LocalDateTime.now());

		mockMvc.perform(get("/admin/matches/" + match.getId()))
				.andExpect(status().isOk())
				.andExpect(model().attribute("matchResultsStale", false))
				.andExpect(content().string(Matchers.containsString("Re-Post Match Results")));
	}

	@Test
	void givenStaleMatchResultsRow_whenDetail_thenUpdateLabelAndStale() throws Exception {
		Match match = seedMatchWithResult("S3");
		seedMatchResultsPost(match, LocalDateTime.now());
		Thread.sleep(10);
		RaceResult result = match.getRaces().get(0).getResults().get(0);
		result.setPosition(2);
		raceResultRepository.save(result);

		mockMvc.perform(get("/admin/matches/" + match.getId()))
				.andExpect(status().isOk())
				.andExpect(model().attribute("matchResultsStale", true))
				.andExpect(content().string(Matchers.containsString("Update Match Results")));
	}
}
