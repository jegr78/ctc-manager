package org.ctc.discord.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.ctc.backup.schema.BackupSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
@Tag("integration")
class DiscordGlobalConfigGuardTest {

	@Autowired
	private BackupSchema backupSchema;

	@Test
	void givenPhase93Migration_whenInspectExportOrder_thenCountStaysAt24() {
		assertThat(backupSchema.getExportOrder())
				.as("Phase 93 must not bump BackupSchema.EXPORT_ORDER; DiscordGlobalConfig is "
						+ "structurally excluded by the org.ctc.domain.model.* package filter.")
				.hasSize(24);
	}

	@Test
	void givenPhase93Migration_whenInspectExportOrder_thenDoesNotContainDiscordGlobalConfig() {
		assertThat(backupSchema.getExportOrder())
				.as("DiscordGlobalConfig must not appear in the backup export order.")
				.noneMatch(ref -> "discord_global_config".equalsIgnoreCase(ref.tableName()));
	}
}
