package org.ctc.discord.service;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.util.List;
import org.ctc.discord.dto.ArchiveCategory;
import org.ctc.discord.model.DiscordGlobalConfig;
import org.ctc.discord.repository.DiscordGlobalConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
@Transactional
class DiscordCategoryResolverWireMockIT {

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
	DiscordCategoryResolver resolver;

	@Autowired
	DiscordGlobalConfigRepository configRepo;

	@BeforeEach
	void setUp() {
		wm.resetAll();
		DiscordGlobalConfig cfg = configRepo.findFirstByOrderByIdAsc();
		if (cfg == null) {
			cfg = new DiscordGlobalConfig();
		}
		cfg.setGuildId("g1");
		configRepo.save(cfg);
	}

	@Test
	void givenMixedChannelTypes_whenResolveArchiveCategoriesFor_thenOnlyType4MatchingRegexReturned() throws Exception {
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.willReturn(okJson("["
						+ "{\"id\":\"cat-match\",\"name\":\"Match Days Archive 2026\",\"type\":4,\"parent_id\":null},"
						+ "{\"id\":\"cat-other\",\"name\":\"General Archive\",\"type\":4,\"parent_id\":null},"
						+ "{\"id\":\"c1\",\"name\":\"md1\",\"type\":0,\"parent_id\":\"cat-match\"},"
						+ "{\"id\":\"c2\",\"name\":\"md2\",\"type\":0,\"parent_id\":\"cat-match\"},"
						+ "{\"id\":\"c3\",\"name\":\"general\",\"type\":0,\"parent_id\":null}"
						+ "]")));

		List<ArchiveCategory> result = resolver.resolveArchiveCategoriesFor(2026);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).id()).isEqualTo("cat-match");
		assertThat(result.get(0).currentChannelCount()).isEqualTo(2);
	}

	@Test
	void givenChannelsUnderCategory_whenResolveArchiveCategoriesFor_thenCountDerivedFromParentId() throws Exception {
		StringBuilder json = new StringBuilder("[");
		json.append("{\"id\":\"cat-1\",\"name\":\"Match Days Archive 2026\",\"type\":4,\"parent_id\":null}");
		for (int i = 0; i < 47; i++) {
			json.append(",{\"id\":\"text-").append(i).append("\",\"name\":\"chan-").append(i)
					.append("\",\"type\":0,\"parent_id\":\"cat-1\"}");
		}
		json.append("]");
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.willReturn(okJson(json.toString())));

		List<ArchiveCategory> result = resolver.resolveArchiveCategoriesFor(2026);

		assertThat(result).hasSize(1);
		assertThat(result.get(0).currentChannelCount()).isEqualTo(47);
	}

	@Test
	void givenYear2025AndYear2026Categories_whenResolveArchiveCategoriesFor2026_thenOnly2026Returned() throws Exception {
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.willReturn(okJson("["
						+ "{\"id\":\"cat-2025\",\"name\":\"Match Days Archive 2025\",\"type\":4,\"parent_id\":null},"
						+ "{\"id\":\"cat-2026-a\",\"name\":\"Match Days Archive 2026\",\"type\":4,\"parent_id\":null},"
						+ "{\"id\":\"cat-2026-b\",\"name\":\"Match Days Archive 2026 (2)\",\"type\":4,\"parent_id\":null}"
						+ "]")));

		List<ArchiveCategory> result = resolver.resolveArchiveCategoriesFor(2026);

		assertThat(result).extracting(ArchiveCategory::id).containsExactly("cat-2026-a", "cat-2026-b");
	}

	@Test
	void givenMultipleNumSuffixCategories_whenResolveArchiveCategoriesFor_thenSortedAscendingByNum() throws Exception {
		wm.stubFor(get(urlPathEqualTo("/api/v10/guilds/g1/channels"))
				.willReturn(okJson("["
						+ "{\"id\":\"cat-c\",\"name\":\"Match Days Archive 2026 (3)\",\"type\":4,\"parent_id\":null},"
						+ "{\"id\":\"cat-a\",\"name\":\"Match Days Archive 2026\",\"type\":4,\"parent_id\":null},"
						+ "{\"id\":\"cat-b\",\"name\":\"Match Days Archive 2026 (2)\",\"type\":4,\"parent_id\":null}"
						+ "]")));

		List<ArchiveCategory> result = resolver.resolveArchiveCategoriesFor(2026);

		assertThat(result).extracting(ArchiveCategory::num).containsExactly(1, 2, 3);
	}
}
