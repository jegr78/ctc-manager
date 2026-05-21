package org.ctc.discord.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.ctc.discord.model.DiscordGlobalConfig;
import org.junit.jupiter.api.BeforeEach;
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
class DiscordGlobalConfigRepositoryIT {

	@Autowired
	private DiscordGlobalConfigRepository repo;

	@BeforeEach
	void resetSeedRowToEmptyDefaults() {
		DiscordGlobalConfig seed = repo.findFirstByOrderByIdAsc();
		if (seed == null) {
			repo.save(new DiscordGlobalConfig());
			return;
		}
		seed.setGuildId("");
		seed.setAnnouncementWebhookUrl("");
		seed.setRaceResultsForumChannelId("");
		seed.setStandingsForumChannelId("");
		seed.setVsEmojiName("CTC");
		seed.setBotApplicationId(null);
		repo.save(seed);
	}

	@Test
	void givenV8MigrationApplied_whenFindFirstByOrderByIdAsc_thenReturnsSeedRow() {
		// when
		DiscordGlobalConfig seed = repo.findFirstByOrderByIdAsc();

		// then
		assertThat(seed).isNotNull();
		assertThat(seed.getId()).isNotNull();
		assertThat(seed.getGuildId()).isEmpty();
		assertThat(seed.getAnnouncementWebhookUrl()).isEmpty();
		assertThat(seed.getVsEmojiName()).isEqualTo("CTC");
		assertThat(seed.getBotApplicationId()).isNull();
	}

	@Test
	void givenSeedRow_whenSaveModified_thenSameIdPreservedAndFieldsUpdated() {
		// given
		DiscordGlobalConfig seed = repo.findFirstByOrderByIdAsc();
		Long originalId = seed.getId();
		seed.setGuildId("123456789012345678");

		// when
		DiscordGlobalConfig saved = repo.save(seed);
		DiscordGlobalConfig reloaded = repo.findFirstByOrderByIdAsc();

		// then
		assertThat(saved.getId()).isEqualTo(originalId);
		assertThat(reloaded.getId()).isEqualTo(originalId);
		assertThat(reloaded.getGuildId()).isEqualTo("123456789012345678");
		assertThat(repo.count()).isEqualTo(1L);
	}

	@Test
	void givenFreshMigration_whenCount_thenExactlyOneRow() {
		// when / then — singleton invariant on freshly migrated DB
		assertThat(repo.count()).isEqualTo(1L);
	}
}
