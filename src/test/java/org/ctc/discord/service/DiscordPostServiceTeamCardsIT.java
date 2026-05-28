package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.ctc.TestHelper;
import org.ctc.admin.service.TeamCardService;
import org.ctc.discord.exception.DiscordTransientException;
import org.ctc.discord.model.DiscordPost;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.SeasonTeam;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.SeasonRepository;
import org.ctc.domain.repository.SeasonTeamRepository;
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
class DiscordPostServiceTeamCardsIT {

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
	private DiscordPostService service;

	@Autowired
	private SeasonRepository seasonRepository;

	@Autowired
	private MatchRepository matchRepository;

	@Autowired
	private SeasonTeamRepository seasonTeamRepository;

	@Autowired
	private TestHelper helper;

	@MockitoBean
	private TeamCardService teamCardService;

	@Value("${app.upload-dir:uploads}")
	private String uploadDir;

	@BeforeEach
	void resetWireMock() {
		wm.resetAll();
	}

	private Match seedMatchWithChannel(String suffix, String webhookUrl) {
		Season season = helper.createSeason("TC Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-TC-" + suffix, 0);
		Team home = helper.createTeam("Home TC " + suffix, "tc-h" + suffix);
		Team away = helper.createTeam("Away TC " + suffix, "tc-a" + suffix);
		season.addTeam(home);
		season.addTeam(away);
		seasonRepository.save(season);
		Match match = helper.createMatch(md, home, away);
		match.setDiscordChannelId("chan-tc-" + suffix);
		match.setDiscordChannelWebhookUrl(webhookUrl);
		return matchRepository.save(match);
	}

	private Path writeDummyCard(Match match, String suffix) throws IOException {
		SeasonTeam st = seasonTeamRepository
				.findBySeasonIdAndTeamId(match.getMatchday().getSeason().getId(),
						match.getHomeTeam().getId())
				.orElseThrow();
		Path file = Path.of(uploadDir).toAbsolutePath().normalize()
				.resolve("team-cards/" + st.getSeason().getId() + "/" + suffix + ".png");
		Files.createDirectories(file.getParent());
		Files.write(file, PNG_BYTES);
		return file;
	}

	@Test
	void givenCardsOnDisk_whenPostTeamCards_thenMultipartPostWithTwoAttachments() throws Exception {
		String webhookPath = "/webhooks/700/tok-tc";
		Match match = seedMatchWithChannel("D1", wm.baseUrl() + webhookPath);
		Path homeCard = writeDummyCard(match, "home-d1");
		Path awayCard = writeDummyCard(match, "away-d1");
		try {
			when(teamCardService.cardExists(org.mockito.ArgumentMatchers.any(SeasonTeam.class)))
					.thenReturn(true);
			when(teamCardService.getCardPath(org.mockito.ArgumentMatchers.any(SeasonTeam.class)))
					.thenReturn("/uploads/team-cards/" + homeCard.getParent().getFileName() + "/home-d1.png");
			wm.stubFor(post(urlPathEqualTo(webhookPath))
					.withHeader("Content-Type", containing("multipart/form-data"))
					.willReturn(okJson("{\"id\":\"msg-tc-1\",\"channel_id\":\"chan-tc-D1\"}")));

			DiscordPost saved = service.postTeamCards(match);

			assertThat(saved.getMessageId()).isEqualTo("msg-tc-1");
			assertThat(saved.getAttachmentsReplacedAt()).isNotNull();
			verify(teamCardService, org.mockito.Mockito.never())
					.generateCard(org.mockito.ArgumentMatchers.any(SeasonTeam.class));
		} finally {
			Files.deleteIfExists(homeCard);
			Files.deleteIfExists(awayCard);
		}
	}

	@Test
	void givenMissingCards_whenPostTeamCards_thenGenerateBothBeforePosting() throws Exception {
		String webhookPath = "/webhooks/701/tok-tc";
		Match match = seedMatchWithChannel("D2", wm.baseUrl() + webhookPath);
		Path stubCard = writeDummyCard(match, "stub-d2");
		try {
			when(teamCardService.cardExists(org.mockito.ArgumentMatchers.any(SeasonTeam.class)))
					.thenReturn(false);
			when(teamCardService.generateCard(org.mockito.ArgumentMatchers.any(SeasonTeam.class)))
					.thenReturn("/uploads/team-cards/" + match.getMatchday().getSeason().getId() + "/stub-d2.png");
			when(teamCardService.getCardPath(org.mockito.ArgumentMatchers.any(SeasonTeam.class)))
					.thenReturn("/uploads/team-cards/" + match.getMatchday().getSeason().getId() + "/stub-d2.png");
			wm.stubFor(post(urlPathEqualTo(webhookPath))
					.willReturn(okJson("{\"id\":\"msg-tc-2\",\"channel_id\":\"chan-tc-D2\"}")));

			DiscordPost saved = service.postTeamCards(match);

			assertThat(saved.getMessageId()).isEqualTo("msg-tc-2");
			verify(teamCardService, org.mockito.Mockito.times(2))
					.generateCard(org.mockito.ArgumentMatchers.any(SeasonTeam.class));
		} finally {
			Files.deleteIfExists(stubCard);
		}
	}

	@Test
	void givenExistingPostRow_whenPostTeamCards_thenMultipartPatchAndStampsAttachmentsReplacedAt() throws Exception {
		String webhookPath = "/webhooks/702/tok-tc";
		Match match = seedMatchWithChannel("D3", wm.baseUrl() + webhookPath);
		Path card = writeDummyCard(match, "rep-d3");
		try {
			when(teamCardService.cardExists(org.mockito.ArgumentMatchers.any(SeasonTeam.class)))
					.thenReturn(true);
			when(teamCardService.getCardPath(org.mockito.ArgumentMatchers.any(SeasonTeam.class)))
					.thenReturn("/uploads/team-cards/" + card.getParent().getFileName() + "/rep-d3.png");
			wm.stubFor(post(urlPathEqualTo(webhookPath))
					.willReturn(okJson("{\"id\":\"msg-tc-3\",\"channel_id\":\"chan-tc-D3\"}")));

			DiscordPost first = service.postTeamCards(match);

			wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-tc-3"))
					.withHeader("Content-Type", containing("multipart/form-data"))
					.willReturn(okJson("{\"id\":\"msg-tc-3\",\"channel_id\":\"chan-tc-D3\"}")));

			DiscordPost second = service.postTeamCards(match);

			assertThat(second.getId()).isEqualTo(first.getId());
			assertThat(second.getAttachmentsReplacedAt()).isNotNull();
		} finally {
			Files.deleteIfExists(card);
		}
	}

	@Test
	void givenWireMock5xx_whenPostTeamCards_thenPropagatesDiscordTransientException() throws Exception {
		String webhookPath = "/webhooks/703/tok-tc";
		Match match = seedMatchWithChannel("D4", wm.baseUrl() + webhookPath);
		Path card = writeDummyCard(match, "err-d4");
		try {
			when(teamCardService.cardExists(org.mockito.ArgumentMatchers.any(SeasonTeam.class)))
					.thenReturn(true);
			when(teamCardService.getCardPath(org.mockito.ArgumentMatchers.any(SeasonTeam.class)))
					.thenReturn("/uploads/team-cards/" + card.getParent().getFileName() + "/err-d4.png");
			wm.stubFor(post(urlPathEqualTo(webhookPath))
					.willReturn(aResponse().withStatus(500).withBody("server error")));

			assertThatThrownBy(() -> service.postTeamCards(match))
					.isInstanceOf(DiscordTransientException.class);
		} finally {
			Files.deleteIfExists(card);
		}
	}

	@Test
	void givenAttackerControlledCardPath_whenPostTeamCards_thenSecurityException() throws Exception {
		String webhookPath = "/webhooks/704/tok-tc";
		Match match = seedMatchWithChannel("D5", wm.baseUrl() + webhookPath);
		when(teamCardService.cardExists(org.mockito.ArgumentMatchers.any(SeasonTeam.class)))
				.thenReturn(true);
		when(teamCardService.getCardPath(org.mockito.ArgumentMatchers.any(SeasonTeam.class)))
				.thenReturn("/uploads/../../../etc/passwd");

		assertThatThrownBy(() -> service.postTeamCards(match))
				.isInstanceOf(SecurityException.class);
	}
}
