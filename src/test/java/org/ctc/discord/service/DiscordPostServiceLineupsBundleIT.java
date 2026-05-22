package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import org.ctc.TestHelper;
import org.ctc.admin.service.LineupGraphicService;
import org.ctc.discord.model.DiscordPost;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceLineup;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceLineupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class DiscordPostServiceLineupsBundleIT {

	private static final byte[] PNG_BYTES = new byte[] {
			(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
	};

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
	DiscordPostService service;

	@Autowired
	TestHelper helper;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	RaceLineupRepository raceLineupRepository;

	@Value("${app.upload-dir:uploads}")
	String uploadDir;

	@MockitoBean
	LineupGraphicService lineupGraphicService;

	@BeforeEach
	void resetState() throws Exception {
		wm.resetAll();
		Path dummy = Path.of(uploadDir, "team-cards/lineups-dummy.png").toAbsolutePath().normalize();
		Files.createDirectories(dummy.getParent());
		if (!Files.exists(dummy)) {
			Files.write(dummy, PNG_BYTES);
		}
		when(lineupGraphicService.generateLineup(any(Race.class)))
				.thenReturn("/uploads/team-cards/lineups-dummy.png");
	}

	private Match seedMatchWith2Races(String suffix, String webhookUrl, boolean allHaveLineups) {
		Season season = helper.createSeason("LB Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-LB-" + suffix, 0);
		Team home = helper.createTeam("LB Home " + suffix, "lb-h" + suffix);
		Team away = helper.createTeam("LB Away " + suffix, "lb-a" + suffix);
		season.addTeam(home);
		season.addTeam(away);
		Match match = helper.createMatch(md, home, away);
		Driver driver = helper.createDriver("PSN-" + suffix, "Driver " + suffix);
		for (int i = 0; i < 2; i++) {
			Race race = helper.createRace(md, match);
			if (allHaveLineups || i == 0) {
				raceLineupRepository.save(new RaceLineup(race, driver, home));
			}
			match.getRaces().add(race);
		}
		match.setDiscordChannelId("chan-lb-" + suffix);
		match.setDiscordChannelWebhookUrl(webhookUrl);
		return matchRepository.save(match);
	}

	@Test
	void givenAllRacesHaveLineups_whenPostLineups_thenMultipartPostWithTwoIndexedAttachments() throws Exception {
		String webhookPath = "/webhooks/810/tok-lb1";
		Match match = seedMatchWith2Races("L1", wm.baseUrl() + webhookPath, true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withHeader("Content-Type", containing("multipart/form-data"))
				.withMultipartRequestBody(aMultipart("files[0]")
						.withHeader("Content-Type", equalTo("image/png")))
				.withMultipartRequestBody(aMultipart("files[1]")
						.withHeader("Content-Type", equalTo("image/png")))
				.willReturn(okJson("{\"id\":\"msg-lb1\",\"channel_id\":\"chan-lb-L1\"}")));

		DiscordPost saved = service.postLineups(match);

		assertThat(saved.getMessageId()).isEqualTo("msg-lb1");
		assertThat(saved.getAttachmentsReplacedAt()).isNotNull();
	}

	@Test
	void givenOneRaceMissingLineup_whenPostLineups_thenBusinessRuleException() {
		Match match = seedMatchWith2Races("L2", wm.baseUrl() + "/webhooks/811/tok-lb2", false);

		assertThatThrownBy(() -> service.postLineups(match))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("Configure lineups for all races first");
	}
}
