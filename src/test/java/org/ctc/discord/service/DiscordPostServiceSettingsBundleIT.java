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
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.nio.file.Files;
import java.nio.file.Path;
import org.ctc.TestHelper;
import org.ctc.admin.service.SettingsGraphicService;
import org.ctc.discord.model.DiscordPost;
import org.ctc.discord.repository.DiscordPostRepository;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.MatchRepository;
import org.ctc.domain.repository.RaceRepository;
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
class DiscordPostServiceSettingsBundleIT {

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
	DiscordPostRepository discordPostRepository;

	@Autowired
	TestHelper helper;

	@Autowired
	MatchRepository matchRepository;

	@Autowired
	RaceRepository raceRepository;

	@Value("${app.upload-dir:uploads}")
	String uploadDir;

	@MockitoBean
	SettingsGraphicService settingsGraphicService;

	@BeforeEach
	void resetState() throws Exception {
		wm.resetAll();
		Path dummy = Path.of(uploadDir, "team-cards/settings-dummy.png").toAbsolutePath().normalize();
		Files.createDirectories(dummy.getParent());
		if (!Files.exists(dummy)) {
			Files.write(dummy, PNG_BYTES);
		}
		when(settingsGraphicService.generateSettings(any(Race.class)))
				.thenReturn("/uploads/team-cards/settings-dummy.png");
	}

	private Match seedMatchWith3Races(String suffix, String webhookUrl, boolean allHaveSettings) {
		Season season = helper.createSeason("SB Season " + suffix);
		Matchday md = helper.createMatchdayInRegularPhase(season, "MD-SB-" + suffix, 0);
		Team home = helper.createTeam("SB Home " + suffix, "sb-h" + suffix);
		Team away = helper.createTeam("SB Away " + suffix, "sb-a" + suffix);
		season.addTeam(home);
		season.addTeam(away);
		Match match = helper.createMatch(md, home, away);
		for (int i = 0; i < 3; i++) {
			Race race = helper.createRace(md, match);
			if (allHaveSettings || i < 2) {
				race.setSettings(helper.completeRaceSettings(race));
				raceRepository.save(race);
			}
			match.getRaces().add(race);
		}
		match.setDiscordChannelId("chan-sb-" + suffix);
		match.setDiscordChannelWebhookUrl(webhookUrl);
		return matchRepository.save(match);
	}

	@Test
	void givenAllRacesComplete_whenPostSettings_thenMultipartPostWithThreeIndexedAttachments() throws Exception {
		String webhookPath = "/webhooks/800/tok-sb1";
		Match match = seedMatchWith3Races("S1", wm.baseUrl() + webhookPath, true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.withHeader("Content-Type", containing("multipart/form-data"))
				.withMultipartRequestBody(aMultipart("files[0]")
						.withHeader("Content-Type", equalTo("image/png")))
				.withMultipartRequestBody(aMultipart("files[1]")
						.withHeader("Content-Type", equalTo("image/png")))
				.withMultipartRequestBody(aMultipart("files[2]")
						.withHeader("Content-Type", equalTo("image/png")))
				.willReturn(okJson("{\"id\":\"msg-sb1\",\"channel_id\":\"chan-sb-S1\"}")));

		DiscordPost saved = service.postSettings(match);

		assertThat(saved.getMessageId()).isEqualTo("msg-sb1");
		assertThat(saved.getAttachmentsReplacedAt()).isNotNull();
	}

	@Test
	void givenExistingPostRow_whenPostSettings_thenMultipartPatchAndStampsAttachmentsReplacedAt() throws Exception {
		String webhookPath = "/webhooks/801/tok-sb2";
		Match match = seedMatchWith3Races("S2", wm.baseUrl() + webhookPath, true);
		wm.stubFor(post(urlPathEqualTo(webhookPath))
				.willReturn(okJson("{\"id\":\"msg-sb2\",\"channel_id\":\"chan-sb-S2\"}")));
		DiscordPost first = service.postSettings(match);

		wm.stubFor(patch(urlPathEqualTo(webhookPath + "/messages/msg-sb2"))
				.willReturn(okJson("{\"id\":\"msg-sb2\",\"channel_id\":\"chan-sb-S2\"}")));
		DiscordPost second = service.postSettings(match);

		assertThat(second.getId()).isEqualTo(first.getId());
		assertThat(second.getAttachmentsReplacedAt()).isNotNull();
	}

	@Test
	void givenOneRaceMissingSettings_whenPostSettings_thenBusinessRuleException() {
		Match match = seedMatchWith3Races("S3", wm.baseUrl() + "/webhooks/802/tok-sb3", false);

		assertThatThrownBy(() -> service.postSettings(match))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("Configure settings for all races first");
	}
}
