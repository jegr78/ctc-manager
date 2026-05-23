package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aMultipart;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.TestHelper;
import org.ctc.admin.service.MatchResultsGraphicService;
import org.ctc.admin.service.ResultsGraphicService;
import org.ctc.discord.model.DiscordPost;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Driver;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceResult;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
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
class DiscordPostServiceMatchResultsIT {

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
	RaceResultRepository raceResultRepository;

	@MockitoBean
	MatchResultsGraphicService matchResultsGraphicService;

	@MockitoBean
	ResultsGraphicService resultsGraphicService;

	@org.springframework.beans.factory.annotation.Value("${app.upload-dir:uploads}")
	String uploadDir;

	@BeforeEach
	void resetWireMock() throws Exception {
		wm.resetAll();
		when(matchResultsGraphicService.generateMatchResults(any(Match.class))).thenReturn(PNG_BYTES);
		java.nio.file.Path dummy = java.nio.file.Path.of(uploadDir, "races/mr-dummy.png").toAbsolutePath().normalize();
		java.nio.file.Files.createDirectories(dummy.getParent());
		java.nio.file.Files.write(dummy, PNG_BYTES);
		when(resultsGraphicService.generateResults(any(Race.class))).thenReturn("/uploads/races/mr-dummy.png");
	}

	private Match seedMatchWith2RacesAndResults(String suffix, String webhookUrl, boolean allHaveResults) {
		Season season = helper.createSeason("MR Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-MR-" + suffix, 0);
		Team home = helper.createTeam("MR Home " + suffix, "mr-h" + suffix);
		Team away = helper.createTeam("MR Away " + suffix, "mr-a" + suffix);
		season.addTeam(home);
		season.addTeam(away);
		Match match = helper.createMatch(md, home, away);
		Driver driver = helper.createDriver("PSN-MR-" + suffix, "Driver MR-" + suffix);
		for (int i = 0; i < 2; i++) {
			Race race = helper.createRace(md, match);
			if (allHaveResults || i == 0) {
				RaceResult result = new RaceResult(race, driver, 1, 1, false);
				raceResultRepository.save(result);
				race.getResults().add(result);
			}
			match.getRaces().add(race);
		}
		match.setDiscordChannelId("chan-mr-" + suffix);
		match.setDiscordChannelWebhookUrl(webhookUrl);
		return matchRepository.save(match);
	}

	@Test
	void givenAllRacesHaveResults_whenPostMatchResults_thenSinglePngMultipartPost() throws Exception {
		String webhookPath = "/webhooks/900/tok-mr1";
		Match match = seedMatchWith2RacesAndResults("R1", wm.baseUrl() + webhookPath, true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withHeader("Content-Type", containing("multipart/form-data"))
				.withMultipartRequestBody(aMultipart("files[0]")
						.withHeader("Content-Type", equalTo("image/png")))
				.willReturn(okJson("{\"id\":\"msg-mr1\",\"channel_id\":\"chan-mr-R1\"}")));

		DiscordPost saved = service.postMatchResults(match);

		assertThat(saved.getMessageId()).isEqualTo("msg-mr1");
		assertThat(saved.getAttachmentsReplacedAt()).isNotNull();
		verify(matchResultsGraphicService).generateMatchResults(any(Match.class));
	}

	@Test
	void givenExistingPostRow_whenPostMatchResults_thenMultipartPatch() throws Exception {
		String webhookPath = "/webhooks/901/tok-mr2";
		Match match = seedMatchWith2RacesAndResults("R2", wm.baseUrl() + webhookPath, true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-mr2\",\"channel_id\":\"chan-mr-R2\"}")));
		DiscordPost first = service.postMatchResults(match);

		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-mr2"))
				.willReturn(okJson("{\"id\":\"msg-mr2\",\"channel_id\":\"chan-mr-R2\"}")));
		DiscordPost second = service.postMatchResults(match);

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(second.getAttachmentsReplacedAt()).isNotNull();
	}

	@Test
	void givenOneRaceMissingResults_whenPostMatchResults_thenBusinessRuleException() {
		Match match = seedMatchWith2RacesAndResults("R3", wm.baseUrl() + "/webhooks/902/tok-mr3", false);

		assertThatThrownBy(() -> service.postMatchResults(match))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("Match results require at least one race result.");
	}

	@Test
	void givenAllRacesHaveResults_whenPostMatchResults_thenFilenameIsMatchResultsPng() throws Exception {
		String webhookPath = "/webhooks/903/tok-mr4";
		Match match = seedMatchWith2RacesAndResults("R4", wm.baseUrl() + webhookPath, true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withMultipartRequestBody(aMultipart("files[0]"))
				.willReturn(okJson("{\"id\":\"msg-mr4\",\"channel_id\":\"chan-mr-R4\"}")));

		DiscordPost saved = service.postMatchResults(match);

		assertThat(saved.getMessageId()).isEqualTo("msg-mr4");
	}

	@Test
	void givenMatchWith2Races_whenPostMatchResults_thenBundleContainsOverviewPlusOnePerRace() throws Exception {
		String webhookPath = "/webhooks/904/tok-mr5";
		Match match = seedMatchWith2RacesAndResults("R5", wm.baseUrl() + webhookPath, true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withMultipartRequestBody(aMultipart("files[0]")
						.withHeader("Content-Type", equalTo("image/png")))
				.withMultipartRequestBody(aMultipart("files[1]")
						.withHeader("Content-Type", equalTo("image/png")))
				.withMultipartRequestBody(aMultipart("files[2]")
						.withHeader("Content-Type", equalTo("image/png")))
				.willReturn(okJson("{\"id\":\"msg-mr5\",\"channel_id\":\"chan-mr-R5\"}")));

		DiscordPost saved = service.postMatchResults(match);

		assertThat(saved.getMessageId())
				.as("MATCH_RESULTS must bundle 1 overview + 1 per-race PNG (3 attachments for 2 races)")
				.isEqualTo("msg-mr5");
		verify(resultsGraphicService, org.mockito.Mockito.times(2)).generateResults(any(Race.class));
	}
}
