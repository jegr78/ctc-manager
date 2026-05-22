package org.ctc.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.ctc.TestHelper;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class MatchRepositoryDiscordFieldsIT {

	@Autowired
	private MatchRepository matchRepository;

	@Autowired
	private TestHelper helper;

	private Match seedMatch(String shortNameSuffix) {
		Season season = helper.createSeason("V10 IT Season " + shortNameSuffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-V10-" + shortNameSuffix, 0);
		Team home = helper.createTeam("Home " + shortNameSuffix, "H" + shortNameSuffix);
		Team away = helper.createTeam("Away " + shortNameSuffix, "A" + shortNameSuffix);
		return helper.createMatch(md, home, away);
	}

	@Test
	void givenMatchWith7DiscordFields_whenSaveAndReload_thenAllFieldsPersist() {
		// given
		Match match = seedMatch("ALL");
		match.setDiscordChannelId("100000000000000001");
		match.setDiscordChannelWebhookUrl("https://discord.com/api/webhooks/100/tok-abc");
		match.setDiscordTeaser("**MD1**: Home vs Away — Tuesday 20:00");
		match.setStreamLink("https://twitch.tv/ctc");
		match.setLobbyHost("PSN_Host");
		match.setRaceDirector("PSN_RD");
		match.setStreamer("PSN_Streamer");

		// when
		Match saved = matchRepository.save(match);
		Match reloaded = matchRepository.findById(saved.getId()).orElseThrow();

		// then
		assertThat(reloaded.getDiscordChannelId()).isEqualTo("100000000000000001");
		assertThat(reloaded.getDiscordChannelWebhookUrl())
				.isEqualTo("https://discord.com/api/webhooks/100/tok-abc");
		assertThat(reloaded.getDiscordTeaser()).isEqualTo("**MD1**: Home vs Away — Tuesday 20:00");
		assertThat(reloaded.getStreamLink()).isEqualTo("https://twitch.tv/ctc");
		assertThat(reloaded.getLobbyHost()).isEqualTo("PSN_Host");
		assertThat(reloaded.getRaceDirector()).isEqualTo("PSN_RD");
		assertThat(reloaded.getStreamer()).isEqualTo("PSN_Streamer");
	}

	@Test
	void givenMatchWithNullDiscordFields_whenSave_thenColumnsNullable() {
		// given — fresh match with no Discord/scheduling fields set
		Match match = seedMatch("NUL");
		UUID id = match.getId();

		// when
		Match reloaded = matchRepository.findById(id).orElseThrow();

		// then — all 7 columns are nullable; default state stays null
		assertThat(reloaded.getDiscordChannelId()).isNull();
		assertThat(reloaded.getDiscordChannelWebhookUrl()).isNull();
		assertThat(reloaded.getDiscordTeaser()).isNull();
		assertThat(reloaded.getStreamLink()).isNull();
		assertThat(reloaded.getLobbyHost()).isNull();
		assertThat(reloaded.getRaceDirector()).isNull();
		assertThat(reloaded.getStreamer()).isNull();
	}

	@Test
	void givenMatchWithDiscordFields_whenClearFields_thenColumnsUpdateToNull() {
		// given — match starts populated
		Match match = seedMatch("CLR");
		match.setDiscordTeaser("Old teaser");
		match.setStreamLink("https://twitch.tv/old");
		match.setLobbyHost("OldHost");
		match.setRaceDirector("OldRD");
		match.setStreamer("OldStreamer");
		matchRepository.save(match);

		// when — operator clears the fields
		Match reloaded = matchRepository.findById(match.getId()).orElseThrow();
		reloaded.setDiscordTeaser(null);
		reloaded.setStreamLink(null);
		reloaded.setLobbyHost(null);
		reloaded.setRaceDirector(null);
		reloaded.setStreamer(null);
		matchRepository.save(reloaded);
		Match afterClear = matchRepository.findById(match.getId()).orElseThrow();

		// then
		assertThat(afterClear.getDiscordTeaser()).isNull();
		assertThat(afterClear.getStreamLink()).isNull();
		assertThat(afterClear.getLobbyHost()).isNull();
		assertThat(afterClear.getRaceDirector()).isNull();
		assertThat(afterClear.getStreamer()).isNull();
	}
}
