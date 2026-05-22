package org.ctc.discord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.ctc.TestHelper;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class MatchEditFormIT {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	TestHelper helper;

	private Match seedMatch(String suffix) {
		Season season = helper.createSeason("Edit Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-1-" + suffix, 0);
		Team home = helper.createTeam("Home " + suffix, "h" + suffix);
		Team away = helper.createTeam("Away " + suffix, "a" + suffix);
		return helper.createMatch(md, home, away);
	}

	@Test
	void givenValidForm_whenPostSaveEdit_thenFlashSuccessAndFieldsPersisted() throws Exception {
		// given
		Match match = seedMatch("V");

		// when
		mockMvc.perform(post("/admin/matches/" + match.getId() + "/save-edit")
						.with(csrf())
						.param("id", match.getId().toString())
						.param("discordTeaser", "**MD1**: Home vs Away — Tuesday 20:00")
						.param("streamLink", "https://twitch.tv/ctc")
						.param("lobbyHost", "PSN_Host")
						.param("raceDirector", "PSN_RD")
						.param("streamer", "PSN_Streamer"))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "Match details updated."));

		// then
		Match reloaded = matchRepository.findById(match.getId()).orElseThrow();
		assertThat(reloaded.getDiscordTeaser()).isEqualTo("**MD1**: Home vs Away — Tuesday 20:00");
		assertThat(reloaded.getStreamLink()).isEqualTo("https://twitch.tv/ctc");
		assertThat(reloaded.getLobbyHost()).isEqualTo("PSN_Host");
		assertThat(reloaded.getRaceDirector()).isEqualTo("PSN_RD");
		assertThat(reloaded.getStreamer()).isEqualTo("PSN_Streamer");
	}

	@Test
	void givenOversizedTeaser_whenPostSaveEdit_thenRendersFormWithErrorBadge() throws Exception {
		// given
		Match match = seedMatch("O");
		String oversized = "x".repeat(2001);

		// when / then — form re-renders with the error badge; nothing persisted
		mockMvc.perform(post("/admin/matches/" + match.getId() + "/save-edit")
						.with(csrf())
						.param("id", match.getId().toString())
						.param("discordTeaser", oversized))
				.andExpect(status().isOk())
				.andExpect(view().name("admin/match-form-edit"))
				.andExpect(content().string(containsString("error-badge--auth")));

		// and — field unchanged in DB
		Match reloaded = matchRepository.findById(match.getId()).orElseThrow();
		assertThat(reloaded.getDiscordTeaser()).isNull();
	}

	@Test
	void givenBlankForm_whenPostSaveEdit_thenAllFieldsClearedAndPersisted() throws Exception {
		// given — match starts populated
		Match match = seedMatch("B");
		match.setDiscordTeaser("Old");
		match.setStreamLink("https://old");
		match.setLobbyHost("OldHost");
		match.setRaceDirector("OldRD");
		match.setStreamer("OldStreamer");
		matchRepository.save(match);

		// when — blank submit
		mockMvc.perform(post("/admin/matches/" + match.getId() + "/save-edit")
						.with(csrf())
						.param("id", match.getId().toString())
						.param("discordTeaser", "")
						.param("streamLink", "")
						.param("lobbyHost", "")
						.param("raceDirector", "")
						.param("streamer", ""))
				.andExpect(status().is3xxRedirection())
				.andExpect(flash().attribute("successMessage", "Match details updated."));

		// then — every field cleared
		Match reloaded = matchRepository.findById(match.getId()).orElseThrow();
		assertThat(reloaded.getDiscordTeaser()).isEmpty();
		assertThat(reloaded.getStreamLink()).isEmpty();
		assertThat(reloaded.getLobbyHost()).isEmpty();
		assertThat(reloaded.getRaceDirector()).isEmpty();
		assertThat(reloaded.getStreamer()).isEmpty();
	}
}
