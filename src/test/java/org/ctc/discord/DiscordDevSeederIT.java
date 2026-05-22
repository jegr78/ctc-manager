package org.ctc.discord;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.ctc.TestHelper;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.ctc.domain.model.Team;
import org.ctc.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class DiscordDevSeederIT {

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
		// Force-enable the seeder for this test class; override env-var defaults so the
		// real .env.dev (developer machine) cannot bleed into test fixtures.
		registry.add("app.discord.dev-seed.enabled", () -> "true");
		registry.add("app.discord.dev-seed.guild-id", () -> "g-seed-1");
		registry.add("app.discord.dev-seed.current-match-category-id", () -> "cat-seed-1");
		registry.add("app.discord.dev-seed.bot-app-id", () -> "bot-app-1");
		registry.add("app.discord.dev-seed.race-results-forum-channel-id", () -> "forum-results-1");
		registry.add("app.discord.dev-seed.standings-forum-channel-id", () -> "forum-standings-1");
		registry.add("app.discord.dev-seed.vs-emoji-name", () -> "CTC");
		registry.add("app.discord.dev-seed.announcement-webhook-url",
				() -> "https://discord.com/api/webhooks/123/abc");
	}

	@Autowired
	DiscordGlobalConfigRepository configRepo;

	@Autowired
	TeamRepository teamRepository;

	@Autowired
	DiscordDevSeeder seeder;

	@Autowired
	TestHelper helper;

	@Autowired
	DiscordRoleCache roleCache;

	@BeforeEach
	void resetWireMock() {
		wm.resetAll();
		wm.stubFor(get(urlPathEqualTo("/api/v10/users/@me"))
				.willReturn(okJson(
						"{\"id\":\"bot-id-seed\",\"username\":\"CTC-Bot\",\"discriminator\":\"0001\"}")));
		// Reset the DiscordGlobalConfig so every test starts from a clean slate (the
		// Spring context auto-runs the seeder once on app-ready, so we need to undo
		// that side-effect for each test).
		DiscordGlobalConfig cfg = configRepo.findFirstByOrderByIdAsc();
		if (cfg == null) {
			cfg = new DiscordGlobalConfig();
		}
		cfg.setGuildId("");
		cfg.setCurrentMatchCategoryId("");
		cfg.setBotApplicationId(null);
		cfg.setRaceResultsForumChannelId("");
		cfg.setStandingsForumChannelId("");
		cfg.setAnnouncementWebhookUrl("");
		configRepo.save(cfg);
	}

	@Test
	void givenEmptyConfigAndGuildRolesReturned_whenSeed_thenConfigPopulatedAndMatchingTeamRolesAssigned() {
		// given
		Team vrx = helper.createTeam("Vortex Racing", "VRX");
		Team nfr = helper.createTeam("Nitro Fuel Racing", "NFR");
		Team unknown = helper.createTeam("Unknown Team", "ZZZ");
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/g-seed-1/roles"))
				.willReturn(okJson("["
						+ "{\"id\":\"100\",\"name\":\"VRX\",\"position\":5},"
						+ "{\"id\":\"200\",\"name\":\"NFR\",\"position\":4}"
						+ "]")));

		// when
		seeder.seed();

		// then — config populated
		DiscordGlobalConfig cfg = configRepo.findFirstByOrderByIdAsc();
		assertThat(cfg.getGuildId()).isEqualTo("g-seed-1");
		assertThat(cfg.getCurrentMatchCategoryId()).isEqualTo("cat-seed-1");
		assertThat(cfg.getBotApplicationId()).isEqualTo("bot-app-1");
		assertThat(cfg.getRaceResultsForumChannelId()).isEqualTo("forum-results-1");
		assertThat(cfg.getStandingsForumChannelId()).isEqualTo("forum-standings-1");
		assertThat(cfg.getAnnouncementWebhookUrl()).isEqualTo("https://discord.com/api/webhooks/123/abc");

		// and — matching teams got their role-id; non-matching team is left alone
		assertThat(teamRepository.findById(vrx.getId()).orElseThrow().getDiscordRoleId()).isEqualTo("100");
		assertThat(teamRepository.findById(nfr.getId()).orElseThrow().getDiscordRoleId()).isEqualTo("200");
		assertThat(teamRepository.findById(unknown.getId()).orElseThrow().getDiscordRoleId()).isNull();

		// and — role cache populated for the Team-Form dropdown
		assertThat(roleCache.snapshot()).containsKeys("100", "200");
	}

	@Test
	void givenGuildIdAlreadyPopulated_whenSeed_thenSkipsWithoutOverwriting() {
		// given — operator-manual change in a previous session: guild-id already set
		DiscordGlobalConfig existing = configRepo.findFirstByOrderByIdAsc();
		existing.setGuildId("g-manual-1");
		existing.setCurrentMatchCategoryId("cat-manual-1");
		configRepo.save(existing);
		Team vrx = helper.createTeam("Vortex Racing", "VRX");

		// when
		seeder.seed();

		// then — config unchanged; team got no role-id (the seeder didn't touch any teams)
		DiscordGlobalConfig cfg = configRepo.findFirstByOrderByIdAsc();
		assertThat(cfg.getGuildId()).isEqualTo("g-manual-1");
		assertThat(cfg.getCurrentMatchCategoryId()).isEqualTo("cat-manual-1");
		assertThat(teamRepository.findById(vrx.getId()).orElseThrow().getDiscordRoleId()).isNull();
	}

	@Test
	void givenTeamAlreadyHasDiscordRoleId_whenSeed_thenLeavesItUntouched() {
		// given — operator pre-assigned a custom role-id; seeder must not overwrite
		Team vrx = helper.createTeam("Vortex Racing", "VRX");
		vrx.setDiscordRoleId("custom-snowflake");
		teamRepository.save(vrx);
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/g-seed-1/roles"))
				.willReturn(okJson("[{\"id\":\"100\",\"name\":\"VRX\",\"position\":5}]")));

		// when
		seeder.seed();

		// then — vrx keeps its operator-assigned role-id
		assertThat(teamRepository.findById(vrx.getId()).orElseThrow().getDiscordRoleId())
				.isEqualTo("custom-snowflake");
	}

	@Test
	void givenDiscordFetchRolesFails_whenSeed_thenConfigPersistsAndNoTeamRolesAssigned() {
		// given — guild-roles endpoint returns 401, simulating live Discord auth fail
		Team vrx = helper.createTeam("Vortex Racing", "VRX");
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/g-seed-1/roles"))
				.willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
						.withStatus(401)
						.withHeader("Content-Type", "application/json")
						.withBody("{\"message\":\"Unauthorized\",\"code\":0}")));

		// when — seeder catches the DiscordApiException gracefully
		seeder.seed();

		// then — config still got persisted; team-role assignment skipped silently
		DiscordGlobalConfig cfg = configRepo.findFirstByOrderByIdAsc();
		assertThat(cfg.getGuildId()).isEqualTo("g-seed-1");
		assertThat(teamRepository.findById(vrx.getId()).orElseThrow().getDiscordRoleId()).isNull();
	}
}
